package com.cyclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number between min and max, shown as a slider. */
public class IntSetting extends Setting<Integer> {
	private final int min;
	private final int max;

	public IntSetting(String name, int defaultValue, int min, int max) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	@Override
	public void set(Integer value) {
		this.value = Math.max(min, Math.min(max, value));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		set(json.getAsInt());
	}
}
