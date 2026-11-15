package com.cyclient.module.modules;

import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.util.CpsTracker;

public class CpsModule extends TextHudModule {
	public final BooleanSetting showRight = add(new BooleanSetting("Show Right Click", true));
	public final ModeSetting separator = add(new ModeSetting("Separator", " | ", " | ", " / ", " - ", " : "));

	public CpsModule() {
		super("CPS", "Shows your left and right clicks per second.", true, 4, 22, true);
	}

	@Override
	protected String getValue(boolean editor) {
		String left = String.valueOf(CpsTracker.getLeftCps());
		if (!showRight.get()) return left;
		return left + separator.get() + CpsTracker.getRightCps();
	}
}
