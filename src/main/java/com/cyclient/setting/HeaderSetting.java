package com.cyclient.setting;

import com.google.gson.JsonElement;

/** Not a real option: a section title that groups the settings below it. */
public class HeaderSetting extends Setting<String> {
	public HeaderSetting(String title) {
		super(title, title);
	}

	@Override
	public JsonElement toJson() {
		return null;
	}

	@Override
	public void fromJson(JsonElement json) {}
}
