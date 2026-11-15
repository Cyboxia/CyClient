// Cy Launcher - downloads and verifies everything needed to play, then builds the launch command.
// Everything comes from official sources: Mojang (game, assets, Java), Fabric (loader), Modrinth (Fabric API).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Win32;

namespace CyLauncher
{
    class InstallStatus
    {
        public string Status;
        public double Fraction; // 0..1, or -1 for "busy" (no bar)
        public InstallStatus(string status, double fraction) { Status = status; Fraction = fraction; }
    }

    class Download
    {
        public string Url, Path, Sha1;
        public long Size;
        public Download(string url, string path, string sha1, long size) { Url = url; Path = path; Sha1 = sha1; Size = size; }
    }

    class LaunchPlan
    {
        public string JavaExe;
        public string ArgsFile;
    }

    static class Installer
    {
        public const string McVersion = "26.3";
        const string Manifest = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        const string JavaRuntimes = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
        const int Parallel = 12;

        /// <summary>Make sure everything is installed and up to date, and write the launch arguments.</summary>
        public static async Task<LaunchPlan> Prepare(LauncherSettings settings, Account account, bool verifyHashes, IProgress<InstallStatus> progress)
        {
            foreach (var d in new[] { Paths.Root, Paths.Libraries, Paths.Assets, Paths.Versions, Paths.Runtime, Paths.Game, Paths.Natives })
                Directory.CreateDirectory(d);

            // 1. Game version info (cached so the launcher still works offline once installed)
            progress.Report(new InstallStatus("Checking for updates...", -1));
            var version = await CachedJson(Path.Combine(Paths.Versions, McVersion, McVersion + ".json"), async () =>
            {
                var manifest = J.Parse(await Net.GetString(Manifest));
                foreach (var v in J.Arr(manifest, "versions"))
                    if (J.Str(v, "id") == McVersion) return await Net.GetString(J.Str(v, "url"));
                throw new Exception("Minecraft " + McVersion + " was not found in Mojang's version list.");
            });

            // 2. Fabric loader (newest stable for this Minecraft version)
            var fabric = await CachedJson(Path.Combine(Paths.Versions, "fabric-" + McVersion + ".json"), async () =>
            {
                var loaders = J.Parse(await Net.GetString("https://meta.fabricmc.net/v2/versions/loader/" + McVersion)) as object[];
                string loaderVersion = null;
                if (loaders != null)
                    foreach (var l in loaders)
                    {
                        var loader = J.Obj(l, "loader");
                        if (J.Bool(loader, "stable")) { loaderVersion = J.Str(loader, "version"); break; }
                    }
                if (loaderVersion == null) throw new Exception("No stable Fabric loader found for " + McVersion + ".");
                return await Net.GetString("https://meta.fabricmc.net/v2/versions/loader/" + McVersion + "/" + loaderVersion + "/profile/json");
            });

            var downloads = new List<Download>();
            var classpath = new List<string>();
            var seen = new HashSet<string>();

            // 3. Fabric libraries first (they win over vanilla copies of the same library)
            foreach (var lib in J.Arr(fabric, "libraries"))
            {
                string name = J.Str(lib, "name");
                string rel = Paths.MavenPath(name);
                string path = Path.Combine(Paths.Libraries, rel.Replace('/', '\\'));
                string url = (J.Str(lib, "url") ?? "https://maven.fabricmc.net/").TrimEnd('/') + "/" + rel;
                downloads.Add(new Download(url, path, J.Str(lib, "sha1"), J.Long(lib, "size")));
                seen.Add(LibKey(name));
                classpath.Add(path);
            }

            // 4. Vanilla libraries (only the ones meant for Windows on this CPU)
            foreach (var lib in J.Arr(version, "libraries"))
            {
                if (!Rules.Allowed(J.Get(lib, "rules"))) continue;
                string name = J.Str(lib, "name");
                if (!seen.Add(LibKey(name))) continue;
                var art = J.Obj(J.Obj(lib, "downloads"), "artifact");
                if (art == null) continue;
                string path = Path.Combine(Paths.Libraries, J.Str(art, "path").Replace('/', '\\'));
                downloads.Add(new Download(J.Str(art, "url"), path, J.Str(art, "sha1"), J.Long(art, "size")));
                classpath.Add(path);
            }

            // 5. The game itself
            var client = J.Obj(J.Obj(version, "downloads"), "client");
            string clientJar = Path.Combine(Paths.Versions, McVersion, McVersion + ".jar");
            downloads.Add(new Download(J.Str(client, "url"), clientJar, J.Str(client, "sha1"), J.Long(client, "size")));
            classpath.Add(clientJar);

            // 6. Logging config
            string loggingArg = null;
            var logging = J.Obj(J.Obj(version, "logging"), "client");
            if (logging != null)
            {
                var file = J.Obj(logging, "file");
                string logPath = Path.Combine(Paths.Assets, "log_configs", J.Str(file, "id"));
                downloads.Add(new Download(J.Str(file, "url"), logPath, J.Str(file, "sha1"), J.Long(file, "size")));
                loggingArg = J.Str(logging, "argument").Replace("${path}", logPath);
            }

            // 7. Assets (sounds, languages, textures that aren't in the jar)
            var assetIndex = J.Obj(version, "assetIndex");
            string assetId = J.Str(assetIndex, "id");
            string indexPath = Path.Combine(Paths.Assets, "indexes", assetId + ".json");
            await EnsureFile(new Download(J.Str(assetIndex, "url"), indexPath, J.Str(assetIndex, "sha1"), J.Long(assetIndex, "size")), verifyHashes);
            var objects = J.Obj(J.ParseObj(File.ReadAllText(indexPath)), "objects");
            foreach (var kv in objects)
            {
                string hash = J.Str(kv.Value, "hash");
                string sub = hash.Substring(0, 2);
                downloads.Add(new Download("https://resources.download.minecraft.net/" + sub + "/" + hash,
                    Path.Combine(Paths.Assets, "objects", sub, hash), hash, J.Long(kv.Value, "size")));
            }

            // 8. Java (Mojang's official runtime for this version, unless a custom one is set)
            string javaExe;
            if (settings.UseCustomJava && !string.IsNullOrWhiteSpace(settings.JavaPath))
            {
                javaExe = settings.JavaPath.Trim().Trim('"');
                if (!File.Exists(javaExe)) throw new Exception("The custom Java path in Settings doesn't exist:\n" + javaExe);
            }
            else
            {
                progress.Report(new InstallStatus("Checking Java...", -1));
                string component = J.Str(J.Obj(version, "javaVersion"), "component") ?? "java-runtime-epsilon";
                string runtimeDir = Path.Combine(Paths.Runtime, component);
                downloads.AddRange(await RuntimeDownloads(component, runtimeDir));
                javaExe = Path.Combine(runtimeDir, "bin", "javaw.exe");
            }

            // 9. Fabric API from Modrinth
            progress.Report(new InstallStatus("Checking mods...", -1));
            string modsDir = Path.Combine(Paths.Game, "mods");
            Directory.CreateDirectory(modsDir);
            var fabricApi = await FabricApiDownload(modsDir);
            if (fabricApi != null) downloads.Add(fabricApi);

            // 10. Download everything that's missing or changed
            await DownloadAll(downloads, verifyHashes, progress);
            if (fabricApi != null) RemoveOthers(modsDir, "fabric-api-", Path.GetFileName(fabricApi.Path));

            // 11. Cy Client itself (shipped next to the launcher)
            InstallCyClient(modsDir);

            // 12. Launch arguments
            progress.Report(new InstallStatus("Starting Minecraft...", -1));
            var plan = new LaunchPlan();
            plan.JavaExe = javaExe;
            plan.ArgsFile = WriteArgs(version, fabric, classpath, loggingArg, assetId, settings, account);
            return plan;
        }

