// Cy Launcher - reads the account you're already signed into in the official Minecraft Launcher.
// The launcher stores a usable Minecraft token in launcher_accounts.json; we reuse it so Cy Launcher
// needs no separate sign-in and no Azure approval.
using System;
using System.Collections.Generic;
using System.IO;

namespace CyLauncher
{
    class LauncherAccountInfo
    {
        public string AccessToken;
        public string Uuid;
        public string Name;
    }

    static class LauncherAccountReader
    {
        public static string DefaultFile
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft", "launcher_accounts.json"); }
        }

        /// <summary>True if the official Minecraft Launcher's account file exists at all.</summary>
        public static bool LauncherPresent { get { return File.Exists(DefaultFile); } }

        /// <summary>The active Java Edition account from the official launcher, or null if there isn't one.</summary>
        public static LauncherAccountInfo ReadActive()
        {
            string file = DefaultFile;
            if (!File.Exists(file)) return null;

            Dictionary<string, object> root;
            try { root = J.ParseObj(File.ReadAllText(file)); }
            catch (Exception e) { Log.Write("launcher_accounts.json unreadable: " + e.Message); return null; }
            if (root == null) return null;

            var accounts = J.Obj(root, "accounts");
            if (accounts == null || accounts.Count == 0) return null;

            // Prefer the account marked active; it must have a Java profile and a token.
            string active = J.Str(root, "activeAccountLocalId");
            if (active != null && accounts.ContainsKey(active))
            {
                var info = FromEntry(accounts[active] as Dictionary<string, object>);
                if (info != null) return info;
            }
            // Otherwise take the first usable one.
            foreach (var kv in accounts)
            {
                var info = FromEntry(kv.Value as Dictionary<string, object>);
                if (info != null) return info;
            }
            return null;
        }

        static LauncherAccountInfo FromEntry(Dictionary<string, object> acct)
        {
            if (acct == null) return null;
            string token = J.Str(acct, "accessToken");
            var profile = J.Obj(acct, "minecraftProfile");
            if (token == null || profile == null) return null;
            string uuid = J.Str(profile, "id");
            string name = J.Str(profile, "name");
            if (string.IsNullOrEmpty(uuid) || string.IsNullOrEmpty(name)) return null;
            var info = new LauncherAccountInfo();
            info.AccessToken = token;
            info.Uuid = uuid;
            info.Name = name;
            return info;
        }
    }
}
