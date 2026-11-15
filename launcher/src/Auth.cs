// Cy Launcher - Microsoft account sign-in for Minecraft.
// Flow: Microsoft (device code) -> Xbox Live -> XSTS -> Minecraft services -> profile.
// Requires an Azure app (client ID) that Mojang has approved for the Minecraft API.
using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace CyLauncher
{
    class DeviceCode
    {
        public string UserCode;
        public string VerificationUri;
        public string Code;
        public int IntervalSeconds;
        public DateTime ExpiresAt;
    }

    class AuthException : Exception
    {
        public AuthException(string message) : base(message) { }
    }

    static class Auth
    {
        const string Scope = "XboxLive.signin offline_access";
        const string MsBase = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";

        // The official Minecraft Launcher's public client ID. It is already approved for Minecraft login,
        // so the device-code flow works without the user registering (and waiting for Mojang to approve) their own Azure app.
        public const string DefaultClientId = "00000000402b5328";

        /// <summary>Step 1: ask Microsoft for a short code the player types at microsoft.com/link.</summary>
        public static async Task<DeviceCode> StartDeviceCode(string clientId)
        {
            var form = new Dictionary<string, string>();
            form["client_id"] = clientId;
            form["scope"] = Scope;
            var r = await Net.PostForm(MsBase + "devicecode", form);
            var o = J.ParseObj(r.Body);
            if (!r.Ok || o == null || J.Str(o, "device_code") == null)
                throw new AuthException(MicrosoftError(o, "Microsoft couldn't start sign-in. Check your internet connection and try again."));
            var dc = new DeviceCode();
            dc.Code = J.Str(o, "device_code");
            dc.UserCode = J.Str(o, "user_code");
            dc.VerificationUri = J.Str(o, "verification_uri") ?? "https://www.microsoft.com/link";
            dc.IntervalSeconds = Math.Max(1, (int)J.Long(o, "interval"));
            dc.ExpiresAt = DateTime.UtcNow.AddSeconds(Math.Max(60, J.Long(o, "expires_in")));
            return dc;
        }

        /// <summary>Step 2: wait until the player has entered the code, then finish signing in.</summary>
        public static async Task<Account> WaitForDeviceCode(string clientId, DeviceCode dc, CancellationToken cancel, IProgress<string> status)
        {
            int interval = dc.IntervalSeconds;
            while (true)
            {
                if (DateTime.UtcNow > dc.ExpiresAt) throw new AuthException("The sign-in code expired. Please try again.");
                await Task.Delay(TimeSpan.FromSeconds(interval), cancel);

                var form = new Dictionary<string, string>();
                form["grant_type"] = "urn:ietf:params:oauth:grant-type:device_code";
                form["client_id"] = clientId;
                form["device_code"] = dc.Code;
                var r = await Net.PostForm(MsBase + "token", form);
                var o = J.ParseObj(r.Body);
                string error = J.Str(o, "error");
                if (error == "authorization_pending") continue;
                if (error == "slow_down") { interval += 5; continue; }
                if (error == "authorization_declined") throw new AuthException("Sign-in was cancelled in the browser.");
                if (error == "expired_token") throw new AuthException("The sign-in code expired. Please try again.");
                if (!r.Ok || error != null) throw new AuthException(MicrosoftError(o, "Microsoft sign-in failed."));

                status.Report("Signing in to Xbox Live...");
                return await FinishSignIn(J.Str(o, "access_token"), J.Str(o, "refresh_token"), status);
            }
        }

        /// <summary>Gets a fresh Minecraft token using the saved Microsoft refresh token (no browser needed).</summary>
        public static async Task<Account> Refresh(string clientId, Account account, IProgress<string> status)
        {
            if (string.IsNullOrEmpty(account.RefreshToken)) throw new AuthException("Please sign in again.");
            status.Report("Refreshing sign-in...");
            var form = new Dictionary<string, string>();
            form["grant_type"] = "refresh_token";
            form["client_id"] = clientId;
            form["refresh_token"] = account.RefreshToken;
            form["scope"] = Scope;
            var r = await Net.PostForm(MsBase + "token", form);
            var o = J.ParseObj(r.Body);
            if (!r.Ok || J.Str(o, "access_token") == null) throw new AuthException("Your sign-in expired. Please sign in again.");
            return await FinishSignIn(J.Str(o, "access_token"), J.Str(o, "refresh_token") ?? account.RefreshToken, status);
        }

        /// <summary>Use the account you're already signed into in the official Minecraft Launcher (no sign-in needed).</summary>
        public static async Task<Account> FromMinecraftLauncher(IProgress<string> status)
        {
            var info = LauncherAccountReader.ReadActive();
            if (info == null)
            {
                if (!LauncherAccountReader.LauncherPresent)
                    throw new AuthException("The official Minecraft Launcher isn't set up on this PC yet. Install it and sign in once, then press Launch.");
                throw new AuthException("No Java Edition account was found in the Minecraft Launcher. Open it, sign in, then press Launch.");
            }

            status.Report("Checking your Minecraft account...");
            var prof = await Net.GetAuthed("https://api.minecraftservices.com/minecraft/profile", info.AccessToken);
            if (prof.Status == 401 || prof.Status == 403)
                throw new AuthException("Your Minecraft Launcher sign-in has expired. Open the official Minecraft Launcher once (it refreshes itself), then press Launch again.");
            if (prof.Status == 404)
                throw new AuthException("That account doesn't own Minecraft: Java Edition.");
            if (!prof.Ok)
                throw new AuthException("Couldn't verify your Minecraft account (HTTP " + prof.Status + ").");
            var p = J.ParseObj(prof.Body);

            var a = new Account();
            a.Source = "launcher";
            a.Name = J.Str(p, "name") ?? info.Name;
            a.Uuid = J.Str(p, "id") ?? info.Uuid;
            a.Xuid = "0";
            a.McToken = info.AccessToken;
            a.McTokenExpiresUtcTicks = DateTime.UtcNow.AddHours(1).Ticks; // re-read from the launcher each time anyway
            a.RefreshToken = null;
            var skins = J.Arr(p, "skins");
            if (skins != null)
                foreach (var s in skins)
                    if (J.Str(s, "state") == "ACTIVE") { a.SkinUrl = J.Str(s, "url"); break; }
            a.Save();
            Log.Write("Using Minecraft Launcher account " + a.Name);
            return a;
        }

        static async Task<Account> FinishSignIn(string msToken, string refreshToken, IProgress<string> status)
        {
            // Xbox Live
            var xblBody = new Dictionary<string, object>();
            var xblProps = new Dictionary<string, object>();
            xblProps["AuthMethod"] = "RPS";
            xblProps["SiteName"] = "user.auth.xboxlive.com";
            xblProps["RpsTicket"] = "d=" + msToken;
            xblBody["Properties"] = xblProps;
            xblBody["RelyingParty"] = "http://auth.xboxlive.com";
            xblBody["TokenType"] = "JWT";
            var xbl = await Net.PostJson("https://user.auth.xboxlive.com/user/authenticate", xblBody, null);
            if (!xbl.Ok) throw new AuthException("Xbox Live sign-in failed (HTTP " + xbl.Status + ").");
            var xblObj = J.ParseObj(xbl.Body);
            string xblToken = J.Str(xblObj, "Token");

            // XSTS
            status.Report("Checking Xbox profile...");
            var xstsBody = new Dictionary<string, object>();
            var xstsProps = new Dictionary<string, object>();
            xstsProps["SandboxId"] = "RETAIL";
            xstsProps["UserTokens"] = new object[] { xblToken };
            xstsBody["Properties"] = xstsProps;
            xstsBody["RelyingParty"] = "rp://api.minecraftservices.com/";
            xstsBody["TokenType"] = "JWT";
            var xsts = await Net.PostJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null);
            var xstsObj = J.ParseObj(xsts.Body);
            if (!xsts.Ok) throw new AuthException(XstsError(J.Str(xstsObj, "XErr"), xsts.Status));
            string xstsToken = J.Str(xstsObj, "Token");
            var xui = J.Arr(J.Obj(xstsObj, "DisplayClaims"), "xui");
            string uhs = xui != null && xui.Length > 0 ? J.Str(xui[0], "uhs") : null;
            string xuid = xui != null && xui.Length > 0 ? J.Str(xui[0], "xid") : null;

            // Minecraft
            status.Report("Signing in to Minecraft...");
            var mcBody = new Dictionary<string, object>();
            mcBody["identityToken"] = "XBL3.0 x=" + uhs + ";" + xstsToken;
            var mc = await Net.PostJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody, null);
            if (mc.Status == 403)
                throw new AuthException("Mojang hasn't approved your Azure app for Minecraft login yet. " +
                                        "Microsoft sign-in worked, but Minecraft blocks unapproved launchers. See SETUP-LOGIN.txt for how to apply.");
            if (!mc.Ok) throw new AuthException("Minecraft sign-in failed (HTTP " + mc.Status + ").");
            var mcObj = J.ParseObj(mc.Body);
            string mcToken = J.Str(mcObj, "access_token");
            long expiresIn = J.Long(mcObj, "expires_in");

            // Profile (name, UUID, skin)
            status.Report("Loading your profile...");
            var prof = await Net.GetAuthed("https://api.minecraftservices.com/minecraft/profile", mcToken);
            if (prof.Status == 404) throw new AuthException("This Microsoft account doesn't own Minecraft: Java Edition.");
            if (!prof.Ok) throw new AuthException("Couldn't load your Minecraft profile (HTTP " + prof.Status + ").");
            var p = J.ParseObj(prof.Body);

            var a = new Account();
            a.Name = J.Str(p, "name");
            a.Uuid = J.Str(p, "id");
            a.Xuid = xuid ?? "0";
            a.McToken = mcToken;
            a.McTokenExpiresUtcTicks = DateTime.UtcNow.AddSeconds(expiresIn > 0 ? expiresIn : 86400).Ticks;
            a.RefreshToken = refreshToken;
            var skins = J.Arr(p, "skins");
            if (skins != null)
                foreach (var s in skins)
                    if (J.Str(s, "state") == "ACTIVE") { a.SkinUrl = J.Str(s, "url"); break; }
            a.Save();
            Log.Write("Signed in as " + a.Name);
            return a;
        }

        static string MicrosoftError(Dictionary<string, object> o, string fallback)
        {
            string desc = J.Str(o, "error_description");
            if (string.IsNullOrEmpty(desc)) return fallback;
            int cut = desc.IndexOf("\r\n", StringComparison.Ordinal);
            return fallback + " (" + (cut > 0 ? desc.Substring(0, cut) : desc) + ")";
        }

        static string XstsError(string xerr, int status)
        {
            switch (xerr)
            {
                case "2148916233": return "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com, then try again.";
                case "2148916235": return "Xbox Live isn't available in your country/region.";
                case "2148916236":
                case "2148916237": return "This account needs adult verification on xbox.com (South Korea).";
                case "2148916238": return "This is a child account. An adult must add it to a Microsoft Family to play.";
                default: return "Xbox profile check failed (HTTP " + status + (xerr != null ? ", code " + xerr : "") + ").";
            }
        }
    }
}
