package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * An RGB color (0xRRGGBB, no transparency). Saved as a hex string like "#4FC3F7".
 * In the menu it shows a swatch that expands into presets and R/G/B sliders.
 */
public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, int defaultRgb) {
		super(name, defaultRgb & 0xFFFFFF);
	}

	@Override
	public void set(Integer rgb) {
		this.value = rgb & 0xFFFFFF;
	}

	public int red() {
		return (value >> 16) & 0xFF;
	}

	public int green() {
		return (value >> 8) & 0xFF;
	}

	public int blue() {
		return value & 0xFF;
	}

	public void setRed(int r) {
		set((clamp(r) << 16) | (value & 0x00FFFF));
	}

	public void setGreen(int g) {
		set((clamp(g) << 8) | (value & 0xFF00FF));
	}

	public void setBlue(int b) {
		set(clamp(b) | (value & 0xFFFF00));
	}

	/** The color with full opacity, ready for drawing. */
	public int argb() {
		return 0xFF000000 | value;
	}

	public String hex() {
		return String.format("#%06X", value);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(hex());
	}

	@Override
	public void fromJson(JsonElement json) {
		String s = json.getAsString().replace("#", "");
		set(Integer.parseInt(s, 16));
	}
}