        /// <summary>Starts the game. Returns the process (the launcher may close; the game keeps running).</summary>
        public static Process Launch(LaunchPlan plan)
        {
            var psi = new ProcessStartInfo(plan.JavaExe, "\"@" + plan.ArgsFile + "\"");
            psi.WorkingDirectory = Paths.Game;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            Log.Write("Launching: " + plan.JavaExe + " @" + plan.ArgsFile);
            return Process.Start(psi);
        }

        // ------------------------------------------------------------------ helpers

        static string LibKey(string mavenName)
        {
            var p = mavenName.Split(':');
            return p[0] + ":" + p[1] + (p.Length > 3 ? ":" + p[3] : "");
        }

        /// <summary>Downloads JSON (and caches it); falls back to the cached copy if offline.</summary>
        static async Task<Dictionary<string, object>> CachedJson(string cachePath, Func<Task<string>> fetch)
        {
            try
            {
                string json = await fetch();
                Directory.CreateDirectory(Path.GetDirectoryName(cachePath));
                File.WriteAllText(cachePath, json);
                return J.ParseObj(json);
            }
            catch (Exception e)
            {
                if (File.Exists(cachePath))
                {
                    Log.Write("Using cached " + cachePath + " (" + e.Message + ")");
                    return J.ParseObj(File.ReadAllText(cachePath));
                }
                throw;
            }
        }

