// Cy Launcher - the window. Monochrome theme, drawn by hand for a clean look and a tiny footprint:
// it only redraws when something changes, and closes itself once the game starts (by default).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace CyLauncher
{
    class MainForm : Form
    {
        // ---------------------------------------------------------------- look (monochrome)
        static readonly Color Bg = Color.FromArgb(13, 14, 16);
        static readonly Color Bar = Color.FromArgb(18, 19, 22);
        static readonly Color Card = Color.FromArgb(27, 29, 33);
        static readonly Color CardHover = Color.FromArgb(37, 40, 45);
        static readonly Color Field = Color.FromArgb(150, 18, 19, 23);
        static readonly Color Line = Color.FromArgb(44, 47, 53);
        static readonly Color TextC = Color.FromArgb(240, 242, 245);
        static readonly Color Dim = Color.FromArgb(138, 142, 149);
        static readonly Color Accent = Color.FromArgb(236, 238, 242);     // near-white "primary"
        static readonly Color AccentHover = Color.FromArgb(255, 255, 255);
        static readonly Color OnAccent = Color.FromArgb(14, 15, 17);       // near-black text on white buttons
        static readonly Color Danger = Color.FromArgb(70, 73, 79);         // muted gray (monochrome)

        const int W = 960, H = 600, TitleH = 52;   // W/H = the content area (right of the sidebar)
        const int SIDEBAR_W = 76;
        const int CANVAS_W = SIDEBAR_W + W;         // whole window in design units
        const int CANVAS_H = H;
        const int MIN_CLIENT_W = 820;               // smallest the window scales down to

        float contentDX;                            // device-px x-offset applied while drawing a content page
        bool resizing;
        Size normalSize;                            // remembered size for the maximize/restore toggle
        bool maximized;

        readonly Font fTitle = UiFonts.Get(UiFonts.Bold, 32f, FontStyle.Bold);
        readonly Font fH = UiFonts.Get(UiFonts.SemiBold, 11.5f, FontStyle.Bold);
        readonly Font fBody = UiFonts.Get(UiFonts.Regular, 10.5f, FontStyle.Regular);
        readonly Font fSmall = UiFonts.Get(UiFonts.Regular, 9.5f, FontStyle.Regular);
        readonly Font fLaunch = UiFonts.Get(UiFonts.Bold, 17f, FontStyle.Bold);
        readonly Font fCode = new Font("Consolas", 26f, FontStyle.Bold);

        // ---------------------------------------------------------------- state
        readonly LauncherSettings settings;
        Account account;
        Image logo, banner, avatar, bg, bgBlur;
        float S = 1f; // DPI scale
        string page = "play";

        // 3D skin viewer (Welcome panel)
        Skin3D skin3d;        // the signed-in player's skin, once loaded
        Skin3D defaultSkin;   // a built-in Steve, shown before sign-in
        bool draggingSkin;
        Point skinDragLast;
        RectangleF skinViewRect;   // ABSOLUTE device rect of the skin box (for input + fast repaint)
        PointF skinCenter;         // ABSOLUTE centre the model is drawn around
        float skinScale;
        Bitmap skinUnderlay;       // cached background behind the model, so dragging never re-blurs it
        string skinUnderlaySig = "";

        // Skin editor (page == "skin")
        Bitmap editSkin;           // the 64x64 texture being edited
        Skin3D editView;           // live 3D preview (shares editSkin, does not own it)
        bool editSlim;
        Color editColor = Color.FromArgb(255, 60, 150, 155);
        int editTool;              // 0 pencil, 1 eraser, 2 eyedropper, 3 fill
        bool editPainting, editRotating, editPainting3d;
        Point editPaintLast;   // last painted pixel (for continuous strokes)
        Point editDragLast;    // last mouse point while rotating the preview
        readonly List<Bitmap> undoStack = new List<Bitmap>();
        readonly List<Bitmap> redoStack = new List<Bitmap>();
        const int UndoCap = 60;
        float edZoom;          // device px per texel on the canvas (0 = fit to panel on next draw)
        float edPanX, edPanY;  // canvas pan offset from centred, device px
        float edPreviewZoom = 1f;
        RectangleF edCanvasPanel; // ABSOLUTE canvas panel rect (hit-test + wheel)
        RectangleF edCanvasRect;   // ABSOLUTE canvas rect (the zoomed 64x64)
        RectangleF edPreviewRect;  // ABSOLUTE preview panel rect
        PointF edPreviewCenter;
        float edPreviewScale;
        static readonly Color[] EditPalette = {
            Color.FromArgb(255,0,0,0), Color.FromArgb(255,70,70,76), Color.FromArgb(255,128,128,134), Color.FromArgb(255,235,235,235),
            Color.FromArgb(255,189,143,102), Color.FromArgb(255,150,110,74), Color.FromArgb(255,110,78,52), Color.FromArgb(255,70,48,30),
            Color.FromArgb(255,196,66,66), Color.FromArgb(255,224,120,60), Color.FromArgb(255,232,196,80), Color.FromArgb(255,120,180,70),
            Color.FromArgb(255,60,150,155), Color.FromArgb(255,70,110,200), Color.FromArgb(255,60,62,120), Color.FromArgb(255,150,80,170),
        };

        /// <summary>The skin currently on show: the account's if loaded, otherwise the built-in default.</summary>
        Skin3D ActiveSkin()
        {
            if (skin3d != null) return skin3d;
            if (defaultSkin == null) { try { defaultSkin = Skin3D.Default(); } catch (Exception e) { Log.Write("Default skin: " + e.Message); } }
            return defaultSkin;
        }

        bool busy;
        string status = "";
        double fraction = -1;
        bool repairNext;
        readonly System.Windows.Forms.Timer anim = new System.Windows.Forms.Timer();
        int animTick;

        // Overlay (sign-in code or a message)
        bool overlay;
        string ovTitle, ovText, ovCode, ovStatus;
        string ovPrimary, ovSecondary;
        Action ovPrimaryAction, ovSecondaryAction;
        CancellationTokenSource signInCancel;

        // Hit testing
        class Hot { public RectangleF R; public Action Click; public string Id; }
        readonly List<Hot> hots = new List<Hot>();
        string hoverId, pressId;
        bool draggingSlider;
        RectangleF sliderTrack;

        TextBox clientIdBox, javaBox, searchBox, offlineNameBox;
        readonly int maxMemoryMb;

        // Mod Explorer (Modrinth)
        List<ModHit> modResults = new List<ModHit>();
        List<InstalledMod> installedMods = new List<InstalledMod>();
        bool modsBusy;
        string modsMsg = "";
        string modBusyId;            // ProjectId being installed right now (null = idle)
        float modScroll, modScrollMax;

        bool UseLauncherAccount { get { return settings.SignInMethod == "launcher"; } }
        bool UseOffline { get { return settings.SignInMethod == "offline"; } }
        string EffectiveClientId { get { return settings.ClientId == null ? "" : settings.ClientId.Trim(); } }

        public MainForm()
        {
            settings = LauncherSettings.Load();
            account = Account.Load();
            maxMemoryMb = Math.Max(4096, (int)(TotalRamMb() * 0.75) / 512 * 512);

            Text = "Cy Launcher";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            try { S = DeviceDpi / 96f; } catch { S = 1f; }
            if (S < 0.5f || S > 8f) S = 1f;
            ClientSize = new Size((int)Math.Round(CANVAS_W * S), (int)Math.Round(CANVAS_H * S));
            MinimumSize = new Size(MIN_CLIENT_W, (int)Math.Round(MIN_CLIENT_W * (double)CANVAS_H / CANVAS_W) + 1);
            normalSize = ClientSize;

            logo = LoadImage("CyLauncher.logo.png");
            banner = LoadImage("CyLauncher.banner.png");
            bg = LoadImage("CyLauncher.bg.png");
            bgBlur = LoadImage("CyLauncher.bg_blur.png");

            clientIdBox = MakeTextBox(settings.ClientId, delegate { settings.ClientId = clientIdBox.Text.Trim(); settings.Save(); });
            javaBox = MakeTextBox(settings.JavaPath, delegate { settings.JavaPath = javaBox.Text.Trim(); settings.Save(); });
            offlineNameBox = MakeTextBox(settings.OfflineName, delegate { settings.OfflineName = offlineNameBox.Text.Trim(); settings.Save(); Invalidate(); });
            offlineNameBox.MaxLength = 16;

            installedMods = ModStore.Load();
            searchBox = MakeTextBox("", null);
            searchBox.KeyDown += delegate(object s, KeyEventArgs ke) { if (ke.KeyCode == Keys.Enter) { ke.SuppressKeyPress = true; RunSearch(); } };

            anim.Interval = 16;
            anim.Tick += delegate { animTick++; Invalidate(); };

            if (UseOffline) LoadOfflineSkin();
            else if (account != null) LoadAvatarAsync();
            else if (UseLauncherAccount) TryAutoLoadLauncherAccount();
            UpdateControls();
        }

        // ---------------------------------------------------------------- window chrome

        protected override CreateParams CreateParams
        {
            get
            {
                var cp = base.CreateParams;
                cp.ClassStyle |= 0x20000;   // CS_DROPSHADOW
                cp.Style |= 0x20000;        // WS_MINIMIZEBOX
                return cp;
            }
        }

        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
        [DllImport("user32.dll")] static extern bool ReleaseCapture();
        [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            try { int round = 2; DwmSetWindowAttribute(Handle, 33, ref round, 4); } catch { } // Windows 11 rounded corners
            try { int dark = 1; DwmSetWindowAttribute(Handle, 20, ref dark, 4); } catch { }
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            Updater.CleanupOldFiles();
            CheckForUpdates();
        }

        /// <summary>On open, ask GitHub if there's a newer release and, if so, offer to install it.</summary>
        async void CheckForUpdates()
        {
            UpdateInfo info = await Updater.CheckAsync();
            if (info == null || overlay) return; // nothing new, or a dialog is already up
            ShowOverlay(
                "Update available",
                "Cy Client " + info.Version + " is ready. You're running " + Updater.CurrentVersion + ".",
                null,
                "Update now", delegate { StartUpdate(info); },
                "Later", delegate { HideOverlay(); });
        }

        /// <summary>Downloads the new files, swaps them in, and prompts to restart when needed.</summary>
        async void StartUpdate(UpdateInfo info)
        {
            // Button-less "working" overlay; ovText is updated as each file downloads.
            ShowOverlay("Updating Cy Client", "Starting...", null, null, null, null, null);

            ApplyResult res = await Updater.ApplyAsync(info, delegate(string s) { ovText = s; Invalidate(); });

            if (!string.IsNullOrEmpty(res.Error))
            {
                ShowOverlay("Update failed",
                    "Couldn't finish the update automatically. You can grab it from the release page instead.",
                    null,
                    "Open page", delegate { OpenUrl(info.PageUrl); HideOverlay(); },
                    "Close", delegate { HideOverlay(); });
                return;
            }

            if (res.ExeUpdated)
            {
                ShowOverlay("Update ready",
                    "Cy Client " + info.Version + " is installed. Restart the launcher to finish.",
                    null,
                    "Restart now", delegate { DoRestart(info); },
                    "Later", delegate { HideOverlay(); });
            }
            else if (res.JarUpdated)
            {
                ShowOverlay("Update downloaded",
                    "Cy Client " + info.Version + " will apply the next time you launch the game.",
                    null,
                    "OK", delegate { HideOverlay(); },
                    null, null);
            }
            else
            {
                ShowOverlay("Nothing to update",
                    "That release didn't have any downloadable files attached.",
                    null,
                    "Open page", delegate { OpenUrl(info.PageUrl); HideOverlay(); },
                    "Close", delegate { HideOverlay(); });
            }
        }

        /// <summary>Swaps in the new launcher exe and quits so the fresh copy takes over.</summary>
        void DoRestart(UpdateInfo info)
        {
            if (Updater.SwapAndRestart()) { Close(); return; }
            ShowOverlay("Couldn't restart",
                "The new launcher downloaded but couldn't be swapped in automatically. Open the release page to finish by hand.",
                null,
                "Open page", delegate { OpenUrl(info.PageUrl); HideOverlay(); },
                "Close", delegate { HideOverlay(); });
        }

        protected override void OnClientSizeChanged(EventArgs e)
        {
            base.OnClientSizeChanged(e);
            // The whole UI is drawn in design units scaled by S, so fitting the canvas to the window
            // resizes everything together. min() keeps it inside the window if the aspect differs.
            float s = Math.Min(ClientSize.Width / (float)CANVAS_W, ClientSize.Height / (float)CANVAS_H);
            S = s < 0.3f ? 0.3f : s;
            edZoom = 0; edPanX = edPanY = 0; // canvas refits to the new size
            if (clientIdBox != null) UpdateControls(); else Invalidate();
        }

        int D(float v) { return (int)Math.Round(v * S); }
        RectangleF R(float x, float y, float w, float h) { return new RectangleF(x * S, y * S, w * S, h * S); }

        // ---------------------------------------------------------------- painting

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;

            // The fast paths below only repaint a small region. They are only safe for a partial repaint;
            // on a full-window repaint we must draw everything, or the rest of the app is left black.
            Rectangle clip = e.ClipRectangle;
            bool fullPaint = clip.Width >= ClientSize.Width && clip.Height >= ClientSize.Height;

            // Fast path: while spinning the skin, repaint only the skin well (cached background + model),
            // skipping the whole-window background photo and panel blurs. This is what keeps it smooth.
            if (!fullPaint && draggingSkin && skinUnderlay != null)
            {
                var skf = ActiveSkin();
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.PixelOffsetMode = PixelOffsetMode.Half;
                g.DrawImageUnscaled(skinUnderlay, (int)Math.Round(skinViewRect.X), (int)Math.Round(skinViewRect.Y));
                if (skf != null)
                {
                    var c = g.Save();
                    g.SetClip(skinViewRect);
                    skf.Render(g, skinCenter.X, skinCenter.Y, skinScale);
                    g.Restore(c);
                }
                return;
            }

            // Same fast path for rotating the editor's preview model.
            if (!fullPaint && editRotating && editView != null)
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.PixelOffsetMode = PixelOffsetMode.Half;
                Fill(g, edPreviewRect, Color.FromArgb(255, 16, 17, 20), 12);
                var c = g.Save();
                g.SetClip(edPreviewRect);
                editView.Render(g, edPreviewCenter.X, edPreviewCenter.Y, edPreviewScale);
                g.Restore(c);
                return;
            }

            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            g.InterpolationMode = InterpolationMode.Bilinear;   // cheap resample for the big background images
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            hots.Clear();

            if (bg != null) g.DrawImage(bg, new Rectangle(0, 0, ClientSize.Width, ClientSize.Height));
            else g.FillRectangle(B(Bg), ClientRectangle);

            DrawTitleBar(g);
            DrawSidebar(g);

            // Content pages are drawn in their own 0..W space, shifted right past the sidebar.
            var cs = g.Save();
            g.TranslateTransform(SIDEBAR_W * S, 0);
            contentDX = SIDEBAR_W * S;
            if (page == "play") DrawPlay(g);
            else if (page == "mods") DrawMods(g);
            else if (page == "servers") DrawServers(g);
            else if (page == "skin") DrawSkinEditor(g);
            else DrawSettings(g);
            g.Restore(cs);
            contentDX = 0;

            if (overlay) DrawOverlay(g);

            // Resize grip (drag the bottom-right corner).
            if (!maximized)
                using (var gp = new Pen(Color.FromArgb(95, 255, 255, 255), 1f * S))
                {
                    float gx = ClientSize.Width, gy = ClientSize.Height;
                    g.DrawLine(gp, gx - 14 * S, gy - 3 * S, gx - 3 * S, gy - 14 * S);
                    g.DrawLine(gp, gx - 9 * S, gy - 3 * S, gx - 3 * S, gy - 9 * S);
                }
        }

        void DrawTitleBar(Graphics g)
        {
            Frost(g, R(0, 0, CANVAS_W, TitleH), 0);
            g.FillRectangle(B(Color.FromArgb(55, 255, 255, 255)), R(0, TitleH - 1, CANVAS_W, 1));
            if (banner != null) g.DrawImage(banner, R(22, 14, 24f * banner.Width / banner.Height, 24));
            else g.DrawString("Cy Launcher", fH, B(TextC), 22 * S, 17 * S);

            // Window buttons: minimize, maximize/restore, close.
            var min = R(CANVAS_W - 138, 0, 46, 36);
            var max = R(CANVAS_W - 92, 0, 46, 36);
            var close = R(CANVAS_W - 46, 0, 46, 36);
            Hotspot("min", min, delegate { WindowState = FormWindowState.Minimized; });
            Hotspot("max", max, delegate { ToggleMaximize(); });
            Hotspot("close", close, delegate { Close(); });
            if (hoverId == "min") g.FillRectangle(B(CardHover), min);
            if (hoverId == "max") g.FillRectangle(B(CardHover), max);
            if (hoverId == "close") g.FillRectangle(B(CardHover), close);
            using (var p = new Pen(TextC, 1.2f * S))
            {
                g.DrawLine(p, (CANVAS_W - 120) * S, 18 * S, (CANVAS_W - 110) * S, 18 * S);
                g.DrawRectangle(p, (CANVAS_W - 74) * S, 13 * S, 10 * S, 10 * S);
                g.DrawLine(p, (CANVAS_W - 28) * S, 13 * S, (CANVAS_W - 18) * S, 23 * S);
                g.DrawLine(p, (CANVAS_W - 18) * S, 13 * S, (CANVAS_W - 28) * S, 23 * S);
            }
        }

        // ---------------------------------------------------------------- sidebar

        void DrawSidebar(Graphics g)
        {
            Frost(g, R(0, TitleH, SIDEBAR_W, CANVAS_H - TitleH), 0);
            g.FillRectangle(B(Color.FromArgb(40, 255, 255, 255)), R(SIDEBAR_W - 1, TitleH, 1, CANVAS_H - TitleH));

            SidebarIcon(g, "play", TitleH + 24);
            SidebarIcon(g, "mods", TitleH + 92);
            SidebarIcon(g, "servers", TitleH + 160);
            SidebarIcon(g, "settings", CANVAS_H - 72);   // pinned to the bottom
        }

        void SidebarIcon(Graphics g, string id, float y)
        {
            float size = 44;
            float x = (SIDEBAR_W - size) / 2f;
            var r = R(x, y, size, size);
            Hotspot("nav-" + id, r, delegate { page = id; UpdateControls(); });
            bool active = page == id;
            if (active) { Fill(g, r, Color.FromArgb(40, 255, 255, 255), 10); Fill(g, R(2, y + 8, 3, size - 16), Accent, 1.5f); }
            else if (hoverId == "nav-" + id) Fill(g, r, Color.FromArgb(22, 255, 255, 255), 10);
            Color c = active ? TextC : (hoverId == "nav-" + id ? TextC : Dim);
            DrawNavGlyph(g, id, R(x + 11, y + 11, 22, 22), c);
        }

        /// <summary>Simple monochrome line icons for the sidebar.</summary>
        void DrawNavGlyph(Graphics g, string id, RectangleF b, Color c)
        {
            using (var pen = new Pen(c, 1.7f * S) { LineJoin = LineJoin.Round, StartCap = LineCap.Round, EndCap = LineCap.Round })
            {
                float x = b.X, y = b.Y, w = b.Width, h = b.Height, cx = x + w / 2f, cy = y + h / 2f;
                if (id == "play")
                {
                    // a cube
                    PointF[] top = { new PointF(cx, y), new PointF(x + w, y + h * 0.27f), new PointF(cx, y + h * 0.54f), new PointF(x, y + h * 0.27f) };
                    g.DrawPolygon(pen, top);
                    g.DrawLine(pen, x, y + h * 0.27f, x, y + h * 0.73f);
                    g.DrawLine(pen, x + w, y + h * 0.27f, x + w, y + h * 0.73f);
                    g.DrawLine(pen, cx, y + h * 0.54f, cx, y + h);
                    g.DrawLine(pen, x, y + h * 0.73f, cx, y + h);
                    g.DrawLine(pen, x + w, y + h * 0.73f, cx, y + h);
                }
                else if (id == "mods")
                {
                    // 2x2 rounded squares
                    float s = w * 0.38f, gap = w * 0.24f;
                    for (int ix = 0; ix < 2; ix++)
                        for (int iy = 0; iy < 2; iy++)
                            g.DrawRectangle(pen, x + ix * (s + gap), y + iy * (s + gap), s, s);
                }
                else if (id == "servers")
                {
                    // globe
                    g.DrawEllipse(pen, x, y, w, h);
                    g.DrawEllipse(pen, cx - w * 0.22f, y, w * 0.44f, h);
                    g.DrawLine(pen, x, cy, x + w, cy);
                }
                else // settings: gear as sliders
                {
                    for (int i = 0; i < 3; i++)
                    {
                        float ly = y + h * (0.2f + i * 0.3f);
                        g.DrawLine(pen, x, ly, x + w, ly);
                        float knob = x + w * (i == 1 ? 0.7f : 0.3f);
                        g.FillEllipse(B(c), knob - 2.4f * S, ly - 2.4f * S, 4.8f * S, 4.8f * S);
                    }
                }
            }
        }

        void DrawPlay(Graphics g)
        {
            const float colCx = 300;   // centre of the left (main) column

            // Logo
            if (logo != null) g.DrawImage(logo, R(colCx - 72, 92, 144, 144));

            // START | Fabric 26.3 split bar
            const float barW = 380, barH = 58, startW = 268, bY = 258;
            float bX = colCx - barW / 2f;
            var bar = R(bX, bY, barW, barH);
            var startCell = R(bX, bY, startW, barH);
            var verCell = R(bX + startW, bY, barW - startW, barH);
            Frost(g, bar, 14);
            Color startColor = busy ? Line : (hoverId == "play" ? AccentHover : Accent);
            var state = g.Save();
            using (var barPath = RoundRect(bar, 14 * S))
            {
                g.SetClip(barPath);
                g.SetClip(startCell, CombineMode.Intersect);
                g.FillRectangle(B(startColor), bar);
            }
            g.Restore(state);
            if (!busy) Hotspot("play", startCell, delegate { OnPlay(); });
            g.FillRectangle(B(Color.FromArgb(40, 255, 255, 255)), R(bX + startW, bY + 8, 1, barH - 16));
            Center(g, busy ? "PLEASE WAIT" : "LAUNCH", fLaunch, busy ? Dim : OnAccent, startCell);
            Center(g, "Fabric", fBody, Dim, new RectangleF(verCell.X, verCell.Y + 10 * S, verCell.Width, 20 * S));
            Center(g, Installer.McVersion, fH, TextC, new RectangleF(verCell.X, verCell.Y + verCell.Height - 28 * S, verCell.Width, 22 * S));

            // Status + progress (while working)
            if (busy || !string.IsNullOrEmpty(status))
            {
                CenterShadow(g, status, fSmall, Color.FromArgb(220, 225, 227, 231), R(bX, 326, barW, 18));
                var track = R(bX, 346, barW, 5);
                Fill(g, track, Line, 2.5f);
                if (fraction >= 0)
                {
                    float w = (float)(barW * Math.Min(1, fraction));
                    if (w > 1) Fill(g, R(bX, 346, w, 5), Accent, 2.5f);
                }
                else if (busy)
                {
                    float pos = (animTick * 4) % (barW + 80) - 80;
                    float a = Math.Max(bX, bX + pos), b = Math.Min(bX + barW, bX + pos + 80);
                    if (b > a) Fill(g, R(a, 346, b - a, 5), Accent, 2.5f);
                }
            }

            // Patch-notes / update box
            var notes = R(40, 374, 520, 172);
            Frost(g, notes, 14);
            g.DrawString("Patch Notes", fH, B(TextC), 58 * S, 388 * S);
            g.DrawString("Fabric " + Installer.McVersion + " · Cy Client " + Updater.CurrentVersion, fSmall, B(Dim), 58 * S, 410 * S);
            g.DrawString("You're on the latest build. New modules, a faster menu and", fBody, B(Color.FromArgb(205, 212, 216, 222)), 58 * S, 436 * S);
            g.DrawString("crisp icons landed this week. More on the way.", fBody, B(Color.FromArgb(205, 212, 216, 222)), 58 * S, 456 * S);

            // Welcome panel (right)
            DrawWelcomePanel(g);

            // Footer
            g.DrawString("Cy Launcher " + Updater.CurrentVersion, fSmall, B(Dim), 22 * S, 570 * S);
            var folder = R(380, 566, 150, 22);
            Hotspot("folder", folder, delegate { OpenFolder(Paths.Game); });
            g.DrawString("Open game folder", fSmall, B(hoverId == "folder" ? TextC : Dim), 388 * S, 570 * S);
        }

        void DrawWelcomePanel(Graphics g)
        {
            var panel = R(600, 70, 340, 476);
            Frost(g, panel, 16);
            g.DrawString("Welcome,", fBody, B(Dim), 624 * S, 90 * S);
            string name = UseOffline ? (string.IsNullOrEmpty(settings.OfflineName) ? "Player" : settings.OfflineName)
                                     : (account != null ? account.Name : "Player");
            g.DrawString(name, fTitle, B(TextC), 622 * S, 108 * S);

            // Skin preview: a real 3D model you can drag to spin, falling back to a flat figure
            // until the skin has loaded.
            var skinBox = R(624, 168, 292, 282);
            Fill(g, skinBox, Color.FromArgb(70, 8, 9, 11), 12);

            // Absolute (unshifted) geometry, used by the drag hit-test and the fast repaint path.
            skinViewRect = new RectangleF(skinBox.X + contentDX, skinBox.Y, skinBox.Width, skinBox.Height);
            skinScale = skinBox.Height / 44f;                        // model is 32 units tall; leave a margin
            skinCenter = new PointF(skinViewRect.X + skinViewRect.Width / 2f, skinViewRect.Y + skinViewRect.Height / 2f);
            EnsureSkinUnderlay();

            var sk = ActiveSkin();
            if (sk != null)
            {
                float cx = skinBox.X + skinBox.Width / 2f;
                float cy = skinBox.Y + skinBox.Height / 2f;
                var clip = g.Save();
                g.SetClip(skinBox);                                 // keep the model inside its panel
                sk.Render(g, cx, cy, skinScale);
                g.Restore(clip);
            }
            else
            {
                DrawSkinPlaceholder(g, R(725, 196, 90, 220));
            }

            if (UseOffline)
            {
                var edit = R(672, 470, 196, 40);
                Hotspot("skin-editor", edit, delegate { OpenSkinEditor(); });
                Fill(g, edit, hoverId == "skin-editor" ? CardHover : Line, 10);
                Center(g, "Skin Editor", fH, TextC, edit);
            }
            else if (account == null)
            {
                var signin = R(624, 470, 138, 40);
                Hotspot("signin-chip", signin, delegate { OnSignInChip(); });
                Fill(g, signin, hoverId == "signin-chip" ? AccentHover : Accent, 10);
                Center(g, UseLauncherAccount ? "Load account" : "Sign in", fBody, OnAccent, signin);

                var ed = R(778, 470, 138, 40);
                Hotspot("skin-editor", ed, delegate { OpenSkinEditor(); });
                Fill(g, ed, hoverId == "skin-editor" ? CardHover : Line, 10);
                Center(g, "Edit skin", fBody, TextC, ed);
            }
            else
            {
                var edit = R(672, 470, 196, 40);
                Hotspot("skin-editor", edit, delegate { OpenSkinEditor(); });
                Fill(g, edit, hoverId == "skin-editor" ? CardHover : Line, 10);
                Center(g, "Skin Editor", fH, TextC, edit);
            }
        }

        /// <summary>
        /// Builds (and caches) the static picture that sits behind the 3D model: the background photo,
        /// the frosted Welcome panel, and the dark skin well. While the user spins the skin we blit
        /// this 1:1 instead of re-resampling the photo and re-blurring the panel every frame.
        /// </summary>
        void EnsureSkinUnderlay()
        {
            int w = (int)Math.Round(skinViewRect.Width), h = (int)Math.Round(skinViewRect.Height);
            if (w <= 0 || h <= 0) return;
            string sig = w + "x" + h + "@" + Math.Round(skinViewRect.X) + "," + Math.Round(skinViewRect.Y)
                       + "|" + ClientSize.Width + "x" + ClientSize.Height;
            if (sig == skinUnderlaySig && skinUnderlay != null) return;

            try
            {
                var bmp = new Bitmap(w, h, System.Drawing.Imaging.PixelFormat.Format32bppPArgb);
                using (var og = Graphics.FromImage(bmp))
                {
                    og.SmoothingMode = SmoothingMode.AntiAlias;
                    og.InterpolationMode = InterpolationMode.Bilinear;
                    og.PixelOffsetMode = PixelOffsetMode.HighQuality;
                    og.TranslateTransform(-skinViewRect.X, -skinViewRect.Y); // draw in screen coordinates

                    var full = new Rectangle(0, 0, ClientSize.Width, ClientSize.Height);
                    if (bg != null) og.DrawImage(bg, full); else og.FillRectangle(B(Bg), full);

                    // Welcome-panel frost, in screen coords (Frost draws bgBlur at screen 0,0 under the transform).
                    RectangleF panel = new RectangleF(600 * S + contentDX, 70 * S, 340 * S, 476 * S);
                    var st = og.Save();
                    using (var pp = RoundRect(panel, 16 * S)) og.SetClip(pp, CombineMode.Intersect);
                    if (bgBlur != null) og.DrawImage(bgBlur, full);
                    og.FillRectangle(B(Color.FromArgb(70, 10, 11, 13)), panel);
                    og.FillRectangle(B(Color.FromArgb(26, 255, 255, 255)), panel);
                    og.Restore(st);

                    using (var sp = RoundRect(skinViewRect, 12 * S))
                        og.FillPath(B(Color.FromArgb(70, 8, 9, 11)), sp);
                }
                if (skinUnderlay != null) skinUnderlay.Dispose();
                skinUnderlay = bmp;
                skinUnderlaySig = sig;
            }
            catch (Exception e) { Log.Write("Skin underlay: " + e.Message); }
        }

        /// <summary>A simple monochrome character silhouette standing in for the skin until the 3D view lands.</summary>
        void DrawSkinPlaceholder(Graphics g, RectangleF b)
        {
            float u = b.Width / 4f; // body is ~4 units wide
            float cx = b.X + b.Width / 2f;
            Color skin = Color.FromArgb(120, 150, 156, 164);
            Color skinD = Color.FromArgb(120, 120, 126, 134);
            Fill(g, new RectangleF(cx - u, b.Y, u * 2, u * 2), skin, 2);                       // head
            Fill(g, new RectangleF(cx - u, b.Y + u * 2.1f, u * 2, u * 3f), skinD, 2);           // torso
            Fill(g, new RectangleF(cx - u * 2f, b.Y + u * 2.1f, u * 0.9f, u * 3f), skin, 2);    // left arm
            Fill(g, new RectangleF(cx + u * 1.1f, b.Y + u * 2.1f, u * 0.9f, u * 3f), skin, 2);  // right arm
            Fill(g, new RectangleF(cx - u, b.Y + u * 5.2f, u * 0.95f, u * 3f), skinD, 2);       // left leg
            Fill(g, new RectangleF(cx + u * 0.05f, b.Y + u * 5.2f, u * 0.95f, u * 3f), skinD, 2); // right leg
        }

        // ---------------------------------------------------------------- skin editor

        void OpenSkinEditor()
        {
            if (editSkin == null)
            {
                var src = ActiveSkin();
                Bitmap b = src != null ? src.CloneBitmap() : null;
                if (b == null) { using (var d = Skin3D.Default()) b = d.CloneBitmap(); }
                SetEditSkin(b, src != null && src.Slim);
                ClearHistory(); edZoom = 0; edPanX = edPanY = 0; edPreviewZoom = 1f;
            }
            page = "skin"; UpdateControls();
        }

        void CloseSkinEditor() { page = "play"; UpdateControls(); }

        void SetEditSkin(Bitmap b, bool slim)
        {
            if (editView != null) { editView.Dispose(); editView = null; } // shares the bitmap, won't dispose it
            if (editSkin != null) editSkin.Dispose();
            editSkin = b; editSlim = slim;
            editView = new Skin3D(editSkin, editSlim, false);               // share, do not own
        }

        void DrawSkinEditor(Graphics g)
        {
            g.DrawString("Skin Editor", fH, B(TextC), 40 * S, 72 * S);
            var undo = R(616, 70, 90, 30);
            Hotspot("ed-undo", undo, delegate { Undo(); });
            Fill(g, undo, hoverId == "ed-undo" ? CardHover : Line, 8);
            Center(g, "Undo", fBody, undoStack.Count > 0 ? TextC : Dim, undo);
            var redo = R(712, 70, 90, 30);
            Hotspot("ed-redo", redo, delegate { Redo(); });
            Fill(g, redo, hoverId == "ed-redo" ? CardHover : Line, 8);
            Center(g, "Redo", fBody, redoStack.Count > 0 ? TextC : Dim, redo);

            var back = R(810, 70, 110, 30);
            Hotspot("ed-back", back, delegate { CloseSkinEditor(); });
            Fill(g, back, hoverId == "ed-back" ? CardHover : Line, 8);
            Center(g, "Back", fBody, TextC, back);

            // Toggle row: model variant, body parts, outer layer.
            var ev = editView;
            bool arms = ev != null && (ev.PartVisible[2] || ev.PartVisible[3]);
            bool legs = ev != null && (ev.PartVisible[4] || ev.PartVisible[5]);
            ToggleChip(g, "ed-slim", R(40, 104, 80, 28), editSlim ? "Slim" : "Classic", editSlim,
                delegate { if (ev != null) { ev.Slim = !ev.Slim; editSlim = ev.Slim; } Invalidate(); });
            ToggleChip(g, "ed-head", R(128, 104, 58, 28), "Head", ev != null && ev.PartVisible[0],
                delegate { if (ev != null) ev.PartVisible[0] = !ev.PartVisible[0]; Invalidate(); });
            ToggleChip(g, "ed-body", R(194, 104, 58, 28), "Body", ev != null && ev.PartVisible[1],
                delegate { if (ev != null) ev.PartVisible[1] = !ev.PartVisible[1]; Invalidate(); });
            ToggleChip(g, "ed-arms", R(260, 104, 58, 28), "Arms", arms,
                delegate { if (ev != null) { bool nv = !(ev.PartVisible[2] || ev.PartVisible[3]); ev.PartVisible[2] = nv; ev.PartVisible[3] = nv; } Invalidate(); });
            ToggleChip(g, "ed-legs", R(326, 104, 58, 28), "Legs", legs,
                delegate { if (ev != null) { bool nv = !(ev.PartVisible[4] || ev.PartVisible[5]); ev.PartVisible[4] = nv; ev.PartVisible[5] = nv; } Invalidate(); });
            ToggleChip(g, "ed-layer", R(392, 104, 64, 28), "Layer", ev != null && ev.ShowOverlay,
                delegate { if (ev != null) ev.ShowOverlay = !ev.ShowOverlay; Invalidate(); });
            ToggleChip(g, "ed-grid", R(462, 104, 58, 28), "Grid", ev != null && ev.ShowGrid,
                delegate { if (ev != null) ev.ShowGrid = !ev.ShowGrid; Invalidate(); });

            // Preview (left) — paintable, like minecraftskins.net
            var prev = R(40, 142, 300, 398);
            Fill(g, prev, Color.FromArgb(255, 16, 17, 20), 12);
            edPreviewRect = new RectangleF(prev.X + contentDX, prev.Y, prev.Width, prev.Height);
            edPreviewScale = (prev.Height / 46f) * edPreviewZoom;
            edPreviewCenter = new PointF(edPreviewRect.X + edPreviewRect.Width / 2f, edPreviewRect.Y + edPreviewRect.Height / 2f);
            if (editView != null)
            {
                var c = g.Save(); g.SetClip(prev);
                editView.Render(g, prev.X + prev.Width / 2f, prev.Y + prev.Height / 2f, edPreviewScale);
                g.Restore(c);
            }
            g.DrawString("Left: paint  ·  Right: rotate", fSmall, B(Dim), 48 * S, 516 * S);

            // Canvas (center) — the flat 64x64 skin, zoom/pan with the scroll wheel
            var canv = R(360, 142, 340, 398);
            Fill(g, canv, Color.FromArgb(255, 16, 17, 20), 12);
            edCanvasPanel = new RectangleF(canv.X + contentDX, canv.Y, canv.Width, canv.Height);
            if (edZoom <= 0) edZoom = Math.Max(2f, (float)Math.Floor(Math.Min(canv.Width, canv.Height) / 64f));
            float cw = 64 * edZoom, ch = 64 * edZoom;
            float cx0 = canv.X + (canv.Width - cw) / 2f + edPanX, cy0 = canv.Y + (canv.Height - ch) / 2f + edPanY;
            edCanvasRect = new RectangleF(cx0 + contentDX, cy0, cw, ch);
            var cclip = g.Save(); g.SetClip(canv);
            DrawChecker(g, cx0, cy0, cw, ch);
            if (editSkin != null)
            {
                var pi = g.InterpolationMode; var pp = g.PixelOffsetMode;
                g.InterpolationMode = InterpolationMode.NearestNeighbor; g.PixelOffsetMode = PixelOffsetMode.Half;
                g.DrawImage(editSkin, cx0, cy0, cw, ch);
                g.InterpolationMode = pi; g.PixelOffsetMode = pp;
            }
            g.Restore(cclip);

            // Toolbar (right)
            string[] tools = { "Pencil", "Eraser", "Pick", "Fill" };
            RectangleF[] tr = { R(720, 142, 96, 44), R(824, 142, 96, 44), R(720, 194, 96, 44), R(824, 194, 96, 44) };
            for (int i = 0; i < 4; i++)
            {
                int idx = i;
                Hotspot("ed-tool" + i, tr[i], delegate { editTool = idx; Invalidate(); });
                bool sel = editTool == i;
                Fill(g, tr[i], sel ? Accent : (hoverId == "ed-tool" + i ? CardHover : Line), 8);
                Center(g, tools[i], fBody, sel ? OnAccent : TextC, tr[i]);
            }

            var sw = R(720, 254, 200, 34);
            Fill(g, sw, editColor, 8);
            using (var pen = new Pen(Color.FromArgb(60, 255, 255, 255), 1f * S)) g.DrawRectangle(pen, sw.X, sw.Y, sw.Width, sw.Height);

            float ps = 22 * S, gap = 3 * S, px0 = 720 * S, py0 = 300 * S; int cols = 8;
            for (int i = 0; i < EditPalette.Length; i++)
            {
                int rr = i / cols, cc = i % cols; int idx = i;
                var rect = new RectangleF(px0 + cc * (ps + gap), py0 + rr * (ps + gap), ps, ps);
                Hotspot("ed-col" + i, rect, delegate { editColor = EditPalette[idx]; if (editTool > 1) editTool = 0; Invalidate(); });
                Fill(g, rect, EditPalette[i], 4);
                if (hoverId == "ed-col" + i)
                    using (var pen = new Pen(Color.FromArgb(160, 255, 255, 255), 1.4f * S)) g.DrawRectangle(pen, rect.X, rect.Y, rect.Width, rect.Height);
            }

            var up = R(720, 382, 200, 38); Hotspot("ed-up", up, delegate { UploadSkin(); });
            Fill(g, up, hoverId == "ed-up" ? CardHover : Line, 8); Center(g, "Upload PNG", fBody, TextC, up);
            var save = R(720, 428, 200, 38); Hotspot("ed-save", save, delegate { SaveSkinFile(); });
            Fill(g, save, hoverId == "ed-save" ? CardHover : Line, 8); Center(g, "Save PNG", fBody, TextC, save);
            var eq = R(720, 474, 200, 38); Hotspot("ed-equip", eq, delegate { EquipSkin(); });
            Fill(g, eq, hoverId == "ed-equip" ? AccentHover : Accent, 8); Center(g, "Equip", fBody, OnAccent, eq);
        }

        void DrawChecker(Graphics g, float x, float y, float w, float h)
        {
            int cell = 8;
            var clip = g.Save(); g.SetClip(new RectangleF(x, y, w, h));
            using (var b1 = new SolidBrush(Color.FromArgb(255, 38, 40, 44)))
            using (var b2 = new SolidBrush(Color.FromArgb(255, 30, 32, 36)))
            {
                int rows = (int)Math.Ceiling(h / cell), colz = (int)Math.Ceiling(w / cell);
                for (int r = 0; r < rows; r++)
                    for (int c = 0; c < colz; c++)
                        g.FillRectangle(((r + c) & 1) == 0 ? b1 : b2, x + c * cell, y + r * cell, cell, cell);
            }
            g.Restore(clip);
        }

        Point CanvasPixel(Point m)
        {
            float pw = edCanvasRect.Width / 64f, ph = edCanvasRect.Height / 64f;
            int px = (int)Math.Floor((m.X - edCanvasRect.X) / pw);
            int py = (int)Math.Floor((m.Y - edCanvasRect.Y) / ph);
            if (px < 0) px = 0; if (py < 0) py = 0; if (px > 63) px = 63; if (py > 63) py = 63;
            return new Point(px, py);
        }

        void PaintAt(Point m)
        {
            if (editSkin == null) return;
            Point p = CanvasPixel(m);
            switch (editTool)
            {
                case 0: PlotLine(editPaintLast, p, editColor); break;
                case 1: PlotLine(editPaintLast, p, Color.FromArgb(0, 0, 0, 0)); break;
                case 2: editColor = editSkin.GetPixel(p.X, p.Y); editTool = 0; break;
                case 3: FloodFill(p.X, p.Y, editColor); break;
            }
            editPaintLast = p;
            Invalidate();
        }

        /// <summary>Paints the texel under the cursor on the 3D preview (single pixel; no stroke join).</summary>
        void Paint3DAt(Point m)
        {
            if (editSkin == null || editView == null) return;
            int tx, ty;
            if (!editView.Pick(m.X, m.Y, edPreviewCenter.X, edPreviewCenter.Y, edPreviewScale, out tx, out ty)) return;
            if (tx < 0 || ty < 0 || tx > 63 || ty > 63) return;
            switch (editTool)
            {
                case 0: editSkin.SetPixel(tx, ty, editColor); break;
                case 1: editSkin.SetPixel(tx, ty, Color.FromArgb(0, 0, 0, 0)); break;
                case 2: editColor = editSkin.GetPixel(tx, ty); editTool = 0; break;
                case 3: FloodFill(tx, ty, editColor); break;
            }
            Invalidate();
        }

        void ToggleChip(Graphics g, string id, RectangleF r, string label, bool on, Action click)
        {
            Hotspot(id, r, click);
            Fill(g, r, on ? Accent : (hoverId == id ? CardHover : Line), 8);
            Center(g, label, fSmall, on ? OnAccent : TextC, r);
        }

        /// <summary>Scroll over the canvas to zoom toward the cursor; over the preview to scale the model.</summary>
        void WheelZoomEditor(MouseEventArgs e)
        {
            if (edCanvasPanel.Contains(e.Location))
            {
                float z0 = edCanvasRect.Width / 64f;
                float tx = (e.Location.X - edCanvasRect.X) / z0;
                float ty = (e.Location.Y - edCanvasRect.Y) / z0;
                float mag = z0 < 8 ? 1f : (z0 < 16 ? 2f : 4f);
                float z1 = z0 + (e.Delta > 0 ? mag : -mag);
                if (z1 < 2f) z1 = 2f; if (z1 > 40f) z1 = 40f;
                if (z1 == z0) return;
                float img1 = 64 * z1;
                edPanX = (e.Location.X - tx * z1) - edCanvasPanel.X - (edCanvasPanel.Width - img1) / 2f;
                edPanY = (e.Location.Y - ty * z1) - edCanvasPanel.Y - (edCanvasPanel.Height - img1) / 2f;
                edZoom = z1;
                Invalidate();
            }
            else if (editView != null && edPreviewRect.Contains(e.Location))
            {
                edPreviewZoom *= e.Delta > 0 ? 1.1f : 1f / 1.1f;
                if (edPreviewZoom < 0.5f) edPreviewZoom = 0.5f;
                if (edPreviewZoom > 3f) edPreviewZoom = 3f;
                Invalidate();
            }
        }

        protected override bool ProcessCmdKey(ref Message msg, Keys keyData)
        {
            if (page == "skin" && !overlay)
            {
                if (keyData == (Keys.Control | Keys.Z)) { Undo(); return true; }
                if (keyData == (Keys.Control | Keys.Y) || keyData == (Keys.Control | Keys.Shift | Keys.Z)) { Redo(); return true; }
            }
            return base.ProcessCmdKey(ref msg, keyData);
        }

        // ---- undo / redo (one snapshot per stroke, fill or upload) ----

        void ClearHistory()
        {
            foreach (var b in undoStack) b.Dispose();
            foreach (var b in redoStack) b.Dispose();
            undoStack.Clear(); redoStack.Clear();
        }

        /// <summary>Snapshots the current skin before an edit, so it can be undone. Clears the redo trail.</summary>
        void PushUndo()
        {
            if (editSkin == null) return;
            undoStack.Add(new Bitmap(editSkin));
            while (undoStack.Count > UndoCap) { undoStack[0].Dispose(); undoStack.RemoveAt(0); }
            foreach (var b in redoStack) b.Dispose();
            redoStack.Clear();
        }

        /// <summary>Copies src onto the existing editSkin in place, so editView keeps its reference.</summary>
        void RestoreInto(Bitmap src)
        {
            if (editSkin == null || src == null) return;
            using (var g = Graphics.FromImage(editSkin))
            {
                g.CompositingMode = CompositingMode.SourceCopy;
                g.DrawImageUnscaled(src, 0, 0);
            }
        }

        void Undo()
        {
            if (undoStack.Count == 0 || editSkin == null) return;
            redoStack.Add(new Bitmap(editSkin));
            Bitmap prev = undoStack[undoStack.Count - 1];
            undoStack.RemoveAt(undoStack.Count - 1);
            RestoreInto(prev); prev.Dispose();
            Invalidate();
        }

        void Redo()
        {
            if (redoStack.Count == 0 || editSkin == null) return;
            undoStack.Add(new Bitmap(editSkin));
            Bitmap next = redoStack[redoStack.Count - 1];
            redoStack.RemoveAt(redoStack.Count - 1);
            RestoreInto(next); next.Dispose();
            Invalidate();
        }

        void PlotLine(Point a, Point b, Color c)
        {
            int x0 = a.X, y0 = a.Y, x1 = b.X, y1 = b.Y;
            int dx = Math.Abs(x1 - x0), dy = -Math.Abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true)
            {
                if (x0 >= 0 && y0 >= 0 && x0 < 64 && y0 < 64) editSkin.SetPixel(x0, y0, c);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; x0 += sx; }
                if (e2 <= dx) { err += dx; y0 += sy; }
            }
        }

        void FloodFill(int sx, int sy, Color repl)
        {
            Color target = editSkin.GetPixel(sx, sy);
            if (target.ToArgb() == repl.ToArgb()) return;
            var stack = new Stack<Point>();
            stack.Push(new Point(sx, sy));
            while (stack.Count > 0)
            {
                var p = stack.Pop();
                if (p.X < 0 || p.Y < 0 || p.X > 63 || p.Y > 63) continue;
                if (editSkin.GetPixel(p.X, p.Y).ToArgb() != target.ToArgb()) continue;
                editSkin.SetPixel(p.X, p.Y, repl);
                stack.Push(new Point(p.X + 1, p.Y)); stack.Push(new Point(p.X - 1, p.Y));
                stack.Push(new Point(p.X, p.Y + 1)); stack.Push(new Point(p.X, p.Y - 1));
            }
        }

        void UploadSkin()
        {
            using (var dlg = new OpenFileDialog())
            {
                dlg.Title = "Choose a skin PNG (64x64)";
                dlg.Filter = "PNG image|*.png";
                if (dlg.ShowDialog(this) != DialogResult.OK) return;
                try
                {
                    using (var loaded = new Bitmap(dlg.FileName))
                    {
                        var b = new Bitmap(64, 64, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
                        using (var gg = Graphics.FromImage(b))
                        {
                            gg.InterpolationMode = InterpolationMode.NearestNeighbor;
                            gg.PixelOffsetMode = PixelOffsetMode.Half;
                            if (loaded.Width == 64 && loaded.Height == 64) gg.DrawImageUnscaled(loaded, 0, 0);
                            else if (loaded.Width == 64 && loaded.Height == 32)
                                gg.DrawImage(loaded, new Rectangle(0, 0, 64, 32), new Rectangle(0, 0, 64, 32), GraphicsUnit.Pixel);
                            else
                                gg.DrawImage(loaded, new Rectangle(0, 0, 64, 64), new Rectangle(0, 0, loaded.Width, loaded.Height), GraphicsUnit.Pixel);
                        }
                        PushUndo();
                        SetEditSkin(b, editSlim);
                    }
                    Invalidate();
                }
                catch (Exception ex) { ShowMessage("Couldn't load skin", ex.Message, null); }
            }
        }

        void SaveSkinFile()
        {
            if (editSkin == null) return;
            using (var dlg = new SaveFileDialog())
            {
                dlg.Title = "Save skin PNG";
                dlg.Filter = "PNG image|*.png";
                dlg.FileName = "cy-skin.png";
                if (dlg.ShowDialog(this) != DialogResult.OK) return;
                try { editSkin.Save(dlg.FileName, System.Drawing.Imaging.ImageFormat.Png); ShowMessage("Saved", "Your skin was saved.", "OK"); }
                catch (Exception ex) { ShowMessage("Couldn't save", ex.Message, null); }
            }
        }

        string OfflineSkinPath { get { return Path.Combine(Paths.Root, "offline-skin.png"); } }

        /// <summary>Loads the saved offline skin into the preview, if one exists.</summary>
        void LoadOfflineSkin()
        {
            try
            {
                if (!File.Exists(OfflineSkinPath)) return;
                using (var loaded = new Bitmap(OfflineSkinPath))
                {
                    var full = new Bitmap(64, 64, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
                    using (var gg = Graphics.FromImage(full))
                    {
                        gg.InterpolationMode = InterpolationMode.NearestNeighbor;
                        gg.PixelOffsetMode = PixelOffsetMode.Half;
                        gg.DrawImage(loaded, 0, 0, loaded.Width, loaded.Height);
                    }
                    var old = skin3d; skin3d = new Skin3D(full, false); if (old != null) old.Dispose();
                }
                Invalidate();
            }
            catch (Exception e) { Log.Write("Offline skin: " + e.Message); }
        }

        void EquipSkin()
        {
            if (UseOffline)
            {
                if (editSkin == null) return;
                try
                {
                    Directory.CreateDirectory(Paths.Root);
                    editSkin.Save(OfflineSkinPath, System.Drawing.Imaging.ImageFormat.Png);
                    var old = skin3d; skin3d = new Skin3D((Bitmap)editSkin.Clone(), editSlim); if (old != null) old.Dispose();
                    ShowMessage("Offline skin set", "Saved as your offline skin. It shows in the launcher now; in-game offline skins need the client-side loader (coming next).", "OK");
                }
                catch (Exception ex) { ShowMessage("Couldn't save skin", ex.Message, null); }
                return;
            }
            if (account == null || string.IsNullOrEmpty(account.McToken))
            {
                ShowMessage("Sign in needed", "Sign in to your Minecraft account first, then you can equip a skin.", "OK");
                return;
            }
            ShowOverlay("Equip skin",
                "Upload this skin to your Minecraft account as your " + (editSlim ? "slim" : "classic") + " skin?",
                null,
                "Equip", delegate { DoEquip(); },
                "Cancel", delegate { HideOverlay(); });
        }

        async void DoEquip()
        {
            HideOverlay();
            if (editSkin == null) return;
            try
            {
                byte[] png;
                using (var ms = new MemoryStream()) { editSkin.Save(ms, System.Drawing.Imaging.ImageFormat.Png); png = ms.ToArray(); }
                var res = await Net.PostSkin("https://api.minecraftservices.com/minecraft/profile/skins",
                                             account.McToken, png, editSlim ? "slim" : "classic");
                if (res.Ok)
                {
                    ShowMessage("Equipped", "Your skin is now set on your account.", "OK");
                    account.SkinUrl = null; LoadAvatarAsync(); // refresh the preview from the account
                }
                else ShowMessage("Couldn't equip", "Minecraft returned status " + res.Status + ".", "OK");
            }
            catch (Exception ex) { ShowMessage("Couldn't equip", ex.Message, null); }
        }

        void DrawServers(Graphics g)
        {
            g.DrawString("Server Finder", fH, B(TextC), 40 * S, 80 * S);
            var box = R(40, 120, W - 80, 400);
            Frost(g, box, 14);
            CenterShadow(g, "Live server browser coming in the next update.", fBody, Color.FromArgb(220, 225, 227, 231), R(40, 300, W - 80, 20));
        }

        void ToggleMaximize()
        {
            Rectangle wa = Screen.FromControl(this).WorkingArea;
            if (maximized)
            {
                maximized = false;
                ClientSize = normalSize;
                Location = new Point(wa.X + (wa.Width - Width) / 2, wa.Y + (wa.Height - Height) / 2);
            }
            else
            {
                normalSize = ClientSize;
                maximized = true;
                int w = (int)(wa.Width * 0.92f);
                int h = (int)Math.Round(w * (double)CANVAS_H / CANVAS_W);
                if (h > wa.Height - 40) { h = wa.Height - 40; w = (int)Math.Round(h * (double)CANVAS_W / CANVAS_H); }
                ClientSize = new Size(w, h);
                Location = new Point(wa.X + (wa.Width - Width) / 2, wa.Y + (wa.Height - Height) / 2);
            }
        }

        void DrawMods(Graphics g)
        {
            // "Modrinth" label, search field (positioned in UpdateControls), and button.
            g.DrawString("Modrinth", fH, B(TextC), 40 * S, 80 * S);
            Fill(g, R(150, 70, 646, 34), Field, 8);
            var sbtn = R(806, 70, 114, 34);
            Hotspot("search", sbtn, delegate { RunSearch(); });
            Fill(g, sbtn, hoverId == "search" ? AccentHover : Accent, 8);
            Center(g, modsBusy ? "..." : "Search", fBody, OnAccent, sbtn);

            const float listTop = 120, listBottom = 560;

            if (modResults.Count == 0)
            {
                string msg = modsBusy ? "Searching..."
                    : (!string.IsNullOrEmpty(modsMsg) ? modsMsg : "Search for a Fabric mod to add it to Cy Client.");
                CenterShadow(g, msg, fBody, TextC, R(0, 300, W, 24));
                DrawModsFooter(g);
                return;
            }

            const float cardH = 92, gap = 12;
            modScrollMax = Math.Max(0f, modResults.Count * (cardH + gap) - (listBottom - listTop));
            if (modScroll > modScrollMax) modScroll = modScrollMax;
            if (modScroll < 0) modScroll = 0;

            var state = g.Save();
            g.SetClip(R(34, listTop - 2, 892, listBottom - listTop + 4));
            for (int i = 0; i < modResults.Count; i++)
            {
                float topU = listTop - modScroll + i * (cardH + gap);
                if (topU + cardH < listTop || topU > listBottom) continue;
                DrawModCard(g, modResults[i], topU, cardH, listTop, listBottom);
            }
            g.Restore(state);

            // Scrollbar thumb
            if (modScrollMax > 0)
            {
                float trackH = listBottom - listTop;
                float thumbH = Math.Max(30f, trackH * trackH / (trackH + modScrollMax));
                float ty = listTop + (trackH - thumbH) * (modScroll / modScrollMax);
                Fill(g, R(928, ty, 4, thumbH), Line, 2);
            }

            if (!string.IsNullOrEmpty(modsMsg)) g.DrawString(Truncate(g, modsMsg, fSmall, 560), fSmall, B(Dim), 230 * S, 570 * S);
            DrawModsFooter(g);
        }

        void DrawModCard(Graphics g, ModHit h, float topU, float cardH, float listTop, float listBottom)
        {
            const float x = 40, w = 880;
            Fill(g, R(x, topU, w, cardH), Color.FromArgb(150, 20, 21, 24), 12);
            using (var pen = new Pen(Color.FromArgb(30, 255, 255, 255), 1f * S))
            using (var path = RoundRect(R(x, topU, w, cardH), 12 * S))
                g.DrawPath(pen, path);

            // Icon
            var iconR = R(x + 16, topU + 18, 56, 56);
            if (h.Icon != null)
            {
                var st = g.Save();
                using (var clip = RoundRect(iconR, 10 * S)) g.SetClip(clip);
                g.DrawImage(h.Icon, iconR);
                g.Restore(st);
            }
            else
            {
                Fill(g, iconR, Line, 10);
                Center(g, "?", fH, Dim, iconR);
                EnsureIcon(h);
            }

            // Text
            float tx = x + 86;
            g.DrawString(Truncate(g, h.Title, fH, 540), fH, B(TextC), tx * S, (topU + 15) * S);
            string meta = (string.IsNullOrEmpty(h.Author) ? "" : h.Author + "    ·    ") + FormatCount(h.Downloads) + " downloads";
            g.DrawString(meta, fSmall, B(Dim), tx * S, (topU + 39) * S);
            string desc = h.Description == null ? "" : h.Description.Replace('\n', ' ').Replace('\r', ' ');
            g.DrawString(Truncate(g, desc, fSmall, 580), fSmall, B(Color.FromArgb(205, 210, 214, 220)), tx * S, (topU + 60) * S);

            // Action button
            var btn = R(x + w - 150, topU + 28, 130, 36);
            bool installed = IsInstalled(h);
            bool installing = modBusyId == h.ProjectId;
            bool visible = topU + 28 >= listTop - 2 && topU + 64 <= listBottom + 2;
            string id = "mod-" + h.Provider + "-" + h.ProjectId;
            if (installing)
            {
                Fill(g, btn, Line, 9);
                Center(g, "Installing...", fSmall, Dim, btn);
            }
            else if (installed)
            {
                if (visible) Hotspot(id, btn, delegate { RemoveMod(h); });
                Fill(g, btn, hoverId == id ? CardHover : Color.FromArgb(175, Danger), 9);
                Center(g, hoverId == id ? "Remove" : "Installed", fBody, TextC, btn);
            }
            else
            {
                if (visible && modBusyId == null) Hotspot(id, btn, delegate { InstallMod(h); });
                Fill(g, btn, hoverId == id ? AccentHover : Accent, 9);
                Center(g, "Install", fBody, OnAccent, btn);
            }
        }

        void DrawModsFooter(Graphics g)
        {
            var folder = R(40, 568, 150, 20);
            Hotspot("modsfolder", folder, delegate { OpenFolder(Mods.ModsDir); });
            g.DrawString("Open mods folder", fSmall, B(hoverId == "modsfolder" ? TextC : Dim), 40 * S, 570 * S);
            g.DrawString("For Minecraft " + Installer.McVersion + " · Fabric", fSmall, B(Dim), (W - 210) * S, 570 * S);
        }

        string Truncate(Graphics g, string s, Font f, float maxWidthUnits)
        {
            if (string.IsNullOrEmpty(s)) return "";
            float max = maxWidthUnits * S;
            if (g.MeasureString(s, f).Width <= max) return s;
            for (int len = s.Length - 1; len > 1; len--)
            {
                string t = s.Substring(0, len).TrimEnd() + "...";
                if (g.MeasureString(t, f).Width <= max) return t;
            }
            return "...";
        }

        static string FormatCount(long n)
        {
            if (n >= 1000000) return (n / 1000000.0).ToString("0.#") + "M";
            if (n >= 1000) return (n / 1000.0).ToString("0.#") + "K";
            return n.ToString();
        }

        void DrawSettings(Graphics g)
        {
            // Memory
            var mem = R(40, 72, 430, 120);
            Frost(g, mem, 14);
            g.DrawString("Memory", fH, B(TextC), 60 * S, 88 * S);
            string memLabel = (settings.MemoryMb / 1024.0).ToString("0.0") + " GB";
            float mw = g.MeasureString(memLabel, fH).Width / S;
            g.DrawString(memLabel, fH, B(TextC), (450 - mw) * S, 88 * S);
            sliderTrack = R(62, 130, 386, 6);
            Hotspot("slider", R(56, 118, 398, 30), null);
            float frac = (settings.MemoryMb - 2048f) / Math.Max(1, maxMemoryMb - 2048f);
            frac = Math.Max(0, Math.Min(1, frac));
            Fill(g, sliderTrack, Line, 3);
            Fill(g, new RectangleF(sliderTrack.X, sliderTrack.Y, sliderTrack.Width * frac, sliderTrack.Height), Accent, 3);
            float kx = sliderTrack.X + sliderTrack.Width * frac;
            Fill(g, new RectangleF(kx - 8 * S, sliderTrack.Y - 5 * S, 16 * S, 16 * S), TextC, 8);
            g.DrawString("4 GB is plenty for PvP. Your PC has " + (TotalRamMb() / 1024.0).ToString("0") + " GB.", fSmall, B(Dim), 60 * S, 158 * S);

            // When the game starts
            var after = R(490, 72, 430, 120);
            Frost(g, after, 14);
            g.DrawString("When the game starts", fH, B(TextC), 510 * S, 88 * S);
            Segmented(g, R(510, 120, 390, 34), new[] { "Close launcher", "Keep it open" }, settings.CloseOnLaunch ? 0 : 1, "after",
                delegate(int i) { settings.CloseOnLaunch = i == 0; settings.Save(); });
            g.DrawString("Closing frees all launcher memory while you play.", fSmall, B(Dim), 510 * S, 164 * S);

            // Account
            var acc = R(40, 208, 880, 150);
            Frost(g, acc, 14);
            g.DrawString("Account", fH, B(TextC), 60 * S, 224 * S);
            int accSel = UseOffline ? 2 : (UseLauncherAccount ? 1 : 0);
            Segmented(g, R(60, 250, 540, 34), new[] { "Microsoft", "Minecraft Launcher", "Offline" }, accSel, "method",
                delegate(int i) { settings.SignInMethod = i == 0 ? "msa" : (i == 1 ? "launcher" : "offline"); settings.Save(); if (account == null && i == 1) TryAutoLoadLauncherAccount(); if (i == 2) LoadOfflineSkin(); UpdateControls(); });

            if (UseOffline)
            {
                g.DrawString("Play offline with any username. Works for singleplayer and offline (LAN/cracked) servers,", fBody, B(TextC), 60 * S, 292 * S);
                g.DrawString("not servers set to online-mode. Set your name and skin below.", fSmall, B(Dim), 60 * S, 316 * S);
                g.DrawString("Username", fSmall, B(Dim), 60 * S, 330 * S);
                Fill(g, R(60, 328, 300, 24), Field, 6);
                var ed = R(380, 328, 160, 24);
                Hotspot("offline-edit", ed, delegate { OpenSkinEditor(); });
                Fill(g, ed, hoverId == "offline-edit" ? CardHover : Line, 6);
                Center(g, "Edit skin", fSmall, TextC, ed);
            }
            else if (!UseLauncherAccount)
            {
                string line = account != null
                    ? "Signed in as " + account.Name + "."
                    : "Press Launch to sign in, then enter the code shown at microsoft.com/link.";
                g.DrawString(line, fBody, B(TextC), 60 * S, 290 * S);
                g.DrawString("Azure Application (client) ID  -  needs Mojang approval (see SETUP-LOGIN.txt)", fSmall, B(Dim), 60 * S, 314 * S);
                Fill(g, R(60, 330, 740, 22), Field, 6);
                var guide = R(812, 330, 106, 22);
                Hotspot("guide", guide, delegate { OpenSetupGuide(); });
                Fill(g, guide, hoverId == "guide" ? CardHover : Line, 6);
                Center(g, "Setup guide", fSmall, TextC, guide);
            }
            else
            {
                string line = account != null
                    ? "Using " + account.Name + " from the Minecraft Launcher."
                    : "Reuses your Java sign-in from the official Minecraft Launcher, if it's available.";
                g.DrawString(line, fBody, B(TextC), 60 * S, 296 * S);
                g.DrawString("May not work on Microsoft Store installs. If it fails, switch to Microsoft.", fSmall, B(Dim), 60 * S, 322 * S);
            }

            // Java
            var java = R(40, 374, 880, 96);
            Frost(g, java, 14);
            g.DrawString("Java", fH, B(TextC), 60 * S, 390 * S);
            Segmented(g, R(60, 418, 300, 34), new[] { "Bundled", "Custom" }, settings.UseCustomJava ? 1 : 0, "java",
                delegate(int i) { settings.UseCustomJava = i == 1; settings.Save(); UpdateControls(); });
            if (settings.UseCustomJava)
            {
                Fill(g, R(380, 418, 400, 34), Field, 8);
                var browse = R(790, 418, 110, 34);
                Hotspot("browse", browse, delegate { BrowseJava(); });
                Fill(g, browse, hoverId == "browse" ? CardHover : Line, 8);
                Center(g, "Browse", fBody, TextC, browse);
            }
            else g.DrawString("Uses Mojang's official Java 25, downloaded automatically.", fSmall, B(Dim), 380 * S, 426 * S);

            // Actions
            float x = 40;
            x = PillButton(g, x, 490, "Open game folder", "open", Line, delegate { OpenFolder(Paths.Game); });
            x = PillButton(g, x, 490, "Repair game files", "repair", Line, delegate
            {
                repairNext = true;
                ShowMessage("Repair scheduled", "Every game file will be checked and re-downloaded if needed the next time you press Launch.", null);
            });
            if (account != null)
                PillButton(g, x, 490, "Sign out", "signout", Danger, delegate
                {
                    Account.Delete(); account = null; avatar = null;
                    if (UseLauncherAccount) TryAutoLoadLauncherAccount();
                    Invalidate();
                });

            g.DrawString("Everything is stored in %APPDATA%\\.cyclient", fSmall, B(Dim), 40 * S, 544 * S);
        }

        float PillButton(Graphics g, float x, float y, string label, string id, Color color, Action click)
        {
            float w = g.MeasureString(label, fBody).Width / S + 32;
            var r = R(x, y, w, 36);
            Hotspot(id, r, click);
            Color baseC = Color.FromArgb(175, color);
            Fill(g, r, hoverId == id ? Color.FromArgb(210, Lighten(color, 22)) : baseC, 10);
            Center(g, label, fBody, TextC, r);
            return x + w + 10;
        }

        void Segmented(Graphics g, RectangleF r, string[] options, int selected, string id, Action<int> pick)
        {
            Fill(g, r, Field, 9);
            float w = r.Width / options.Length;
            for (int i = 0; i < options.Length; i++)
            {
                int index = i;
                var seg = new RectangleF(r.X + w * i + 3 * S, r.Y + 3 * S, w - 6 * S, r.Height - 6 * S);
                string hid = id + i;
                Hotspot(hid, seg, delegate { pick(index); Invalidate(); });
                if (i == selected) Fill(g, seg, Accent, 7);
                else if (hoverId == hid) Fill(g, seg, CardHover, 7);
                Center(g, options[i], fBody, i == selected ? OnAccent : TextC, seg);
            }
        }

        void DrawOverlay(Graphics g)
        {
            hots.Clear(); // only the overlay is clickable
            g.FillRectangle(B(Color.FromArgb(175, 6, 7, 9)), R(0, 0, CANVAS_W, CANVAS_H));
            float h = ovCode != null ? 300 : 210;
            float cardX = (CANVAS_W - 460) / 2f;
            var card = R(cardX, (CANVAS_H - h) / 2, 460, h);
            Frost(g, card, 16, 30, 120);
            float x = cardX + 30, y = (CANVAS_H - h) / 2 + 26;
            g.DrawString(ovTitle ?? "", fH, B(TextC), x * S, y * S);
            y += 32;
            g.DrawString(ovText ?? "", fBody, B(Dim), new RectangleF(x * S, y * S, 400 * S, 80 * S));
            y += ovCode != null ? 46 : 80;

            if (ovCode != null)
            {
                var codeBox = R(x, y, 400, 64);
                Fill(g, codeBox, Field, 12);
                Center(g, ovCode, fCode, TextC, codeBox);
                y += 76;
                if (!string.IsNullOrEmpty(ovStatus)) g.DrawString(ovStatus, fSmall, B(Dim), x * S, y * S);
                y += 24;
            }

            float bx = x;
            if (ovPrimary != null)
            {
                float w = g.MeasureString(ovPrimary, fBody).Width / S + 36;
                var r = R(bx, y, w, 38);
                Hotspot("ov1", r, ovPrimaryAction);
                Fill(g, r, hoverId == "ov1" ? AccentHover : Accent, 10);
                Center(g, ovPrimary, fBody, OnAccent, r);
                bx += w + 10;
            }
            if (ovSecondary != null)
            {
                float w = g.MeasureString(ovSecondary, fBody).Width / S + 36;
                var r = R(bx, y, w, 38);
                Hotspot("ov2", r, ovSecondaryAction);
                Fill(g, r, hoverId == "ov2" ? CardHover : Line, 10);
                Center(g, ovSecondary, fBody, TextC, r);
            }
        }

        // ---------------------------------------------------------------- drawing helpers

        static SolidBrush B(Color c) { return BrushCache.Get(c); }

        /// <summary>Frosted-glass panel: the blurred background shows through, tinted and bordered.</summary>
        void Frost(Graphics g, RectangleF r, float radius) { Frost(g, r, radius, 26, 70); }

        void Frost(Graphics g, RectangleF r, float radius, int sheen, int darken)
        {
            var state = g.Save();
            GraphicsPath path = (radius > 0.5f) ? RoundRect(r, radius * S) : null;
            // Intersect (not replace) so a partial repaint only rasterizes the blurred background
            // inside the invalid region. For a full repaint the clip is the whole window, so this is
            // identical to before, but while dragging the skin it keeps the blur off the whole panel.
            if (path != null) g.SetClip(path, CombineMode.Intersect); else g.SetClip(r, CombineMode.Intersect);
            if (bgBlur != null) g.DrawImage(bgBlur, new Rectangle((int)-contentDX, 0, ClientSize.Width, ClientSize.Height));
            else g.FillRectangle(B(Card), r);
            g.FillRectangle(B(Color.FromArgb(darken, 10, 11, 13)), r);   // deepen for text contrast
            g.FillRectangle(B(Color.FromArgb(sheen, 255, 255, 255)), r); // glass sheen
            g.Restore(state);
            using (var pen = new Pen(Color.FromArgb(46, 255, 255, 255), 1f * S))
            {
                if (path != null) g.DrawPath(pen, path);
                else g.DrawLine(pen, r.X, r.Bottom - 0.5f, r.Right, r.Bottom - 0.5f);
            }
            if (path != null) path.Dispose();
        }

        /// <summary>Centered text with a soft shadow, for text drawn directly over the background image.</summary>
        void CenterShadow(Graphics g, string text, Font f, Color c, RectangleF r)
        {
            if (string.IsNullOrEmpty(text)) return;
            var sh = new RectangleF(r.X + 1f * S, r.Y + 1f * S, r.Width, r.Height);
            Center(g, text, f, Color.FromArgb(150, 0, 0, 0), sh);
            Center(g, text, f, c, r);
        }

        void Fill(Graphics g, RectangleF r, Color c, float radius)
        {
            float rad = radius * S;
            if (rad <= 0.5f) { g.FillRectangle(B(c), r); return; }
            using (var path = RoundRect(r, rad)) g.FillPath(B(c), path);
        }

        static GraphicsPath RoundRect(RectangleF r, float rad)
        {
            rad = Math.Min(rad, Math.Min(r.Width, r.Height) / 2);
            float d = rad * 2;
            var p = new GraphicsPath();
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        static void Center(Graphics g, string text, Font f, Color c, RectangleF r)
        {
            if (string.IsNullOrEmpty(text)) return;
            using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
                g.DrawString(text, f, B(c), r, sf);
        }

        static Color Lighten(Color c, int amount)
        {
            return Color.FromArgb(c.A, Math.Min(255, c.R + amount), Math.Min(255, c.G + amount), Math.Min(255, c.B + amount));
        }

        void Hotspot(string id, RectangleF r, Action click)
        {
            // hots are tested against absolute mouse coords, so bake in the current content offset.
            var h = new Hot(); h.Id = id; h.R = new RectangleF(r.X + contentDX, r.Y, r.Width, r.Height); h.Click = click;
            hots.Add(h);
        }

        // ---------------------------------------------------------------- input

        Hot HotAt(Point p)
        {
            for (int i = hots.Count - 1; i >= 0; i--) if (hots[i].R.Contains(p)) return hots[i];
            return null;
        }

        bool OverGrip(Point p) { return !maximized && p.X >= ClientSize.Width - D(18) && p.Y >= ClientSize.Height - D(18); }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            if (resizing)
            {
                int w = Math.Max(MIN_CLIENT_W, e.X);
                int hh = (int)Math.Round(w * (double)CANVAS_H / CANVAS_W);
                ClientSize = new Size(w, hh);
                return;
            }
            if (draggingSlider) { SetMemoryFromMouse(e.X); return; }
            if (draggingSkin)
            {
                var sk = ActiveSkin();
                if (sk != null)
                {
                    sk.Yaw += (e.X - skinDragLast.X) * 0.6f;
                    sk.Pitch += (e.Y - skinDragLast.Y) * 0.6f;
                    if (sk.Pitch > 89f) sk.Pitch = 89f;
                    if (sk.Pitch < -89f) sk.Pitch = -89f;
                    skinDragLast = e.Location;
                    // Only repaint the skin box, not the whole blurred window: this is what keeps the spin fast.
                    Rectangle r = Rectangle.Round(skinViewRect);
                    r.Inflate(2, 2);
                    Invalidate(r);
                }
                return;
            }
            if (editRotating && editView != null)
            {
                editView.Yaw += (e.X - editDragLast.X) * 0.6f;
                editView.Pitch += (e.Y - editDragLast.Y) * 0.6f;
                if (editView.Pitch > 89f) editView.Pitch = 89f;
                if (editView.Pitch < -89f) editView.Pitch = -89f;
                editDragLast = e.Location;
                Rectangle r = Rectangle.Round(edPreviewRect); r.Inflate(2, 2); Invalidate(r);
                return;
            }
            if (editPainting3d) { Paint3DAt(e.Location); return; }
            if (editPainting) { PaintAt(e.Location); return; }
            if (OverGrip(e.Location)) { Cursor = Cursors.SizeNWSE; if (hoverId != null) { hoverId = null; Invalidate(); } return; }
            var h = HotAt(e.Location);
            string id = h == null ? null : h.Id;
            if (id != hoverId) { hoverId = id; Invalidate(); }
            if (h != null) Cursor = Cursors.Hand;
            else if (page == "play" && ActiveSkin() != null && skinViewRect.Contains(e.Location)) Cursor = Cursors.SizeAll;
            else if (page == "skin" && edCanvasRect.Contains(e.Location)) Cursor = Cursors.Cross;
            else if (page == "skin" && editView != null && edPreviewRect.Contains(e.Location)) Cursor = Cursors.Cross;
            else Cursor = Cursors.Default;
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            if (hoverId != null) { hoverId = null; Invalidate(); }
        }

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            base.OnMouseWheel(e);
            if (overlay) return;
            if (page == "skin") { WheelZoomEditor(e); return; }
            if (page != "mods" || modResults.Count == 0) return;
            modScroll -= e.Delta * 0.5f;
            if (modScroll < 0) modScroll = 0;
            if (modScroll > modScrollMax) modScroll = modScrollMax;
            Invalidate();
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button == MouseButtons.Right)
            {
                // Right-drag rotates the editor preview (left paints it).
                if (!overlay && page == "skin" && editView != null && edPreviewRect.Contains(e.Location))
                { editRotating = true; editDragLast = e.Location; }
                return;
            }
            if (e.Button != MouseButtons.Left) return;
            if (!overlay && OverGrip(e.Location)) { resizing = true; return; }
            var h = HotAt(e.Location);
            if (h == null)
            {
                if (!overlay && page == "play" && ActiveSkin() != null && skinViewRect.Contains(e.Location))
                {
                    draggingSkin = true; skinDragLast = e.Location; return; // spin the skin
                }
                if (!overlay && page == "skin")
                {
                    if (editSkin != null && edPreviewRect.Contains(e.Location))
                    { if (editTool != 2) PushUndo(); editPainting3d = true; Paint3DAt(e.Location); return; }
                    if (editSkin != null && edCanvasRect.Contains(e.Location) && edCanvasPanel.Contains(e.Location))
                    { if (editTool != 2) PushUndo(); editPainting = true; editPaintLast = CanvasPixel(e.Location); PaintAt(e.Location); return; }
                }
                if (e.Y < D(TitleH) && !overlay) { ReleaseCapture(); SendMessage(Handle, 0xA1, (IntPtr)2, IntPtr.Zero); } // drag window
                return;
            }
            if (h.Id == "slider") { draggingSlider = true; SetMemoryFromMouse(e.X); return; }
            pressId = h.Id;
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            if (draggingSkin) { draggingSkin = false; Invalidate(); return; }
            if (editPainting3d) { editPainting3d = false; return; }
            if (editPainting) { editPainting = false; return; }
            if (editRotating) { editRotating = false; Invalidate(); return; }
            if (resizing) { resizing = false; normalSize = ClientSize; return; }
            if (draggingSlider) { draggingSlider = false; settings.Save(); return; }
            var h = HotAt(e.Location);
            if (h != null && h.Id == pressId && h.Click != null) h.Click();
            pressId = null;
            Invalidate();
        }

        void SetMemoryFromMouse(int mouseX)
        {
            float frac = (mouseX - sliderTrack.X) / sliderTrack.Width;
            frac = Math.Max(0, Math.Min(1, frac));
            int mb = 2048 + (int)Math.Round(frac * (maxMemoryMb - 2048) / 512.0) * 512;
            if (mb != settings.MemoryMb) { settings.MemoryMb = mb; Invalidate(); }
        }

        // ---------------------------------------------------------------- text boxes (settings page, msa only)

        TextBox MakeTextBox(string value, EventHandler changed)
        {
            var tb = new TextBox();
            tb.BorderStyle = BorderStyle.None;
            tb.BackColor = Color.FromArgb(26, 27, 32); // opaque: WinForms text boxes reject translucent colors
            tb.ForeColor = TextC;
            tb.Font = fBody;
            tb.Text = value ?? "";
            tb.TextChanged += changed;
            tb.Visible = false;
            Controls.Add(tb);
            return tb;
        }

        void UpdateControls()
        {
            // Content text boxes sit in the translated content area, so add the sidebar width.
            bool settingsPage = page == "settings" && !overlay;
            clientIdBox.Visible = settingsPage && !UseLauncherAccount && !UseOffline;
            clientIdBox.Bounds = new Rectangle(D(66 + SIDEBAR_W), D(334), D(728), D(16));
            offlineNameBox.Visible = settingsPage && UseOffline;
            offlineNameBox.Bounds = new Rectangle(D(66 + SIDEBAR_W), D(332), D(288), D(16));
            javaBox.Visible = settingsPage && settings.UseCustomJava;
            javaBox.Bounds = new Rectangle(D(392 + SIDEBAR_W), D(426), D(376), D(18));

            bool modsPage = page == "mods" && !overlay;
            searchBox.Visible = modsPage;
            searchBox.Bounds = new Rectangle(D(156 + SIDEBAR_W), D(78), D(634), D(18));

            Invalidate();
        }

        // ---------------------------------------------------------------- actions

        void OnSignInChip()
        {
            if (UseLauncherAccount) OnPlayCore(false);  // loads the launcher account (and shows friendly errors)
            else StartSignIn();
        }

        async void OnPlay() { await Task.Yield(); OnPlayCore(true); }

        async void OnPlayCore(bool thenLaunch)
        {
            if (busy) return;

            // Microsoft sign-in just needs an interactive sign-in the first time. Offline needs none.
            if (!UseOffline && !UseLauncherAccount && account == null) { StartSignIn(); return; }

            busy = true; status = "Getting ready..."; fraction = -1; anim.Start(); Invalidate();
            var statusReporter = new Progress<string>(delegate(string s) { status = s; fraction = -1; Invalidate(); });
            var installReporter = new Progress<InstallStatus>(delegate(InstallStatus p) { status = p.Status; fraction = p.Fraction; Invalidate(); });
            try
            {
                if (UseOffline)
                    account = Account.Offline(settings.OfflineName);
                else if (UseLauncherAccount)
                    account = await Auth.FromMinecraftLauncher(statusReporter); // re-read each time (the official launcher keeps it fresh)
                else if (!account.McTokenValid)
                    account = await Auth.Refresh(EffectiveClientId, account, statusReporter);
                if (UseOffline) LoadOfflineSkin(); else LoadAvatarAsync();

                if (!thenLaunch) { busy = false; anim.Stop(); status = ""; Invalidate(); return; }

                var plan = await Installer.Prepare(settings, account, repairNext, installReporter);
                repairNext = false;
                var game = Installer.Launch(plan);
                status = "Minecraft is starting..."; fraction = 1; Invalidate();

                if (settings.CloseOnLaunch)
                {
                    await Task.Delay(2000);
                    Close();
                    return;
                }

                await Task.Delay(2000);
                status = "Minecraft is running"; fraction = -1; anim.Stop(); Invalidate();
                WindowState = FormWindowState.Minimized;
                await Task.Run(delegate { game.WaitForExit(); });
                WindowState = FormWindowState.Normal; Activate();
                status = "";
                if (game.ExitCode != 0)
                    ShowMessage("Minecraft closed unexpectedly", "Exit code " + game.ExitCode + ". The game log may say why.",
                        "Open logs", delegate { HideOverlay(); OpenFolder(Path.Combine(Paths.Game, "logs")); });
            }
            catch (AuthException ex)
            {
                Log.Write("Auth: " + ex.Message);
                WriteLastError("Account: " + ex.Message);
                status = "";
                ShowMessage("Account problem", ex.Message,
                    UseLauncherAccount ? "Open Minecraft Launcher" : null,
                    UseLauncherAccount ? (Action)delegate { HideOverlay(); OpenMinecraftLauncher(); } : null);
            }
            catch (Exception ex)
            {
                Log.Write("Launch failed: " + ex);
                WriteLastError("Launch: " + ex);
                status = "";
                ShowMessage("Couldn't start the game", ex.Message, "Open launcher log", delegate { HideOverlay(); OpenFile(Paths.LogFile); });
            }
            finally
            {
                busy = false; anim.Stop(); Invalidate();
            }
        }

        // ---------------------------------------------------------------- mod explorer actions

        async void RunSearch()
        {
            if (modsBusy) return;
            string q = searchBox.Text == null ? "" : searchBox.Text.Trim();
            modsBusy = true; modsMsg = "Searching..."; modResults = new List<ModHit>(); modScroll = 0; Invalidate();
            try
            {
                var list = await Mods.Search(q, Installer.McVersion);
                modResults = list;
                modsMsg = list.Count == 0 ? "No Fabric mods found for Minecraft " + Installer.McVersion + "." : "";
            }
            catch (Exception ex)
            {
                modsMsg = "Search failed: " + ex.Message;
                Log.Write("Mod search: " + ex);
            }
            finally { modsBusy = false; Invalidate(); }
        }

        async void InstallMod(ModHit h)
        {
            if (modBusyId != null) return;
            modBusyId = h.ProjectId; modsMsg = "Installing " + h.Title + "..."; Invalidate();
            try
            {
                var rec = await Mods.Install(h, Installer.McVersion);
                installedMods.RemoveAll(delegate(InstalledMod m) { return m.Provider == rec.Provider && m.ProjectId == rec.ProjectId; });
                installedMods.Add(rec);
                ModStore.Save(installedMods);
                string deps = rec.DepFiles.Count > 0 ? " (plus " + rec.DepFiles.Count + " dependenc" + (rec.DepFiles.Count == 1 ? "y" : "ies") + ")" : "";
                modsMsg = h.Title + " installed" + deps + ".";
            }
            catch (Exception ex)
            {
                modsMsg = "Couldn't install " + h.Title + ": " + ex.Message;
                Log.Write("Install mod: " + ex);
            }
            finally { modBusyId = null; Invalidate(); }
        }

        void RemoveMod(ModHit h)
        {
            var m = installedMods.Find(delegate(InstalledMod x) { return x.Provider == h.Provider && x.ProjectId == h.ProjectId; });
            if (m == null) return;
            Mods.Remove(m, installedMods);
            installedMods.Remove(m);
            ModStore.Save(installedMods);
            modsMsg = h.Title + " removed.";
            Invalidate();
        }

        bool IsInstalled(ModHit h)
        {
            return installedMods.Exists(delegate(InstalledMod m) { return m.Provider == h.Provider && m.ProjectId == h.ProjectId; });
        }

        async void EnsureIcon(ModHit h)
        {
            if (h.IconTried || string.IsNullOrEmpty(h.IconUrl)) return;
            h.IconTried = true;
            try
            {
                byte[] bytes = await Net.GetBytes(h.IconUrl.Replace("http://", "https://"));
                using (var ms = new MemoryStream(bytes))
                using (var tmp = new Bitmap(ms))
                    h.Icon = new Bitmap(tmp); // copy so we don't hold the stream
                Invalidate();
            }
            catch (Exception e) { Log.Write("Mod icon: " + e.Message); }
        }

        /// <summary>Best-effort: fill the account chip from the official launcher without any network wait.</summary>
        void TryAutoLoadLauncherAccount()
        {
            Task.Run(delegate
            {
                try
                {
                    var info = LauncherAccountReader.ReadActive();
                    if (info == null) return;
                    var a = new Account();
                    a.Source = "launcher"; a.Name = info.Name; a.Uuid = info.Uuid;
                    a.McToken = info.AccessToken; a.Xuid = "0";
                    a.McTokenExpiresUtcTicks = DateTime.UtcNow.AddHours(1).Ticks;
                    BeginInvoke((Action)delegate { if (account == null) { account = a; FetchSkinForUuid(info.Uuid); Invalidate(); } });
                }
                catch (Exception e) { Log.Write("Auto-load: " + e.Message); }
            });
        }

        async void StartSignIn()
        {
            if (busy) return;
            if (string.IsNullOrWhiteSpace(EffectiveClientId))
            {
                page = "settings"; UpdateControls();
                ShowMessage("Microsoft sign-in needs your app ID",
                    "Standalone sign-in needs your own Azure app ID, approved by Mojang. Paste it under Account in Settings once it's approved. See SETUP-LOGIN.txt for how.",
                    "Open setup guide", delegate { HideOverlay(); OpenSetupGuide(); });
                return;
            }
            signInCancel = new CancellationTokenSource();
            var cancel = signInCancel;
            ShowOverlay("Sign in with Microsoft", "Connecting to Microsoft...", null, null, null, "Cancel", delegate { cancel.Cancel(); HideOverlay(); });
            try
            {
                var dc = await Auth.StartDeviceCode(EffectiveClientId);
                if (cancel.IsCancellationRequested) return;
                string link = dc.VerificationUri + "?otc=" + Uri.EscapeDataString(dc.UserCode);
                ShowOverlay("Sign in with Microsoft",
                    "Open microsoft.com/link and enter this code. It's copied to your clipboard.",
                    dc.UserCode, "Open sign-in page", delegate { OpenUrl(link); },
                    "Cancel", delegate { cancel.Cancel(); HideOverlay(); });
                try { Clipboard.SetText(dc.UserCode); } catch { }
                ovStatus = "Waiting for you to sign in...";
                OpenUrl(link);

                var reporter = new Progress<string>(delegate(string s) { ovStatus = s; Invalidate(); });
                account = await Auth.WaitForDeviceCode(EffectiveClientId, dc, cancel.Token, reporter);
                HideOverlay();
                LoadAvatarAsync();
                Activate();
            }
            catch (OperationCanceledException) { }
            catch (AuthException ex) { if (!cancel.IsCancellationRequested) ShowMessage("Sign-in problem", ex.Message, null); }
            catch (Exception ex)
            {
                Log.Write("Sign-in failed: " + ex);
                if (!cancel.IsCancellationRequested) ShowMessage("Sign-in problem", ex.Message, null);
            }
        }

        void ShowOverlay(string title, string text, string code, string primary, Action primaryAction, string secondary, Action secondaryAction)
        {
            overlay = true; ovTitle = title; ovText = text; ovCode = code; ovStatus = null;
            ovPrimary = primary; ovPrimaryAction = primaryAction;
            ovSecondary = secondary; ovSecondaryAction = secondaryAction;
            UpdateControls();
        }

        void ShowMessage(string title, string text, string action, Action actionClick = null)
        {
            if (action != null && actionClick != null) ShowOverlay(title, text, null, action, actionClick, "Close", delegate { HideOverlay(); });
            else ShowOverlay(title, text, null, "OK", delegate { HideOverlay(); }, null, null);
        }

        void HideOverlay() { overlay = false; UpdateControls(); }

        void BrowseJava()
        {
            using (var dlg = new OpenFileDialog())
            {
                dlg.Title = "Choose javaw.exe (Java 25 or newer)";
                dlg.Filter = "Java (javaw.exe;java.exe)|javaw.exe;java.exe";
                if (dlg.ShowDialog(this) == DialogResult.OK) { javaBox.Text = dlg.FileName; }
            }
        }

        void OpenSetupGuide()
        {
            string guide = Path.Combine(Paths.ExeDir, "SETUP-LOGIN.txt");
            if (File.Exists(guide)) OpenFile(guide);
            else ShowMessage("Setup guide not found", "SETUP-LOGIN.txt should be next to CyLauncher.exe.", null);
        }

        /// <summary>Opens the official Minecraft Launcher (the Java-capable hub), never the Bedrock app.</summary>
        static void OpenMinecraftLauncher()
        {
            string[] exes = {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
            };
            foreach (var exe in exes)
                if (File.Exists(exe)) { try { Process.Start(exe); return; } catch { } }
            // Microsoft Store install: the launcher hub (NOT the minecraft:// link, which belongs to Bedrock).
            try { Process.Start("explorer.exe", "shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft"); } catch { }
        }

        static void WriteLastError(string message)
        {
            try { File.WriteAllText(Path.Combine(Paths.ExeDir, "cy-last-error.txt"), DateTime.Now + "\r\n" + message); } catch { }
        }

        static void OpenFolder(string dir) { try { Directory.CreateDirectory(dir); Process.Start("explorer.exe", "\"" + dir + "\""); } catch { } }
        static void OpenFile(string path) { try { Process.Start(path); } catch { } }
        static void OpenUrl(string url) { try { Process.Start(url); } catch { } }

        async void LoadAvatarAsync()
        {
            if (account == null) return;
            if (string.IsNullOrEmpty(account.SkinUrl)) { FetchSkinForUuid(account.Uuid); return; }
            try
            {
                byte[] png = await Net.GetBytes(account.SkinUrl.Replace("http://", "https://"));
                SetAvatarFromSkin(png);
            }
            catch (Exception e) { Log.Write("Avatar: " + e.Message); }
        }

        /// <summary>Gets the skin URL from Mojang's public session server (used for launcher accounts).</summary>
        async void FetchSkinForUuid(string uuid)
        {
            if (string.IsNullOrEmpty(uuid)) return;
            try
            {
                string body = await Net.GetString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                var props = J.Arr(J.ParseObj(body), "properties");
                if (props == null || props.Length == 0) return;
                string value = J.Str(props[0], "value");
                var decoded = J.ParseObj(System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(value)));
                var skin = J.Obj(J.Obj(decoded, "textures"), "SKIN");
                string skinUrl = J.Str(skin, "url");
                if (string.IsNullOrEmpty(skinUrl)) return;
                if (account != null) account.SkinUrl = skinUrl;
                byte[] png = await Net.GetBytes(skinUrl.Replace("http://", "https://"));
                SetAvatarFromSkin(png);
            }
            catch (Exception e) { Log.Write("Skin lookup: " + e.Message); }
        }

        void SetAvatarFromSkin(byte[] png)
        {
            try
            {
                using (var ms = new MemoryStream(png))
                using (var skin = new Bitmap(ms))
                {
                    var face = new Bitmap(8, 8);
                    using (var g = Graphics.FromImage(face))
                    {
                        g.InterpolationMode = InterpolationMode.NearestNeighbor;
                        g.DrawImage(skin, new Rectangle(0, 0, 8, 8), new Rectangle(8, 8, 8, 8), GraphicsUnit.Pixel);   // face
                        g.DrawImage(skin, new Rectangle(0, 0, 8, 8), new Rectangle(40, 8, 8, 8), GraphicsUnit.Pixel);  // hat layer
                    }
                    avatar = face;

                    // Keep a full 64x64 copy for the 3D viewer (drawn onto a fresh canvas so legacy
                    // 64x32 skins and odd sizes never read out of bounds).
                    var full = new Bitmap(64, 64, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
                    using (var gg = Graphics.FromImage(full))
                    {
                        gg.InterpolationMode = InterpolationMode.NearestNeighbor;
                        gg.PixelOffsetMode = PixelOffsetMode.Half;
                        gg.DrawImage(skin, 0, 0, skin.Width, skin.Height);
                    }
                    var oldSkin = skin3d;
                    skin3d = new Skin3D(full, false);
                    if (oldSkin != null) oldSkin.Dispose();
                }
                if (IsHandleCreated) BeginInvoke((Action)Invalidate);
            }
            catch (Exception e) { Log.Write("Avatar build: " + e.Message); }
        }

        static Image LoadImage(string resource)
        {
            try
            {
                var s = typeof(MainForm).Assembly.GetManifestResourceStream(resource);
                return s == null ? null : Image.FromStream(s);
            }
            catch { return null; }
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        class MEMORYSTATUSEX
        {
            public uint dwLength = (uint)Marshal.SizeOf(typeof(MEMORYSTATUSEX));
            public uint dwMemoryLoad; public ulong ullTotalPhys, ullAvailPhys, ullTotalPageFile, ullAvailPageFile, ullTotalVirtual, ullAvailVirtual, ullAvailExtendedVirtual;
        }
        [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        static extern bool GlobalMemoryStatusEx([In, Out] MEMORYSTATUSEX buffer);

        static long TotalRamMb()
        {
            var m = new MEMORYSTATUSEX();
            return GlobalMemoryStatusEx(m) ? (long)(m.ullTotalPhys / 1048576) : 8192;
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (signInCancel != null) signInCancel.Cancel();
            settings.Save();
            base.OnFormClosed(e);
        }
    }

    /// <summary>Re-uses brushes instead of creating new ones every frame.</summary>
    static class BrushCache
    {
        static readonly Dictionary<int, SolidBrush> Cache = new Dictionary<int, SolidBrush>();
        public static SolidBrush Get(Color c)
        {
            SolidBrush b;
            int key = c.ToArgb();
            if (!Cache.TryGetValue(key, out b)) { b = new SolidBrush(c); Cache[key] = b; }
            return b;
        }
    }
}
