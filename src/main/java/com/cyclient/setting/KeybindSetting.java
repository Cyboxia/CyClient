package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.Map;

/**
 * A single keyboard key, stored as its GLFW key code. -1 means "not bound".
 * Used for the ClickGUI open key and for per-module toggle hotkeys.
 *
 * Key names are derived from the raw GLFW code directly (no LWJGL/Minecraft key
 * APIs needed): printable ASCII codes map to their character, and the table below
 * covers the non-printable keys people actually bind.
 */
public class KeybindSetting extends Setting<Integer> {
	public static final int NONE = -1;

	private static final Map<Integer, String> SPECIAL = new HashMap<>();
	static {
		SPECIAL.put(32, "Space");
		SPECIAL.put(256, "Esc");
		SPECIAL.put(257, "Enter");
		SPECIAL.put(258, "Tab");
		SPECIAL.put(259, "Backspace");
		SPECIAL.put(260, "Insert");
		SPECIAL.put(261, "Delete");
		SPECIAL.put(262, "Right");
		SPECIAL.put(263, "Left");
		SPECIAL.put(264, "Down");
		SPECIAL.put(265, "Up");
		SPECIAL.put(266, "Page Up");
		SPECIAL.put(267, "Page Down");
		SPECIAL.put(268, "Home");
		SPECIAL.put(269, "End");
		SPECIAL.put(280, "Caps");
		SPECIAL.put(335, "Num Enter");
		SPECIAL.put(340, "L Shift");
		SPECIAL.put(341, "L Ctrl");
		SPECIAL.put(342, "L Alt");
		SPECIAL.put(344, "R Shift");
		SPECIAL.put(345, "R Ctrl");
		SPECIAL.put(346, "R Alt");
		SPECIAL.put(348, "Menu");
		for (int i = 0; i < 12; i++) SPECIAL.put(290 + i, "F" + (i + 1)); // F1..F12
	}

	public KeybindSetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public boolean isBound() {
		return value != null && value >= 0;
	}

	/** True when this key is the one that was just pressed (ignores "None"). */
	public boolean matches(int key) {
		return isBound() && value == key;
	}

	/** A readable name for the current key, e.g. "R Shift", "C", or "None". */
	public String label() {
		return displayName(value == null ? NONE : value);
	}

	public static String displayName(int key) {
		if (key < 0) return "None";
		String special = SPECIAL.get(key);
		if (special != null) return special;
		if (key >= 33 && key <= 96) return String.valueOf((char) key); // letters, digits, symbols
		return "Key " + key;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value == null ? NONE : value);
	}

	@Override
	public void fromJson(JsonElement json) {
		set(json.getAsInt());
	}
}