        static async Task<List<Download>> RuntimeDownloads(string component, string runtimeDir)
        {
            var list = new List<Download>();
            string manifestCache = Path.Combine(Paths.Runtime, component + ".manifest.json");
            var manifest = await CachedJson(manifestCache, async () =>
            {
                var all = J.Parse(await Net.GetString(JavaRuntimes));
                string platform = Rules.IsArm64 ? "windows-arm64" : "windows-x64";
                var entries = J.Arr(J.Obj(all, platform), component);
                if (entries == null || entries.Length == 0) throw new Exception("Mojang has no " + component + " Java for " + platform + ".");
                return await Net.GetString(J.Str(J.Obj(entries[0], "manifest"), "url"));
            });
            foreach (var kv in J.Obj(manifest, "files"))
            {
                if (J.Str(kv.Value, "type") != "file") continue;
                var raw = J.Obj(J.Obj(kv.Value, "downloads"), "raw");
                list.Add(new Download(J.Str(raw, "url"), Path.Combine(runtimeDir, kv.Key.Replace('/', '\\')), J.Str(raw, "sha1"), J.Long(raw, "size")));
            }
            return list;
        }

        static async Task<Download> FabricApiDownload(string modsDir)
        {
            try
            {
                string url = "https://api.modrinth.com/v2/project/fabric-api/version?loaders=" + Uri.EscapeDataString("[\"fabric\"]")
                             + "&game_versions=" + Uri.EscapeDataString("[\"" + McVersion + "\"]");
                var versions = J.Parse(await Net.GetString(url)) as object[];
                if (versions == null || versions.Length == 0) throw new Exception("No Fabric API build for " + McVersion + " on Modrinth.");
                foreach (var f in J.Arr(versions[0], "files"))
                {
                    if (!J.Bool(f, "primary")) continue;
                    return new Download(J.Str(f, "url"), Path.Combine(modsDir, J.Str(f, "filename")),
                        J.Str(J.Obj(f, "hashes"), "sha1"), J.Long(f, "size"));
                }
                throw new Exception("Fabric API download not found.");
            }
            catch (Exception e)
            {
                // Offline but already installed? Keep using it.
                if (Directory.GetFiles(modsDir, "fabric-api-*.jar").Length > 0) { Log.Write("Fabric API check skipped: " + e.Message); return null; }
                throw;
            }
        }

        static void InstallCyClient(string modsDir)
        {
            string source = null;
            foreach (var dir in new[] { Paths.ExeDir, Path.Combine(Paths.ExeDir, "mods") })
            {
                if (!Directory.Exists(dir)) continue;
                var jars = Directory.GetFiles(dir, "cyclient*.jar").Where(f => !f.EndsWith("-sources.jar")).OrderByDescending(File.GetLastWriteTimeUtc).ToArray();
                if (jars.Length > 0) { source = jars[0]; break; }
            }
            if (source == null)
            {
                if (Directory.GetFiles(modsDir, "cyclient*.jar").Length > 0) return; // keep the installed copy
                throw new Exception("Cy Client's mod file (cyclient-*.jar) wasn't found next to CyLauncher.exe.\nRun build-launcher.bat to create it.");
            }
            string target = Path.Combine(modsDir, Path.GetFileName(source));
            if (!File.Exists(target) || new FileInfo(target).Length != new FileInfo(source).Length
                || File.GetLastWriteTimeUtc(target) < File.GetLastWriteTimeUtc(source))
            {
                File.Copy(source, target, true);
            }
            RemoveOthers(modsDir, "cyclient", Path.GetFileName(source));
        }

        static void RemoveOthers(string dir, string prefix, string keepFile)
        {
            foreach (var f in Directory.GetFiles(dir, prefix + "*.jar"))
                if (!string.Equals(Path.GetFileName(f), keepFile, StringComparison.OrdinalIgnoreCase))
                    try { File.Delete(f); } catch { }
        }

