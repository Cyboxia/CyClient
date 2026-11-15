package com.cyclient.setting;

import com.google.gson.JsonElement;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** A button in the settings that runs an action (like opening an editor). Not saved. */
public class ActionSetting extends Setting<Consumer<Screen>> {
	private final Supplier<String> buttonLabel;
	private final boolean needsConfirm;

	public ActionSetting(String name, Consumer<Screen> action) {
		this(name, "Open", false, action);
	}

	/**
	 * @param buttonLabel  text on the button
	 * @param needsConfirm if true, the button must be clicked twice (for resets)
	 */
	public ActionSetting(String name, String buttonLabel, boolean needsConfirm, Consumer<Screen> action) {
		this(name, () -> buttonLabel, needsConfirm, action);
	}

	/** Button text that can change, e.g. showing the current choice. */
	public ActionSetting(String name, Supplier<String> buttonLabel, boolean needsConfirm, Consumer<Screen> action) {
		super(name, action);
		this.buttonLabel = buttonLabel;
		this.needsConfirm = needsConfirm;
	}

	public String getButtonLabel() {
		return buttonLabel.get();
	}

	public boolean needsConfirm() {
		return needsConfirm;
	}

	/** @param currentScreen the screen showing the button, so the action can return to it */
	public void run(Screen currentScreen) {
		value.accept(currentScreen);
	}

	@Override
	public void reset() {} // nothing to reset

	@Override
	public JsonElement toJson() {
		return null;
	}

	@Override
	public void fromJson(JsonElement json) {}
}
