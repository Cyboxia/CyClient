package com.cyclient.module;

import com.cyclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD module that shows one line like "FPS: 240" in a box.
 * Subclasses give a value (and optionally a label); this class handles
 * label style, brackets, colors and the box.
 */
public abstract class TextHudModule extends HudModule {
	private static final int PADDING_X = 5;
	private static final int HEIGHT = 16;
	private static final int MIN_WIDTH = 40;

	/** Only modules with a label ("FPS", "Ping"...) get the Label Style option. */
	protected final ModeSetting labelStyle;
	public final ModeSetting brackets = addAppearance(new ModeSetting("Brackets", "None", "None", "[ ]", "( )"));

	protected TextHudModule(String name, String description, boolean enabledByDefault, int defaultX, int defaultY, boolean hasLabel) {
		super(name, description, enabledByDefault, defaultX, defaultY);
		labelStyle = hasLabel
				? add(new ModeSetting("Label Style", "Label: Value", "Label: Value", "Value Label", "Value Only"))
				: null;
	}

	/** The value to show (e.g. "240"), or null to hide the module this frame. */
	protected abstract String getValue(boolean editor);

	/** The label (e.g. "FPS"). Only used if the module has a label. */
	protected String getLabel() {
		return getName();
	}

	/** Lets modules color the value, e.g. green/yellow/red ping. Default: the normal value color. */
	protected int colorValue(String value, int defaultColor) {
		return defaultColor;
	}

	/** Builds the line as [before-value text, value, after-value text]. */
	private String[] parts(boolean editor) {
		String value = getValue(editor);
		if (value == null) return null;
		String open = brackets.is("[ ]") ? "[" : brackets.is("( )") ? "(" : "";
		String close = brackets.is("[ ]") ? "]" : brackets.is("( )") ? ")" : "";

		if (labelStyle == null || labelStyle.is("Value Only")) return new String[]{open, value, close};
		if (labelStyle.is("Value Label")) return new String[]{open, value, " " + getLabel() + close};
		return new String[]{open + getLabel() + ": ", value, close};
	}

	@Override
	public int getWidth() {
		String[] p = parts(true);
		int textWidth = p == null ? 0 : font().width(p[0] + p[1] + p[2]);
		return Math.max(MIN_WIDTH, textWidth + PADDING_X * 2);
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, boolean editor) {
		String[] p = parts(editor);
		if (p == null) return;

		int width = getWidth();
		drawBox(graphics, x, y, width, HEIGHT);

		int textX = x + (width - font().width(p[0] + p[1] + p[2])) / 2;
		int textY = y + (HEIGHT - font().lineHeight) / 2 + 1;

		drawText(graphics, p[0], textX, textY, labelColor(textX, textY));
		textX += font().width(p[0]);
		drawText(graphics, p[1], textX, textY, colorValue(p[1], valueTextColor(textX, textY)));
		textX += font().width(p[1]);
		drawText(graphics, p[2], textX, textY, labelColor(textX, textY));
	}

	/** Green / yellow / red helper for "good / okay / bad" values. */
	protected static int trafficLight(boolean good, boolean okay) {
		return good ? 0xFF55FF55 : okay ? 0xFFFFFF55 : 0xFFFF5555;
	}
}
