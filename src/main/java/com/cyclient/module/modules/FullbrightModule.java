package com.cyclient.module.modules;

import com.cyclient.module.Category;
import com.cyclient.module.Module;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;

/**
 * Makes dark areas fully visible, like having Night Vision (client-side only).
 * The lighting change happens in LightmapRenderStateExtractorMixin.
 */
public class FullbrightModule extends Module {
	public final IntSetting strength = add(new IntSetting("Strength %", 100, 10, 100));
	public final BooleanSetting removeDarkness = add(new BooleanSetting("Remove Darkness Effect", true));

	public FullbrightModule() {
		super("Fullbright", "See clearly in the dark, like Night Vision.", false);
	}

	@Override
	public Category getCategory() {
		return Category.VISUAL;
	}
}
