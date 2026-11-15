package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice from a fixed list, shown as a button that cycles through the options. */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equalsIgnoreCase(mode);
	}

	public void cycle() {
		int index = modes.indexOf(value);
		value = modes.get((index + 1) % modes.size());
	}

	public void cycleBack() {
		int index = modes.indexOf(value);
		value = modes.get((index - 1 + modes.size()) % modes.size());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		String saved = json.getAsString();
		if (modes.contains(saved)) value = saved;
	}
}
