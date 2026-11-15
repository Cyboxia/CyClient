package com.cyclient.util;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ClientSettingsModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Every UI color comes from here, based on the Client Settings module,
 * so changing a setting restyles the whole client instantly.
 */
public final class Theme {
	private Theme() {}

	private static ClientSettingsModule s() {
		return ModuleManager.get(ClientSettingsModule.class);
	}

	// ---------------------------------------------------------------- menu

	public static int accent() {
		if (s().rainbowAccent.get()) return rainbow(0, 0.55F);
		return s().accent.argb();
	}

	// The opacity slider fades the whole menu together. The panel is the most see-through layer;
	// the top bar and module buttons stay a bit more solid so they read dark and clear.
	private static int panelAlpha() {
		return Math.round(s().panelOpacity.get() / 100F * 255F);
	}

	private static int surfaceAlpha() {
		return Math.min(255, panelAlpha() + 40);
	}

	private static int cardRgb() {
		return s().cardColor.argb() & 0xFFFFFF;
	}

	public static int card() {
		return (surfaceAlpha() << 24) | cardRgb();
	}

	public static int cardHover() {
		return (surfaceAlpha() << 24) | (shade(cardRgb(), 9) & 0xFFFFFF);
	}

	public static int control() {
		return (surfaceAlpha() << 24) | (shade(cardRgb(), 14) & 0xFFFFFF);
	}

	public static int header() {
		return (surfaceAlpha() << 24) | (shade(cardRgb(), -6) & 0xFFFFFF);
	}

	/** The big background panel: darker than the cards, with adjustable opacity. */
	public static int panel() {
		return (panelAlpha() << 24) | (shade(cardRgb(), -12) & 0xFFFFFF);
	}

	/** Soft gray border for module buttons; fades with the menu opacity. */
	public static int cardOutline() {
		return (surfaceAlpha() << 24) | 0x565B64;
	}

	public static int radius() {
		return s().rounding.get();
	}

	public static int textOnAccent() {
		int a = accent();
		int brightness = (((a >> 16) & 0xFF) * 299 + ((a >> 8) & 0xFF) * 587 + (a & 0xFF) * 114) / 1000;
		return brightness > 140 ? 0xFF0B0D10 : 0xFFFFFFFF;
	}

	/** What mod cards show under the name: "Icon", "Description" or "Name Only". */
	public static String cardContent() {
		return s().cardContent.get();
	}

	public static boolean tooltips() {
		return s().tooltips.get();
	}

	public static boolean openAnimation() {
		return s().openAnimation.get();
	}

	public static boolean clickSounds() {
		return s().clickSounds.get();
	}

	public static boolean rememberPage() {
		return s().rememberPage.get();
	}

	public static boolean pauseGame() {
		return s().pauseGame.get();
	}

	// ---------------------------------------------------------------- HUD

	public static int hudBackground() {
		int alpha = Math.round(s().hudBackgroundOpacity.get() / 100F * 255F);
		return (alpha << 24) | s().hudBackground.get();
	}

	/** HUD text color. With chroma on, the color depends on screen position so it flows across the HUD. */
	public static int hudText(int x, int y) {
		if (s().chromaText.get()) return rainbow((x + y) * 6, 0.65F);
		return s().hudText.argb();
	}

	public static boolean hudShadow() {
		return s().hudShadow.get();
	}

	public static boolean hudRounded() {
		return s().hudRounded.get();
	}

	public static void drawHudBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int r = s().hudRounded.get() ? 3 : 0;
		Render2D.roundedRect(g, x, y, x + w, y + h, r, hudBackground());
	}

	public static void drawHudText(GuiGraphicsExtractor g, String text, int x, int y) {
		g.text(UiFont.hud(""), text, x, y, hudText(x, y), hudShadow());
	}

	// ---------------------------------------------------------------- helpers

	/** A color that cycles through the rainbow over time. offsetMs shifts the phase. */
	public static int rainbow(int offsetMs, float saturation) {
		float hue = ((System.currentTimeMillis() + offsetMs) % 4000L) / 4000F;
		return 0xFF000000 | hsbToRgb(hue, saturation, 1F);
	}

	/** Hue/saturation/brightness (each 0-1) to 0xRRGGBB. */
	private static int hsbToRgb(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6F;
		int sector = (int) hh;
		float f = hh - sector;
		float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	/** Brightens (positive) or darkens (negative) each RGB channel, keeping alpha. */
	public static int shade(int color, int delta) {
		int a = (color >>> 24) & 0xFF;
		int r = Math.max(0, Math.min(255, ((color >> 16) & 0xFF) + delta));
		int g = Math.max(0, Math.min(255, ((color >> 8) & 0xFF) + delta));
		int b = Math.max(0, Math.min(255, (color & 0xFF) + delta));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
