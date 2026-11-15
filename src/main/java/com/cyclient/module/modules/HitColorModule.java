package com.cyclient.module.modules;

import com.cyclient.mixin.OverlayTextureAccessor;
import com.cyclient.module.Category;
import com.cyclient.module.Module;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.IntSetting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Changes the tint mobs and players flash when they take damage.
 * Minecraft stores that tint in a tiny 16x16 texture (top half = hurt color);
 * this repaints it whenever the color changes.
 */
public class HitColorModule extends Module {
	private static final int VANILLA_COLOR = 0xB2FF0000; // semi-transparent red

	public final ColorSetting color = add(new ColorSetting("Color", 0xFF0000));
	public final IntSetting opacity = add(new IntSetting("Opacity %", 70, 10, 100));

	private int applied = VANILLA_COLOR;

	public HitColorModule() {
		super("Hit Color", "Change the color entities flash when they get hit.", false);
	}

	@Override
	public Category getCategory() {
		return Category.VISUAL;
	}

	private int wantedColor() {
		int alpha = Math.round(opacity.get() / 100F * 255F);
		return (alpha << 24) | color.get();
	}

	@Override
	public void onTick() {
		int wanted = wantedColor();
		if (wanted != applied) paint(wanted);
	}

	@Override
	protected void onDisable() {
		paint(VANILLA_COLOR);
	}

	private void paint(int argb) {
		if (mc.gameRenderer == null) return;
		DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).cyclient$getTexture();
		NativeImage pixels = texture.getPixels();
		if (pixels == null) return;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, argb);
			}
		}
		texture.upload();
		applied = argb;
	}
}
