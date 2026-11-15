package com.cyclient.module;

import com.cyclient.gui.FontPickerScreen;
import com.cyclient.setting.ActionSetting;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.HeaderSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.Setting;
import com.cyclient.setting.StringSetting;
import com.cyclient.util.Render2D;
import com.cyclient.util.SystemFonts;
import com.cyclient.util.Theme;
import com.cyclient.util.UiFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A module that draws something on screen and can be dragged around in the HUD editor.
 * Positions are stored in GUI-scaled pixels from the top-left corner.
 *
 * Every HUD module automatically gets an "Appearance" section (scale, background,
 * custom colors) listed after its own settings.
 */
public abstract class HudModule extends Module {
	public int x;
	public int y;
	private final int defaultX;
	private final int defaultY;

	private final List<Setting<?>> appearance = new ArrayList<>();
	public final IntSetting scale = addAppearance(new IntSetting("Scale %", 100, 50, 200));
	public final BooleanSetting background = addAppearance(new BooleanSetting("Background", true));
	public final BooleanSetting customColors = addAppearance(new BooleanSetting("Custom Colors", false));
	public final ColorSetting textColor = addAppearance(new ColorSetting("Text Color", 0xFFFFFF));
	public final ColorSetting valueColor = addAppearance(new ColorSetting("Value Color", 0xFFFFFF));
	public final ColorSetting backgroundColor = addAppearance(new ColorSetting("Background Color", 0x000000));
	public final IntSetting backgroundOpacity = addAppearance(new IntSetting("Background Opacity", 44, 0, 100));
	public final BooleanSetting textShadow = addAppearance(new BooleanSetting("Text Shadow", true));
	public final StringSetting fontPath = addAppearance(new StringSetting("Font File", "", 400));
	public final ActionSetting fontPicker = addAppearance(new ActionSetting("Font", this::fontLabel, false,
			screen -> mc.gui.setScreen(new FontPickerScreen(screen, getName() + " Font", fontPath,
					"Use HUD Font", this::font, () -> UiFont.hudError(fontPath.get())))));

	protected HudModule(String name, String description, boolean enabledByDefault, int defaultX, int defaultY) {
		super(name, description, enabledByDefault);
		this.x = defaultX;
		this.y = defaultY;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		fontPath.hideFromMenu(); // chosen with the font picker
	}

	/** Adds a setting to the Appearance section (shown after the module's own settings). */
	protected <S extends Setting<?>> S addAppearance(S setting) {
		appearance.add(setting);
		return setting;
	}

	@Override
	public List<Setting<?>> getSettings() {
		List<Setting<?>> all = new ArrayList<>(super.getSettings());
		all.add(new HeaderSetting("Appearance"));
		all.addAll(appearance);
		return all;
	}

	/** Modules whose default position depends on screen size (e.g. right side) override this. */
	protected void placeDefault(int screenWidth, int screenHeight) {
		x = Math.max(0, x);
		y = Math.max(0, y);
	}

	public void resetPosition() {
		x = defaultX;
		y = defaultY;
	}

	@Override
	public void resetToDefaults() {
		super.resetToDefaults();
		resetPosition();
	}

	@Override
	public Category getCategory() {
		return Category.HUD;
	}

	/** Unscaled size. */
	public abstract int getWidth();

	public abstract int getHeight();

	/**
	 * Draw the module with its top-left corner at (x, y), at 100% scale.
	 * @param editor true while the HUD editor is open, so modules can show sample data
	 */
	public abstract void render(GuiGraphicsExtractor graphics, int x, int y, boolean editor);

	/** Draws the module at its position and scale. This is what the HUD and editor call. */
	public final void renderScaled(GuiGraphicsExtractor graphics, boolean editor) {
		if (x < 0 || y < 0) placeDefault(graphics.guiWidth(), graphics.guiHeight());
		float s = getScale();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(s, s);
		render(graphics, 0, 0, editor);
		graphics.pose().popMatrix();
	}

	public float getScale() {
		return scale.get() / 100F;
	}

	public int getScaledWidth() {
		return Math.round(getWidth() * getScale());
	}

	public int getScaledHeight() {
		return Math.round(getHeight() * getScale());
	}

	public boolean isHovered(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + getScaledWidth() && mouseY >= y && mouseY <= y + getScaledHeight();
	}

	// ---------------------------------------------------------------- drawing helpers for subclasses

	/** The normal text color: this module's custom color, or the global theme. */
	protected int labelColor(int localX, int localY) {
		return customColors.get() ? textColor.argb() : Theme.hudText(x + localX, y + localY);
	}

	/** Color for values (numbers). Same as text unless Custom Colors sets a different one. */
	protected int valueTextColor(int localX, int localY) {
		return customColors.get() ? valueColor.argb() : Theme.hudText(x + localX, y + localY);
	}

	protected boolean shadow() {
		return customColors.get() ? textShadow.get() : Theme.hudShadow();
	}

	protected int backgroundArgb() {
		if (customColors.get()) {
			int alpha = Math.round(backgroundOpacity.get() / 100F * 255F);
			return (alpha << 24) | backgroundColor.get();
		}
		return Theme.hudBackground();
	}

	/** Draws the background box (if Background is on). */
	protected void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (!background.get()) return;
		Render2D.roundedRect(g, x, y, x + w, y + h, Theme.hudRounded() ? 3 : 0, backgroundArgb());
	}

	/** The font this widget draws with: its own, or the global HUD font, or Minecraft's. */
	public Font font() {
		return UiFont.hud(fontPath.get());
	}

	private String fontLabel() {
		return fontPath.get().isEmpty() ? "HUD Font" : SystemFonts.name(new File(fontPath.get()));
	}

	protected void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(font(), text, x, y, color, shadow());
	}
}
