package com.cyclient.setting;

import com.google.gson.JsonElement;

/** One adjustable option on a module (a toggle, a number, a color, or a choice from a list). */
public abstract class Setting<T> {
	private final String name;
	protected final T defaultValue;
	protected T value;
	private boolean hiddenFromMenu;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	/** Saved to the config but not shown as a row in the menu (it has its own editor). */
	public boolean isHiddenFromMenu() {
		return hiddenFromMenu;
	}

	public void hideFromMenu() {
		hiddenFromMenu = true;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	/** Puts the setting back to its default value. */
	public void reset() {
		set(defaultValue);
	}

	/** @return the value to save, or null if this setting has nothing to save */
	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);
}
