package com.cyclient.module.modules;

import com.cyclient.module.Category;
import com.cyclient.module.Module;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.ModeSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * OptiFine/Lunar-style zoom (default key: C, rebindable in Controls).
 * The FOV change itself happens in CameraMixin; sensitivity and scroll in MouseHandlerMixin.
 */
public class ZoomModule extends Module {
	public final ModeSetting mode = add(new ModeSetting("Mode", "Hold", "Hold", "Toggle"));
	public final IntSetting amount = add(new IntSetting("Zoom Amount (x)", 4, 2, 10));
	public final BooleanSetting smooth = add(new BooleanSetting("Smooth Zoom", true));
	public final BooleanSetting scrollToAdjust = add(new BooleanSetting("Scroll To Adjust", true));
	public final BooleanSetting reduceSensitivity = add(new BooleanSetting("Reduce Sensitivity", true));
	public final BooleanSetting cinematic = add(new BooleanSetting("Cinematic Camera", false));

	private KeyMapping key;
	private boolean toggled;
	private float current = 1F;     // the zoom actually applied this frame (1 = no zoom)
	private float scrollMultiplier = 1F;
	private long lastFrame;
	private boolean cinematicApplied;
	private boolean savedSmoothCamera;

	public ZoomModule() {
		super("Zoom", "Zoom in with a key (C by default). Scroll to adjust.", true);
	}

	@Override
	public Category getCategory() {
		return Category.UTILITY;
	}

	public void registerKey(KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cyclient.zoom", InputConstants.KEY_C, category));
	}

	@Override
	public void onTick() {
		if (key == null) return;
		while (key.consumeClick()) {
			if (mode.is("Toggle")) toggled = !toggled;
		}
		if (mode.is("Hold")) toggled = false;

		// Cinematic camera only while zoomed, then restore the player's own setting.
		boolean wantCinematic = cinematic.get() && isZooming();
		if (wantCinematic && !cinematicApplied) {
			savedSmoothCamera = mc.options.smoothCamera;
			mc.options.smoothCamera = true;
			cinematicApplied = true;
		} else if (!wantCinematic && cinematicApplied) {
			mc.options.smoothCamera = savedSmoothCamera;
			cinematicApplied = false;
		}
	}

	@Override
	protected void onDisable() {
		toggled = false;
		if (cinematicApplied) {
			mc.options.smoothCamera = savedSmoothCamera;
			cinematicApplied = false;
		}
	}

	public boolean isZooming() {
		if (!isEnabled() || key == null || mc.player == null || mc.gui.screen() != null) return false;
		return mode.is("Hold") ? key.isDown() : toggled;
	}

	/** Called every frame by CameraMixin. Returns how much to divide the FOV by (1 = normal). */
	public float getFovDivisor() {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
		lastFrame = now;

		boolean zooming = isZooming();
		if (!zooming) scrollMultiplier = 1F; // each zoom starts at the set amount
		float target = zooming ? amount.get() * scrollMultiplier : 1F;

		if (smooth.get()) {
			current += (target - current) * (1F - (float) Math.exp(-dt * 14F));
			if (Math.abs(target - current) < 0.001F) current = target;
		} else {
			current = target;
		}
		return current;
	}

	/** Called by MouseHandlerMixin. */
	public double getSensitivityMultiplier() {
		if (!reduceSensitivity.get() || current <= 1.01F) return 1.0;
		return 1.0 / current;
	}

	/** Called by MouseHandlerMixin. Returns true if the scroll was used for zoom (so the hotbar ignores it). */
	public boolean onScroll(double scrollY) {
		if (!scrollToAdjust.get() || !isZooming() || scrollY == 0) return false;
		scrollMultiplier *= scrollY > 0 ? 1.25F : 0.8F;
		// Keep the total zoom between 1.5x and 50x.
		float total = amount.get() * scrollMultiplier;
		if (total < 1.5F) scrollMultiplier = 1.5F / amount.get();
		if (total > 50F) scrollMultiplier = 50F / amount.get();
		return true;
	}
}
