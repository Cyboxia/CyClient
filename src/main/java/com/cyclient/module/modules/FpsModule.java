package com.cyclient.module.modules;

import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;

public class FpsModule extends TextHudModule {
	public final BooleanSetting colorByValue = add(new BooleanSetting("Color By Value", false));

	public FpsModule() {
		super("FPS", "Shows your frames per second.", true, 4, 4, true);
	}

	@Override
	protected String getValue(boolean editor) {
		return String.valueOf(mc.getFps());
	}

	@Override
	protected int colorValue(String value, int defaultColor) {
		if (!colorByValue.get()) return defaultColor;
		int fps = mc.getFps();
		return trafficLight(fps >= 60, fps >= 30);
	}
}