        // ------------------------------------------------------------------ downloading

        static async Task DownloadAll(List<Download> all, bool verifyHashes, IProgress<InstallStatus> progress)
        {
            progress.Report(new InstallStatus("Checking files...", -1));
            // Work out what's missing on a background thread (hashing can take a moment).
            var needed = await Task.Run(() => all.Where(d => !IsValid(d, verifyHashes)).GroupBy(d => d.Path.ToLowerInvariant()).Select(g => g.First()).ToList());
            if (needed.Count == 0) return;

            long total = Math.Max(1, needed.Sum(d => d.Size));
            long done = 0;
            int filesDone = 0;
            var gate = new SemaphoreSlim(Parallel);
            var lastReport = DateTime.MinValue;
            var reportLock = new object();

            var tasks = needed.Select(async d =>
            {
                await gate.WaitAsync();
                try { await EnsureFile(d, true); }
                finally { gate.Release(); }
                long nowDone = Interlocked.Add(ref done, d.Size);
                int files = Interlocked.Increment(ref filesDone);
                lock (reportLock)
                {
                    if ((DateTime.UtcNow - lastReport).TotalMilliseconds < 100 && files < needed.Count) return;
                    lastReport = DateTime.UtcNow;
                }
                progress.Report(new InstallStatus(string.Format("Downloading {0} of {1} files  ({2:0.0} / {3:0.0} MB)",
                    files, needed.Count, nowDone / 1048576.0, total / 1048576.0), (double)nowDone / total));
            }).ToList();
            await Task.WhenAll(tasks);
        }

        static bool IsValid(Download d, bool verifyHash)
        {
            if (!File.Exists(d.Path)) return false;
            if (d.Size > 0 && new FileInfo(d.Path).Length != d.Size) return false;
            if (verifyHash && !string.IsNullOrEmpty(d.Sha1)) return string.Equals(Hash.Sha1File(d.Path), d.Sha1, StringComparison.OrdinalIgnoreCase);
            return true;
        }

        static async Task EnsureFile(Download d, bool verifyHash)
        {
            if (IsValid(d, verifyHash)) return;
            Exception last = null;
            for (int attempt = 1; attempt <= 3; attempt++)
            {
                try
                {
                    byte[] data = await Net.GetBytes(d.Url);
                    if (!string.IsNullOrEmpty(d.Sha1) && !string.Equals(Hash.Sha1(data), d.Sha1, StringComparison.OrdinalIgnoreCase))
                        throw new Exception("Checksum mismatch for " + Path.GetFileName(d.Path));
                    Directory.CreateDirectory(Path.GetDirectoryName(d.Path));
                    string tmp = d.Path + ".part";
                    File.WriteAllBytes(tmp, data);
                    if (File.Exists(d.Path)) File.Delete(d.Path);
                    File.Move(tmp, d.Path);
                    return;
                }
                catch (Exception e)
                {
                    last = e; // C# 5 can't wait inside a catch block, so the retry pause is below
                }
                await Task.Delay(500 * attempt);
            }
            throw new Exception("Couldn't download " + Path.GetFileName(d.Path) + ": " + (last != null ? last.Message : "unknown error"));
        }

        // ------------------------------------------------------------------ arguments

