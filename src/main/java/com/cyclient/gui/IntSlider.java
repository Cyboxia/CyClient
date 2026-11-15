package com.cyclient.gui;

import com.cyclient.setting.IntSetting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A vanilla-style slider bound to an IntSetting. */
public class IntSlider extends AbstractSliderButton {
	private final IntSetting setting;

	public IntSlider(int x, int y, int width, int height, IntSetting setting) {
		super(x, y, width, height, Component.empty(), toSlider(setting));
		this.setting = setting;
		updateMessage();
	}

	private static double toSlider(IntSetting setting) {
		int range = setting.getMax() - setting.getMin();
		return range == 0 ? 0 : (setting.get() - setting.getMin()) / (double) range;
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(setting.getName() + ": " + setting.get()));
	}

	@Override
	protected void applyValue() {
		int range = setting.getMax() - setting.getMin();
		setting.set(setting.getMin() + (int) Math.round(this.value * range));
	}
}
