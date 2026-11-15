package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short piece of text the player can type into a box in the menu. */
public class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String name, String defaultValue, int maxLength) {
		super(name, defaultValue);
		this.maxLength = maxLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	public void set(String value) {
		if (value == null) value = "";
		this.value = value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		set(json.getAsString());
	}
}
