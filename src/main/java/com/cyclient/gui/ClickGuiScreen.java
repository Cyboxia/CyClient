package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.module.Category;
import com.cyclient.module.Module;
import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ClientSettingsModule;
import com.cyclient.setting.ActionSetting;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.HeaderSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.KeybindSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.setting.PixelGridSetting;
import com.cyclient.setting.Setting;
import com.cyclient.setting.StringSetting;
import com.cyclient.util.GridBackground;
import com.cyclient.util.IconCache;
import com.cyclient.util.MouseButtons;
import com.cyclient.util.Render2D;
import com.cyclient.util.Theme;
import com.cyclient.util.UiFont;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The Cy Client mod menu (Right Shift), styled after Lunar's:
 * a panel with category tabs, a search bar, a grid of mod cards,
 * and each mod's settings shown inside the same panel.
 *
 * Everything is drawn by hand. Each frame records clickable areas ("hotspots"),
 * and clicks are matched against the hotspots from the last frame.
 * All colors come from {@link Theme}, so Client Settings can restyle it live.
 */
public class ClickGuiScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFF8B929D;
	private static final int GREEN = 0xFF27AE60;
	private static final int RED = 0xFFC0392B;
	private static final int SWITCH_OFF = 0xFF4A505A;
	private static final int ICON_OFF = 0xFF5E646E;
	// Header banner: 39x16 GUI pixels, with one PNG per GUI scale (banner_1x.png ... banner_6x.png)
	private static final int BANNER_W = 39;
	private static final int BANNER_H = 16;
	private static final int[] BANNER_TEX_W = {39, 78, 117, 156, 194, 233};
	private static final int ICON = 24;      // icon size on the card, in GUI pixels
	private static final int ICON_TEX = 64;  // icon PNG size
	private static final Identifier FALLBACK_ICON = Identifier.fromNamespaceAndPath("cyclient", "textures/gui/icons/module.png");
	private static final Map<Identifier, Boolean> ICON_EXISTS = new HashMap<>();

	private static final int HEADER_H = 28;
	private static final int FOOTER_H = 24;
	private static final int CARD_W = 100;
	private static final int CARD_H = 64;
	private static final int CARD_GAP = 6;
	private static final int ROW_H = 22;
	private static final int HEADER_ROW_H = 18;
	private static final int CONTROL_W = 120;
	private static final int SCROLL_STEP = 18;
	private static final int OPEN_ANIM_MS = 180;
	private static final int[] COLOR_PRESETS = {
			0xFFFFFF, 0x4FC3F7, 0x2ECC71, 0xF1C40F, 0xE67E22, 0xE74C3C, 0xE84393, 0x9B59B6, 0x000000
	};

	private enum Page { MODULES, SETTINGS }

	private record Hotspot(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {
		boolean contains(double mx, double my) {
			return Render2D.inside(mx, my, x1, y1, x2, y2);
		}
	}

	// Remembered between openings when "Remember Last Page" is on.
	private static Page lastPage = Page.MODULES;
	private static Category lastCategory;
	private static Module lastSelected;
	private static String lastQuery = "";

	private final Screen parent;
	private final List<Hotspot> hotspots = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();

	private Page page = Page.MODULES;
	private Category category; // null = All
	private Module selected;
	private String query = "";
	private int moduleScroll;
	private int settingsScroll;
	private int maxScroll;
	private ColorSetting expandedColor;
	private ActionSetting pendingConfirm;
	private StringSetting focusedText; // text box being typed into, if any
	private KeybindSetting listeningBind; // keybind row waiting for the next key, if any
	private final IconCache icons = new IconCache(); // crisp, pre-scaled module icons
	private final GridBackground grid = new GridBackground(); // card backgrounds baked into one texture
	private long confirmUntil;

	// Slider dragging (works for number settings and color channels)
	private boolean leftHeld;
	private Object activeSliderKey;
	private IntConsumer activeSliderSetter;
	private int activeSliderMin, activeSliderMax;
	private int activeSliderX1, activeSliderX2;

	// Panel and content area, recalculated every frame
	private int px, py, pw, ph;
	private int cx1, cy1, cx2, cy2;

	public ClickGuiScreen(Screen parent) {
		super(Component.literal("Cy Client"));
		this.parent = parent;
		if (Theme.rememberPage()) {
			page = lastPage;
			category = lastCategory;
			selected = lastSelected;
			query = lastQuery;
			if (selected == null) page = Page.MODULES;
		}
	}

	// ================================================================ text input

	/**
	 * Minecraft 26.x only sends typed characters to a screen after it turns on text input
	 * (vanilla's sign editor does the same). Needed for the search bar and text boxes.
	 */
	@Override
	protected void init() {
		super.init();
		this.minecraft.textInputManager().startTextInput(this);
	}

	@Override
	public void removed() {
		this.minecraft.textInputManager().stopTextInput(this);
		icons.close();
		grid.close();
		super.removed();
	}

	// ================================================================ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		hotspots.clear();
		layout();

		if (activeSliderSetter != null) {
			if (leftHeld) updateSlider(mouseX);
			else clearSlider();
		}
		if (pendingConfirm != null && System.currentTimeMillis() > confirmUntil) pendingConfirm = null;

		Render2D.roundedRect(g, px, py, px + pw, py + ph, Theme.radius() + 2, Theme.panel());
		// Frosted-glass accents to match the launcher: a soft top sheen and a thin rounded border.
		g.fill(px + 8, py + 1, px + pw - 8, py + 2, 0x18FFFFFF);
		Render2D.roundedOutline(g, px, py, px + pw, py + ph, Theme.radius() + 2, 1, 0x26FFFFFF);
		drawHeader(g, mouseX, mouseY);

		g.enableScissor(cx1, cy1, cx2, cy2);
		if (page == Page.MODULES) drawModules(g, mouseX, mouseY);
		else drawSettings(g, mouseX, mouseY);
		g.disableScissor();

		drawFooter(g, mouseX, mouseY);
	}

	private void layout() {
		pw = Math.min(440, this.width - 20);
		ph = Math.min(290, this.height - 20);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;

		if (Theme.openAnimation()) {
			// Slide up slightly while opening (ease-out).
			float t = Math.min(1F, (System.currentTimeMillis() - openedAt) / (float) OPEN_ANIM_MS);
			float ease = 1F - (1F - t) * (1F - t);
			py += Math.round((1F - ease) * 16F);
		}

		cx1 = px + 8;
		cy1 = py + HEADER_H + 2;
		cx2 = px + pw - 8;
		cy2 = py + ph - FOOTER_H - 2;
	}

	private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
		int r = Theme.radius() + 2;
		Render2D.roundedRect(g, px, py, px + pw, py + HEADER_H, r, Theme.header());
		g.fill(px, py + HEADER_H - r, px + pw, py + HEADER_H, Theme.header()); // square off the bottom corners

		// Cy Client banner (logo + "client"), picked to match the GUI scale so it stays crisp.
		int scale = Math.max(1, Math.min(BANNER_TEX_W.length, this.minecraft.getWindow().getGuiScale()));
		int texW = BANNER_TEX_W[scale - 1], texH = BANNER_H * scale;
		Identifier banner = Identifier.fromNamespaceAndPath("cyclient", "textures/gui/brand/banner_" + scale + "x.png");
		int bannerY = py + (HEADER_H - BANNER_H) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, banner, px + 10, bannerY, 0F, 0F, BANNER_W, BANNER_H, texW, texH, texW, texH, 0xFFFFFFFF);

		// Category tabs
		int x = px + 10 + BANNER_W + 12;
		x = drawTab(g, mx, my, x, "All", null);
		for (Category c : Category.values()) {
			x = drawTab(g, mx, my, x, c.getDisplayName(), c);
		}

		// Search box (right side)
		int sw = pw < 420 ? 76 : 100;
		int sx = px + pw - 10 - sw;
		int sy = py + 7;
		Render2D.roundedRect(g, sx, sy, sx + sw, sy + 14, small(), Theme.control());
		boolean cursor = page == Page.MODULES && (System.currentTimeMillis() / 500) % 2 == 0;
		if (query.isEmpty()) {
			g.text(font(), "Search...", sx + 5, sy + 3, TEXT_DIM, false);
		} else {
			String shown = trimLeft(query, sw - 14);
			g.text(font(), shown + (cursor ? "_" : ""), sx + 5, sy + 3, TEXT, false);
		}

		// Client Settings button, left of the search box
		ClientSettingsModule settings = ModuleManager.get(ClientSettingsModule.class);
		String label = "Settings";
		int bw = font().width(label) + 12;
		int bx = sx - 4 - bw;
		boolean active = page == Page.SETTINGS && selected == settings;
		boolean hover = Render2D.inside(mx, my, bx, sy, bx + bw, sy + 14);
		int bg = active ? Theme.accent() : (hover ? Theme.shade(Theme.control(), 15) : Theme.control());
		Render2D.roundedRect(g, bx, sy, bx + bw, sy + 14, small(), bg);
		g.text(font(), label, bx + 6, sy + 3, active ? Theme.textOnAccent() : TEXT, false);
		hotspots.add(new Hotspot(bx, sy, bx + bw, sy + 14, () -> openSettings(settings), null));
	}

	/** Draws one category tab and returns the x position for the next one. */
	private int drawTab(GuiGraphicsExtractor g, int mx, int my, int x, String label, Category tabCategory) {
		int w = font().width(label) + 12;
		int y = py + 7;
		boolean active = page == Page.MODULES && category == tabCategory;
		boolean hover = Render2D.inside(mx, my, x, y, x + w, y + 14);
		int bg = active ? Theme.accent() : (hover ? Theme.shade(Theme.control(), 15) : Theme.control());
		Render2D.roundedRect(g, x, y, x + w, y + 14, small(), bg);
		g.text(font(), label, x + 6, y + 3, active ? Theme.textOnAccent() : TEXT, false);
		hotspots.add(new Hotspot(x, y, x + w, y + 14, () -> {
			category = tabCategory;
			page = Page.MODULES;
			moduleScroll = 0;
		}, null));
		return x + w + 4;
	}

	private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
		int y = py + ph - FOOTER_H + 4;
		String label = "Edit HUD Layout";
		int w = font().width(label) + 14;
		int x = px + 10;
		boolean hover = Render2D.inside(mx, my, x, y, x + w, y + 16);
		Render2D.roundedRect(g, x, y, x + w, y + 16, small(), hover ? Theme.shade(Theme.accent(), 25) : Theme.accent());
		g.text(font(), label, x + 7, y + 4, Theme.textOnAccent(), false);
		hotspots.add(new Hotspot(x, y, x + w, y + 16, () -> this.minecraft.gui.setScreen(new HudEditorScreen(this)), null));

		String version = "Cy Client v" + FabricLoader.getInstance().getModContainer("cyclient")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		g.text(font(), version, px + pw - 10 - font().width(version), y + 4, TEXT_DIM, false);
	}

	// ================================================================ modules page

	private List<Module> filteredModules() {
		String q = query.toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : ModuleManager.getModules()) {
			if (m.isHidden()) continue;
			if (category != null && m.getCategory() != category) continue;
			if (!q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q)
					&& !m.getDescription().toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(m);
		}
		return list;
	}

	private void drawModules(GuiGraphicsExtractor g, int mx, int my) {
		List<Module> list = filteredModules();
		int contentW = cx2 - cx1;
		int cols = Math.max(1, (contentW + CARD_GAP) / (CARD_W + CARD_GAP));
		int gridW = cols * CARD_W + (cols - 1) * CARD_GAP;
		int startX = cx1 + (contentW - gridW) / 2;
		int rows = (list.size() + cols - 1) / cols;
		int totalH = rows * (CARD_H + CARD_GAP);

		maxScroll = Math.max(0, totalH - (cy2 - cy1));
		moduleScroll = clamp(moduleScroll, 0, maxScroll);

		if (list.isEmpty()) {
			String msg = "No mods found";
			g.text(font(), msg, cx1 + (contentW - font().width(msg)) / 2, cy1 + 30, TEXT_DIM, false);
			return;
		}

		// The card backgrounds + outlines are baked into one texture and blitted once (fast + smooth).
		int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
		boolean baked = grid.ensure(cols, rows, CARD_W, CARD_H, CARD_GAP, Theme.radius(),
				Theme.card(), Theme.cardOutline(), scale);
		if (baked) {
			int top = cy1 - moduleScroll;
			g.blit(RenderPipelines.GUI_TEXTURED, grid.id(), startX, top, 0F, 0F,
					grid.gridWGui(), grid.gridHGui(), grid.widthPx(), grid.heightPx(), grid.widthPx(), grid.heightPx(), 0xFFFFFFFF);
		}

		for (int i = 0; i < list.size(); i++) {
			int x = startX + (i % cols) * (CARD_W + CARD_GAP);
			int y = cy1 + (i / cols) * (CARD_H + CARD_GAP) - moduleScroll;
			if (y + CARD_H < cy1 || y > cy2) continue; // off screen
			drawCard(g, list.get(i), x, y, mx, my, !baked);
		}
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, int x, int y, int mx, int my, boolean drawBackground) {
		boolean hover = inContent(mx, my) && Render2D.inside(mx, my, x, y, x + CARD_W, y + CARD_H);
		// The baked texture holds every card's resting background. Draw a full card live only as a
		// fallback; over the baked version, a hover just gets a subtle light overlay to brighten it.
		if (drawBackground) {
			Render2D.roundedRect(g, x, y, x + CARD_W, y + CARD_H, Theme.radius(), hover ? Theme.cardHover() : Theme.card());
			Render2D.roundedOutline(g, x, y, x + CARD_W, y + CARD_H, Theme.radius(), 1, Theme.cardOutline());
		} else if (hover) {
			Render2D.roundedRect(g, x, y, x + CARD_W, y + CARD_H, Theme.radius(), 0x22FFFFFF);
		}

		String name = trim(m.getName(), CARD_W - 10);
		String content = Theme.cardContent();
		if (content.equals("Icon")) {
			g.text(font(), name, x + (CARD_W - font().width(name)) / 2, y + 7, TEXT, true);
			int iconColor = !m.isEnabled() ? ICON_OFF : (hover ? Theme.accent() : TEXT);
			// Draw the icon pre-scaled to its exact pixel size so it stays crisp (GUI blit is nearest-neighbour).
			Identifier src = iconFor(m);
			int sizePx = ICON * Math.max(1, this.minecraft.getWindow().getGuiScale());
			Identifier icon = icons.scaled(src, sizePx);
			int tex = icon == src ? ICON_TEX : sizePx;
			g.blit(RenderPipelines.GUI_TEXTURED, icon, x + (CARD_W - ICON) / 2, y + 19,
					0F, 0F, ICON, ICON, tex, tex, tex, tex, iconColor);
		} else if (content.equals("Description")) {
			g.text(font(), name, x + (CARD_W - font().width(name)) / 2, y + 7, TEXT, true);
			List<String> lines = wrap(m.getDescription(), CARD_W - 10, 2);
			for (int j = 0; j < lines.size(); j++) {
				String line = lines.get(j);
				g.text(font(), line, x + (CARD_W - font().width(line)) / 2, y + 20 + j * 10, TEXT_DIM, false);
			}
		} else {
			g.text(font(), name, x + (CARD_W - font().width(name)) / 2, y + 20, TEXT, true);
		}

		boolean hasSettings = hasVisibleSettings(m);
		int barX1 = x + 5;
		int barY1 = y + CARD_H - 17;
		int barY2 = y + CARD_H - 5;
		int barX2 = hasSettings ? x + CARD_W - 5 - 16 : x + CARD_W - 5;

		boolean barHover = hover && Render2D.inside(mx, my, barX1, barY1, barX2, barY2);
		int barColor = m.isEnabled() ? GREEN : RED;
		Render2D.roundedRect(g, barX1, barY1, barX2, barY2, small(), barHover ? Theme.shade(barColor, 25) : barColor);
		String state = m.isEnabled() ? "ENABLED" : "DISABLED";
		g.text(font(), state, barX1 + (barX2 - barX1 - font().width(state)) / 2, barY1 + 2, TEXT, true);
		addContentHotspot(barX1, barY1, barX2, barY2, m::toggle, null);

		if (hasSettings) {
			int gx1 = x + CARD_W - 5 - 14;
			int gx2 = x + CARD_W - 5;
			boolean gearHover = hover && Render2D.inside(mx, my, gx1, barY1, gx2, barY2);
			Render2D.roundedRect(g, gx1, barY1, gx2, barY2, small(), gearHover ? Theme.shade(Theme.control(), 20) : Theme.control());
			g.text(font(), "...", gx1 + (14 - font().width("...")) / 2, barY1 + 1, TEXT, false);
			addContentHotspot(gx1, barY1, gx2, barY2, () -> openSettings(m), null);
		}

		// Hovering shows the description as a tooltip (unless the card already shows it).
		if (hover && Theme.tooltips() && !content.equals("Description")) {
			g.setTooltipForNextFrame(font(), Component.literal(m.getDescription()), mx, my);
		}

		// Clicking the rest of the card opens settings (or toggles if there are none).
		addContentHotspot(x, y, x + CARD_W, y + CARD_H, () -> {
			if (hasSettings) openSettings(m);
			else m.toggle();
		}, null);
	}

	private void openSettings(Module m) {
		selected = m;
		page = Page.SETTINGS;
		settingsScroll = 0;
		expandedColor = null;
		pendingConfirm = null;
		focusedText = null;
		listeningBind = null;
	}

	// ================================================================ settings page

	private void drawSettings(GuiGraphicsExtractor g, int mx, int my) {
		Module m = selected;
		int y = cy1 + 2 - settingsScroll;

		// Top row: back button, module name, enable switch
		String back = "< Back";
		int bw = font().width(back) + 12;
		boolean backHover = inContent(mx, my) && Render2D.inside(mx, my, cx1, y, cx1 + bw, y + 16);
		Render2D.roundedRect(g, cx1, y, cx1 + bw, y + 16, small(), backHover ? Theme.shade(Theme.control(), 20) : Theme.control());
		g.text(font(), back, cx1 + 6, y + 4, TEXT, false);
		addContentHotspot(cx1, y, cx1 + bw, y + 16, () -> page = Page.MODULES, null);

		g.text(font(), m.getName(), cx1 + bw + 10, y + 4, Theme.accent(), true);
		if (m.canToggle()) {
			int swX = cx2 - 26;
			drawSwitch(g, swX, y + 2, m.isEnabled());
			addContentHotspot(swX - 4, y, cx2, y + 16, m::toggle, null);
		}

		y += 22;
		int rowRight = m.hasPreview() ? cx2 - 104 : cx2; // leave room for the preview
		// The module's own toggle hotkey comes first, then its normal settings.
		if (m.toggleKeySetting() != null) {
			y = drawSettingRow(g, m.toggleKeySetting(), y, rowRight, mx, my);
		}
		for (Setting<?> s : visibleSettings(m)) {
			y = drawSettingRow(g, s, y, rowRight, mx, my);
		}

		int totalH = (y + settingsScroll) - cy1;
		maxScroll = Math.max(0, totalH - (cy2 - cy1));
		settingsScroll = clamp(settingsScroll, 0, maxScroll);

		// Live preview (crosshair, theme sample...), pinned to the right of the content area.
		if (m.hasPreview()) {
			m.renderPreview(g, cx2 - 50, cy1 + 70);
		}
	}

	/** Draws one setting (plus any expanded sub-rows) and returns the y position for the next one. */
	private int drawSettingRow(GuiGraphicsExtractor g, Setting<?> s, int y, int rowX2, int mx, int my) {
		if (s instanceof HeaderSetting header) {
			g.text(font(), header.getName().toUpperCase(Locale.ROOT), cx1 + 2, y + 7, Theme.accent(), false);
			g.fill(cx1 + 4 + font().width(header.getName().toUpperCase(Locale.ROOT)) + 4, y + 11, rowX2, y + 12, Theme.control());
			return y + HEADER_ROW_H;
		}

		int rowY2 = y + ROW_H - 2;
		boolean hover = inContent(mx, my) && Render2D.inside(mx, my, cx1, y, rowX2, rowY2);
		Render2D.roundedRect(g, cx1, y, rowX2, rowY2, small(), hover ? Theme.cardHover() : Theme.card());

		int textY = y + (ROW_H - 2 - font().lineHeight) / 2 + 1;
		g.text(font(), s.getName(), cx1 + 8, textY, TEXT, false);

		int right = rowX2 - 8;
		int midY = y + (ROW_H - 2) / 2;

		if (s instanceof BooleanSetting b) {
			drawSwitch(g, right - 22, midY - 5, b.get());
			addContentHotspot(cx1, y, rowX2, rowY2, b::toggle, null);

		} else if (s instanceof IntSetting is) {
			drawSlider(g, right, midY, textY, is, is::get, is::set, is.getMin(), is.getMax(), y, rowY2);

		} else if (s instanceof ModeSetting mode) {
			int bx1 = right - CONTROL_W;
			Render2D.roundedRect(g, bx1, y + 3, right, rowY2 - 3, small(), Theme.control());
			g.text(font(), "<", bx1 + 5, textY, TEXT_DIM, false);
			g.text(font(), ">", right - 10, textY, TEXT_DIM, false);
			String val = trim(mode.get(), CONTROL_W - 28);
			g.text(font(), val, bx1 + (CONTROL_W - font().width(val)) / 2, textY, TEXT, false);
			int mid = bx1 + CONTROL_W / 2;
			addContentHotspot(bx1, y, mid, rowY2, mode::cycleBack, mode::cycleBack);
			addContentHotspot(mid, y, right, rowY2, mode::cycle, mode::cycleBack);

		} else if (s instanceof ColorSetting color) {
			boolean open = expandedColor == color;
			String hex = color.hex();
			int swatchX2 = right;
			int swatchX1 = right - 18;
			Render2D.roundedRect(g, swatchX1 - 1, y + 4, swatchX2 + 1, rowY2 - 4, small(), 0xFF000000 | 0x555555);
			Render2D.roundedRect(g, swatchX1, y + 5, swatchX2, rowY2 - 5, small(), color.argb());
			g.text(font(), hex, swatchX1 - 6 - font().width(hex), textY, TEXT_DIM, false);
			g.text(font(), open ? "v" : ">", cx1 + 8 + font().width(s.getName()) + 6, textY, TEXT_DIM, false);
			addContentHotspot(cx1, y, rowX2, rowY2, () -> expandedColor = open ? null : color, null);

			if (open) return drawColorEditor(g, color, y + ROW_H, rowX2, mx, my);

		} else if (s instanceof StringSetting str) {
			boolean focused = focusedText == str;
			int bx1 = right - CONTROL_W;
			Render2D.roundedRect(g, bx1 - 1, y + 2, right + 1, rowY2 - 2, small(), focused ? Theme.accent() : Theme.control());
			Render2D.roundedRect(g, bx1, y + 3, right, rowY2 - 3, small(), Theme.shade(Theme.card(), -8));
			if (str.get().isEmpty() && !focused) {
				g.text(font(), "Click to type", bx1 + 4, textY, TEXT_DIM, false);
			} else {
				boolean cursor = focused && (System.currentTimeMillis() / 500) % 2 == 0;
				String shown = trimLeft(str.get(), CONTROL_W - 14);
				g.text(font(), shown + (cursor ? "_" : ""), bx1 + 4, textY, TEXT, false);
			}
			addContentHotspot(bx1, y, right, rowY2, () -> focusedText = str, null);

		} else if (s instanceof ActionSetting action) {
			boolean confirming = pendingConfirm == action;
			String label = trim(confirming ? "Click to confirm" : action.getButtonLabel(), CONTROL_W - 8);
			int bx1 = right - CONTROL_W;
			boolean btnHover = hover && Render2D.inside(mx, my, bx1, y + 3, right, rowY2 - 3);
			int bg = confirming ? RED : Theme.accent();
			Render2D.roundedRect(g, bx1, y + 3, right, rowY2 - 3, small(), btnHover ? Theme.shade(bg, 25) : bg);
			int fg = confirming ? TEXT : Theme.textOnAccent();
			g.text(font(), label, bx1 + (CONTROL_W - font().width(label)) / 2, textY, fg, false);
			addContentHotspot(bx1, y, right, rowY2, () -> {
				if (action.needsConfirm() && pendingConfirm != action) {
					pendingConfirm = action;
					confirmUntil = System.currentTimeMillis() + 3000;
					return;
				}
				pendingConfirm = null;
				action.run(this);
			}, null);

		} else if (s instanceof KeybindSetting kb) {
			boolean listening = listeningBind == kb;
			int bx1 = right - CONTROL_W;
			boolean btnHover = hover && Render2D.inside(mx, my, bx1, y + 3, right, rowY2 - 3);
			int bg = listening ? Theme.accent() : Theme.control();
			Render2D.roundedRect(g, bx1, y + 3, right, rowY2 - 3, small(), btnHover ? Theme.shade(bg, 20) : bg);
			String label = trim(listening ? "Press a key..." : kb.label(), CONTROL_W - 8);
			int fg = listening ? Theme.textOnAccent() : TEXT;
			g.text(font(), label, bx1 + (CONTROL_W - font().width(label)) / 2, textY, fg, false);
			// Left click: listen for the next key. Right click: clear the bind.
			addContentHotspot(bx1, y, right, rowY2,
					() -> listeningBind = (listeningBind == kb ? null : kb),
					() -> { kb.set(KeybindSetting.NONE); if (listeningBind == kb) listeningBind = null; });
		}
		return y + ROW_H;
	}

	/** Preset swatches plus R/G/B sliders under an expanded color setting. */
	private int drawColorEditor(GuiGraphicsExtractor g, ColorSetting color, int y, int rowX2, int mx, int my) {
		int indent = cx1 + 12;

		// Preset swatches
		int sw = 14;
		int sx = indent;
		for (int preset : COLOR_PRESETS) {
			boolean hover = inContent(mx, my) && Render2D.inside(mx, my, sx, y + 2, sx + sw, y + 2 + sw);
			Render2D.roundedRect(g, sx - 1, y + 1, sx + sw + 1, y + 3 + sw, small(), hover ? TEXT : 0xFF555555);
			Render2D.roundedRect(g, sx, y + 2, sx + sw, y + 2 + sw, small(), 0xFF000000 | preset);
			addContentHotspot(sx, y + 2, sx + sw, y + 2 + sw, () -> color.set(preset), null);
			sx += sw + 4;
		}
		y += ROW_H;

		// Channel sliders
		String[] names = {"Red", "Green", "Blue"};
		IntSupplier[] getters = {color::red, color::green, color::blue};
		IntConsumer[] setters = {color::setRed, color::setGreen, color::setBlue};
		for (int i = 0; i < 3; i++) {
			int rowY2 = y + ROW_H - 2;
			Render2D.roundedRect(g, indent, y, rowX2, rowY2, small(), Theme.card());
			int textY = y + (ROW_H - 2 - font().lineHeight) / 2 + 1;
			g.text(font(), names[i], indent + 8, textY, TEXT_DIM, false);
			String key = color.getName() + ":" + names[i];
			drawSlider(g, rowX2 - 8, y + (ROW_H - 2) / 2, textY, key, getters[i], setters[i], 0, 255, y, rowY2);
			y += ROW_H;
		}
		return y;
	}

	private void drawSlider(GuiGraphicsExtractor g, int right, int midY, int textY, Object key,
							IntSupplier getter, IntConsumer setter, int min, int max, int rowY1, int rowY2) {
		int value = getter.getAsInt();
		String val = String.valueOf(value);
		int valW = 22;
		int tx1 = right - CONTROL_W;
		int tx2 = right - valW - 4;
		float frac = (value - min) / (float) Math.max(1, max - min);
		int knobX = tx1 + Math.round(frac * (tx2 - tx1));

		g.fill(tx1, midY - 1, tx2, midY + 1, Theme.control());
		g.fill(tx1, midY - 1, knobX, midY + 1, Theme.accent());
		Render2D.roundedRect(g, knobX - 2, midY - 5, knobX + 3, midY + 5, 2, TEXT);
		g.text(font(), val, right - font().width(val), textY, TEXT_DIM, false);

		if (key.equals(activeSliderKey)) {
			activeSliderX1 = tx1;
			activeSliderX2 = tx2;
		}
		addContentHotspot(tx1 - 4, rowY1, tx2 + 4, rowY2, () -> {
			activeSliderKey = key;
			activeSliderSetter = setter;
			activeSliderMin = min;
			activeSliderMax = max;
			activeSliderX1 = tx1;
			activeSliderX2 = tx2;
		}, null);
	}

	private void drawSwitch(GuiGraphicsExtractor g, int x, int y, boolean on) {
		Render2D.roundedRect(g, x, y, x + 22, y + 11, 5, on ? GREEN : SWITCH_OFF);
		int kx = on ? x + 12 : x + 1;
		Render2D.roundedRect(g, kx, y + 1, kx + 9, y + 10, 4, TEXT);
	}

	private void updateSlider(int mouseX) {
		float frac = (mouseX - activeSliderX1) / (float) Math.max(1, activeSliderX2 - activeSliderX1);
		frac = Math.max(0F, Math.min(1F, frac));
		activeSliderSetter.accept(activeSliderMin + Math.round(frac * (activeSliderMax - activeSliderMin)));
	}

	private void clearSlider() {
		activeSliderKey = null;
		activeSliderSetter = null;
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		boolean left = MouseButtons.isLeft(button);
		if (left) leftHeld = true;
		if (left) focusedText = null; // a click on the text box itself re-focuses it below

		for (Hotspot h : List.copyOf(hotspots)) {
			if (!h.contains(mx, my)) continue;
			if (left && h.left() != null) {
				h.left().run();
				if (activeSliderSetter != null) updateSlider((int) mx);
				playClick();
				return true;
			}
			if (MouseButtons.isRightOrMiddle(button) && h.right() != null) {
				h.right().run();
				playClick();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (MouseButtons.isLeft(event.button())) {
			leftHeld = false;
			clearSlider();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int amount = (int) Math.round(-scrollY * SCROLL_STEP);
		if (page == Page.MODULES) moduleScroll = clamp(moduleScroll + amount, 0, maxScroll);
		else settingsScroll = clamp(settingsScroll + amount, 0, maxScroll);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focusedText != null) {
			int c = event.codepoint();
			if (c >= 32 && c != 127 && c != 0xA7 && focusedText.get().length() < focusedText.getMaxLength()) {
				focusedText.set(focusedText.get() + Character.toString(c));
			}
			return true;
		}
		if (page != Page.MODULES) return super.charTyped(event);
		int cp = event.codepoint();
		if (Character.isLetterOrDigit(cp) || cp == ' ') {
			if (query.length() < 24) {
				query += Character.toString(cp);
				moduleScroll = 0;
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		// Rebinding a keybind row: the next key becomes the bind (Esc clears it).
		if (listeningBind != null) {
			listeningBind.set(key == InputConstants.KEY_ESCAPE ? KeybindSetting.NONE : key);
			listeningBind = null;
			playClick();
			return true;
		}

		if (focusedText != null) {
			String text = focusedText.get();
			if (key == InputConstants.KEY_BACKSPACE && !text.isEmpty()) {
				focusedText.set(text.substring(0, text.length() - 1));
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
				focusedText = null; // done typing
			}
			return true; // while typing, keys never close the menu
		}
		if (key == InputConstants.KEY_BACKSPACE && page == Page.MODULES && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			return true;
		}
		// The configured open key also closes the menu, like Lunar's Right Shift.
		int openKey = ModuleManager.get(ClientSettingsModule.class).openKey.get();
		if (openKey >= 0 && key == openKey) {
			closeMenu();
			return true;
		}
		return super.keyPressed(event); // Esc -> onClose()
	}

	@Override
	public void onClose() {
		if (page == Page.SETTINGS) { // Esc on a settings page goes back first
			page = Page.MODULES;
			return;
		}
		closeMenu();
	}

	private void closeMenu() {
		lastPage = page;
		lastCategory = category;
		lastSelected = selected;
		lastQuery = query;
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return Theme.pauseGame();
	}

	private void playClick() {
		if (!Theme.clickSounds()) return;
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ================================================================ helpers

	/** The menu's font: the one chosen in Client Settings, or Minecraft's. */
	private Font font() {
		return UiFont.get();
	}

	/** The module's icon, or a generic one if it doesn't have its own PNG yet. */
	private Identifier iconFor(Module m) {
		Identifier id = m.getIcon();
		boolean exists = ICON_EXISTS.computeIfAbsent(id, i -> this.minecraft.getResourceManager().getResource(i).isPresent());
		return exists ? id : FALLBACK_ICON;
	}

	/** Corner radius for small controls (buttons, tabs, switches). */
	private static int small() {
		return Math.min(3, Theme.radius());
	}

	private void addContentHotspot(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {
		// Only the part inside the visible content area is clickable.
		int ax1 = Math.max(x1, cx1), ay1 = Math.max(y1, cy1);
		int ax2 = Math.min(x2, cx2), ay2 = Math.min(y2, cy2);
		if (ax1 < ax2 && ay1 < ay2) hotspots.add(new Hotspot(ax1, ay1, ax2, ay2, left, right));
	}

	private boolean inContent(double mx, double my) {
		return Render2D.inside(mx, my, cx1, cy1, cx2, cy2);
	}

	private static List<Setting<?>> visibleSettings(Module m) {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> s : m.getSettings()) {
			if (s == m.toggleKeySetting()) continue;           // drawn separately at the top of the page
			if (!(s instanceof PixelGridSetting) && !s.isHiddenFromMenu()) list.add(s); // these have their own editors
		}
		return list;
	}

	private static boolean hasVisibleSettings(Module m) {
		// A toggleable module always has a page (at least its toggle-hotkey row).
		return m.toggleKeySetting() != null || !visibleSettings(m).isEmpty();
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Cuts text to fit maxWidth, adding "..." if it was too long. */
	private String trim(String text, int maxWidth) {
		if (font().width(text) <= maxWidth) return text;
		String dots = "...";
		while (!text.isEmpty() && font().width(text + dots) > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}
		return text + dots;
	}

	/** Keeps the end of the text (for the search box while typing). */
	private String trimLeft(String text, int maxWidth) {
		while (!text.isEmpty() && font().width(text) > maxWidth) text = text.substring(1);
		return text;
	}

	/** Word-wraps text into at most maxLines lines; the last line gets "..." if cut off. */
	private List<String> wrap(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (font().width(test) <= maxWidth) {
				line = new StringBuilder(test);
				continue;
			}
			if (!line.isEmpty()) lines.add(line.toString());
			line = new StringBuilder(word);
			if (lines.size() == maxLines) break;
		}
		if (lines.size() < maxLines && !line.isEmpty()) lines.add(line.toString());
		if (lines.size() > maxLines) lines = new ArrayList<>(lines.subList(0, maxLines));
		for (int i = 0; i < lines.size(); i++) lines.set(i, trim(lines.get(i), maxWidth)); // very long single words

		String joined = String.join(" ", lines);
		if (joined.length() < text.length() && !lines.isEmpty()) {
			int last = lines.size() - 1;
			lines.set(last, trim(lines.get(last) + "...", maxWidth));
		}
		return lines;
	}
}
