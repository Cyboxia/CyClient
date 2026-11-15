package com.cyclient.module.modules;

import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Counts hits landed in a row without getting hit back.
 * A hit counts when the target you attacked shows its "hurt" flash.
 */
public class ComboModule extends TextHudModule {
	public final IntSetting resetSeconds = add(new IntSetting("Reset After (s)", 3, 1, 10));
	public final BooleanSetting showZero = add(new BooleanSetting("Show When 0", true));
	public final BooleanSetting colorByCombo = add(new BooleanSetting("Color By Combo", true));

	private int combo;
	private LivingEntity target;
	private int lastTargetHurtTime;
	private int lastPlayerHurtTime;
	private long lastHitAt;

	public ComboModule() {
		super("Combo", "Counts hits you land in a row without getting hit.", false, 4, 300, true);
	}

	/** Called by MultiPlayerGameModeMixin when you attack an entity. */
	public void onAttack(Entity entity) {
		if (!isEnabled()) return;
		if (entity instanceof LivingEntity living) {
			if (living != target) lastTargetHurtTime = living.hurtTime;
			target = living;
		}
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			combo = 0;
			return;
		}

		// You got hit: combo over.
		int playerHurt = mc.player.hurtTime;
		if (playerHurt > lastPlayerHurtTime) combo = 0;
		lastPlayerHurtTime = playerHurt;

		// Your target just started its hurt flash: that's a landed hit.
		if (target != null) {
			int hurt = target.hurtTime;
			if (hurt > lastTargetHurtTime) {
				combo++;
				lastHitAt = System.currentTimeMillis();
			}
			lastTargetHurtTime = hurt;
			if (!target.isAlive()) target = null;
		}

		if (combo > 0 && System.currentTimeMillis() - lastHitAt > resetSeconds.get() * 1000L) combo = 0;
	}

	@Override
	protected void onDisable() {
		combo = 0;
		target = null;
	}

	@Override
	protected String getValue(boolean editor) {
		if (editor) return String.valueOf(Math.max(combo, 3));
		if (combo == 0 && !showZero.get()) return null;
		return String.valueOf(combo);
	}

	@Override
	protected int colorValue(String value, int defaultColor) {
		if (!colorByCombo.get() || combo < 2) return defaultColor;
		return combo >= 5 ? 0xFFFF5555 : combo >= 3 ? 0xFFFFAA00 : 0xFFFFFF55;
	}
}
