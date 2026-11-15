package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A square grid of on/off pixels (used for the custom crosshair drawing).
 * Saved to the config as a string of '0' and '1' characters, row by row.
 * Not shown as a normal widget; it has its own editor screen.
 */
public class PixelGridSetting extends Setting<boolean[]> {
	private final int size;
	private final boolean[] defaults;

	public PixelGridSetting(String name, int size, boolean[] defaultPixels) {
		super(name, defaultPixels.clone());
		this.size = size;
		this.defaults = defaultPixels.clone();
	}

	public int getSize() {
		return size;
	}

	public boolean get(int x, int y) {
		if (x < 0 || y < 0 || x >= size || y >= size) return false;
		return value[y * size + x];
	}

	public void set(int x, int y, boolean on) {
		if (x < 0 || y < 0 || x >= size || y >= size) return;
		value[y * size + x] = on;
	}

	@Override
	public void reset() {
		value = defaults.clone();
	}

	public void clear() {
		value = new boolean[size * size];
	}

	@Override
	public JsonElement toJson() {
		StringBuilder sb = new StringBuilder(size * size);
		for (boolean on : value) sb.append(on ? '1' : '0');
		return new JsonPrimitive(sb.toString());
	}

	@Override
	public void fromJson(JsonElement json) {
		String saved = json.getAsString();
		if (saved.length() != size * size) return; // wrong size, keep default
		boolean[] pixels = new boolean[size * size];
		for (int i = 0; i < pixels.length; i++) pixels[i] = saved.charAt(i) == '1';
		value = pixels;
	}
}