        static string WriteArgs(Dictionary<string, object> version, Dictionary<string, object> fabric, List<string> classpath,
                                string loggingArg, string assetId, LauncherSettings settings, Account account)
        {
            var vars = new Dictionary<string, string>();
            vars["auth_player_name"] = account.Name;
            vars["version_name"] = J.Str(fabric, "id") ?? McVersion;
            vars["game_directory"] = Paths.Game;
            vars["assets_root"] = Paths.Assets;
            vars["assets_index_name"] = assetId;
            vars["auth_uuid"] = account.Uuid;
            vars["auth_access_token"] = account.McToken;
            vars["clientid"] = settings.ClientId;
            vars["auth_xuid"] = account.Xuid ?? "0";
            vars["user_type"] = account.IsOffline ? "legacy" : "msa";
            vars["version_type"] = "release";
            vars["natives_directory"] = Paths.Natives;
            vars["launcher_name"] = "cy-launcher";
            vars["launcher_version"] = "1.0";
            vars["classpath"] = string.Join(";", classpath.Distinct(StringComparer.OrdinalIgnoreCase));
            vars["classpath_separator"] = ";";
            vars["library_directory"] = Paths.Libraries;

            var jvm = new List<string>();
            AddArgs(jvm, J.Arr(J.Obj(version, "arguments"), "jvm"), vars);
            AddArgs(jvm, J.Arr(J.Obj(fabric, "arguments"), "jvm"), vars);
            if (loggingArg != null) jvm.Add(loggingArg);
            int mem = Math.Max(1024, settings.MemoryMb);
            jvm.Add("-Xms" + Math.Min(mem, 2048) + "M"); // these come last, so they override the defaults above
            jvm.Add("-Xmx" + mem + "M");

            var game = new List<string>();
            AddArgs(game, J.Arr(J.Obj(version, "arguments"), "game"), vars);
            AddArgs(game, J.Arr(J.Obj(fabric, "arguments"), "game"), vars);

            var all = new List<string>(jvm);
            all.Add(J.Str(fabric, "mainClass") ?? J.Str(version, "mainClass"));
            all.AddRange(game);

            // A Java "@argfile": avoids Windows' command-line length limit with a long classpath.
            var sb = new StringBuilder();
            foreach (var a in all) sb.Append('"').Append(a.Replace('\\', '/').Replace("\"", "\\\"")).Append('"').Append('\n');
            string file = Path.Combine(Paths.Root, "launch-args.txt");
            File.WriteAllText(file, sb.ToString(), new UTF8Encoding(false));
            return file;
        }

        static void AddArgs(List<string> into, object[] args, Dictionary<string, string> vars)
        {
            if (args == null) return;
            foreach (var a in args)
            {
                if (a is string) { into.Add(Fill((string)a, vars)); continue; }
                if (!Rules.Allowed(J.Get(a, "rules"))) continue;
                var value = J.Get(a, "value");
                if (value is string) into.Add(Fill((string)value, vars));
                else
                {
                    var list = value as object[];
                    if (list != null) foreach (var v in list) into.Add(Fill(Convert.ToString(v), vars));
                }
            }
        }

        static string Fill(string s, Dictionary<string, string> vars)
        {
            return Regex.Replace(s, @"\$\{([a-zA-Z_]+)\}", m =>
            {
                string v;
                return vars.TryGetValue(m.Groups[1].Value, out v) ? v : m.Value;
            });
        }
    }

    /// <summary>Mojang's "rules" (which libraries/arguments apply to this computer).</summary>
    static class Rules
    {
        static string osVersion;

        public static bool IsArm64
        {
            get
            {
                string a = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITEW6432") ?? Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE") ?? "";
                return a.ToUpperInvariant() == "ARM64";
            }
        }

        public static bool Allowed(object rulesObj)
        {
            var rules = rulesObj as object[];
            if (rules == null) return true;
            bool allow = false;
            foreach (var r in rules)
            {
                bool match = true;
                var os = J.Obj(r, "os");
                if (os != null)
                {
                    string name = J.Str(os, "name");
                    if (name != null && name != "windows") match = false;
                    string arch = J.Str(os, "arch");
                    if (arch != null && !ArchMatches(arch)) match = false;
                    string ver = J.Str(os, "version");
                    if (ver != null && !Regex.IsMatch(OsVersion(), ver)) match = false;
                }
                if (J.Obj(r, "features") != null) match = false; // demo mode, custom resolution, quick play: not used
                if (match) allow = J.Str(r, "action") == "allow";
            }
            return allow;
        }

        static bool ArchMatches(string arch)
        {
            switch (arch)
            {
                case "x86": return !Environment.Is64BitOperatingSystem;
                case "arm64": return IsArm64;
                case "x86_64":
                case "amd64": return Environment.Is64BitOperatingSystem && !IsArm64;
                default: return false;
            }
        }

        /// <summary>Real Windows version, e.g. "10.0.26100" (read from the registry, which is always accurate).</summary>
        static string OsVersion()
        {
            if (osVersion != null) return osVersion;
            try
            {
                using (var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion"))
                {
                    object major = key.GetValue("CurrentMajorVersionNumber"), minor = key.GetValue("CurrentMinorVersionNumber"), build = key.GetValue("CurrentBuildNumber");
                    if (major != null && minor != null && build != null) return osVersion = major + "." + minor + "." + build;
                }
            }
            catch { }
            return osVersion = Environment.OSVersion.Version.ToString(3);
        }
    }
}
