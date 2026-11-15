package com.cyclient.module.modules;

import com.cyclient.config.Config;
import com.cyclient.module.HudModule;
import com.cyclient.module.Module;
import com.cyclient.module.ModuleManager;
import com.cyclient.setting.ActionSetting;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.HeaderSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.KeybindSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.gui.FontPickerScreen;
import com.cyclient.setting.StringSetting;
import com.cyclient.util.Render2D;
import com.cyclient.util.SystemFonts;
import com.cyclient.util.Theme;
import com.cyclient.util.UiFont;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.File;

/**
 * Look-and-feel options for the whole client (menu colors, HUD style, convenience options).
 * Always on, and opened from the gear button in the menu header instead of a card.
 */
public class ClientSettingsModule extends Module {
	// Controls
	public final HeaderSetting controlsHeader = add(new HeaderSetting("Controls"));
	public final KeybindSetting openKey = add(new KeybindSetting("Open Menu Key", InputConstants.KEY_RSHIFT));

	// Menu
	public final HeaderSetting menuHeader = add(new HeaderSetting("Menu"));
	public final ColorSetting accent = add(new ColorSetting("Accent Color", 0xECEEF2));
	public final BooleanSetting rainbowAccent = add(new BooleanSetting("Rainbow Accent", false));
	public final ColorSetting cardColor = add(new ColorSetting("Card Color", 0x1B1D21));
	public final IntSetting panelOpacity = add(new IntSetting("Panel Opacity", 90, 50, 100));
	public final IntSetting rounding = add(new IntSetting("Corner Rounding", 4, 0, 8));
	public final StringSetting uiFontPath = add(new StringSetting("UI Font File", "", 400));
	public final ActionSetting uiFont = add(new ActionSetting("UI Font", () -> fontLabel(uiFontPath.get()), false,
			screen -> mc.gui.setScreen(new FontPickerScreen(screen, "Menu Font", uiFontPath,
					"Bricolage (default)", UiFont::get, UiFont::getError))));
	public final IntSetting uiFontSize = add(new IntSetting("UI Font Size", 9, 6, 16));
	public final IntSetting uiFontOffset = add(new IntSetting("UI Font Offset", 0, -3, 3));
	public final ModeSetting cardContent = add(new ModeSetting("Card Content", "Icon", "Icon", "Description", "Name Only"));
	public final BooleanSetting tooltips = add(new BooleanSetting("Description Tooltips", true));
	public final BooleanSetting openAnimation = add(new BooleanSetting("Open Animation", true));
	public final BooleanSetting clickSounds = add(new BooleanSetting("Click Sounds", true));
	public final BooleanSetting rememberPage = add(new BooleanSetting("Remember Last Page", true));
	public final BooleanSetting pauseGame = add(new BooleanSetting("Pause Singleplayer", false));

	// HUD
	public final HeaderSetting hudHeader = add(new HeaderSetting("HUD"));
	public final StringSetting hudFontPath = add(new StringSetting("HUD Font File", "", 400));
	public final ActionSetting hudFont = add(new ActionSetting("HUD Font", () -> fontLabel(hudFontPath.get()), false,
			screen -> mc.gui.setScreen(new FontPickerScreen(screen, "HUD Font (all widgets)", hudFontPath,
					"Minecraft (default)", () -> UiFont.hud(""), () -> UiFont.hudError("")))));
	public final IntSetting hudFontSize = add(new IntSetting("HUD Font Size", 9, 6, 16));
	public final IntSetting hudFontOffset = add(new IntSetting("HUD Font Offset", 0, -3, 3));
	public final ColorSetting hudText = add(new ColorSetting("HUD Text Color", 0xFFFFFF));
	public final BooleanSetting chromaText = add(new BooleanSetting("Chroma HUD Text", false));
	public final BooleanSetting hudShadow = add(new BooleanSetting("HUD Text Shadow", true));
	public final ColorSetting hudBackground = add(new ColorSetting("HUD Background", 0x000000));
	public final IntSetting hudBackgroundOpacity = add(new IntSetting("HUD Background Opacity", 44, 0, 100));
	public final BooleanSetting hudRounded = add(new BooleanSetting("HUD Rounded Corners", false));
	public final ActionSetting resetHud = add(new ActionSetting("Reset HUD Layout", "Reset", true, screen -> {
		for (HudModule hud : ModuleManager.getHudModules()) hud.resetPosition();
		Config.save();
	}));

	// Data
	public final HeaderSetting dataHeader = add(new HeaderSetting("Data"));
	public final ActionSetting resetAll = add(new ActionSetting("Reset All Settings", "Reset", true, screen -> {
		for (Module module : ModuleManager.getModules()) module.resetToDefaults();
		Config.save();
	}));

	public ClientSettingsModule() {
		super("Client Settings", "Colors, HUD style and menu options for Cy Client.", true);
		uiFontPath.hideFromMenu(); // chosen with the font pickers instead of typed
		hudFontPath.hideFromMenu();
	}

	private static String fontLabel(String path) {
		if (path.isEmpty()) return "Minecraft";
		return SystemFonts.name(new File(path));
	}

	@Override
	public boolean isHidden() {
		return true;
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	/** A small sample: a HUD box and a button in the current theme. */
	@Override
	public void renderPreview(GuiGraphicsExtractor g, int cx, int cy) {
		int w = 80;
		Render2D.roundedRect(g, cx - w / 2 - 4, cy - 34, cx + w / 2 + 4, cy + 34, Theme.radius(), Theme.card());

		String sample = "FPS: 240";
		int bx = cx - 30;
		int by = cy - 24;
		Theme.drawHudBox(g, bx, by, 60, 16);
		Theme.drawHudText(g, sample, bx + (60 - UiFont.hud("").width(sample)) / 2, by + 5);

		int btnY = cy + 4;
		Render2D.roundedRect(g, cx - 30, btnY, cx + 30, btnY + 14, Math.min(3, Theme.radius()), Theme.accent());
		String label = "Button";
		g.text(mc.font, label, cx - mc.font.width(label) / 2, btnY + 3, 0xFF0B0D10, false);
	}
}
