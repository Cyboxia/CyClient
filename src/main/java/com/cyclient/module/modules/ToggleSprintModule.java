package com.cyclient.module.modules;

import com.cyclient.module.Category;
import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ModeSetting;

/**
 * Keeps you sprinting without holding the sprint key.
 * Same result as vanilla's "Sprint: Toggle" option, plus an on-screen indicator.
 */
public class ToggleSprintModule extends TextHudModule {
	public final BooleanSetting showIndicator = add(new BooleanSetting("Show Indicator", true));
	public final ModeSetting indicatorText = add(new ModeSetting("Indicator Text",
			"[Sprinting (Toggled)]", "[Sprinting (Toggled)]", "Sprinting", "Sprint", "Toggled"));
	public final BooleanSetting showWhenIdle = add(new BooleanSetting("Show When Not Sprinting", false));

	public ToggleSprintModule() {
		super("Toggle Sprint", "Always sprint without holding the key.", true, 4, 94, false);
	}

	@Override
	public Category getCategory() {
		return Category.UTILITY; // it has a HUD indicator, but it's a movement feature
	}

	@Override
	public void onTick() {
		if (mc.player != null && mc.gui.screen() == null) {
			mc.options.keySprint.setDown(true);
		}
	}

	@Override
	protected void onDisable() {
		mc.options.keySprint.setDown(false);
	}

	@Override
	protected String getValue(boolean editor) {
		if (!showIndicator.get()) return null;
		if (editor || mc.player == null) return indicatorText.get();
		if (mc.player.isSprinting()) return indicatorText.get();
		return showWhenIdle.get() ? "[Walking]" : null;
	}
}
