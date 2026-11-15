package com.cyclient.mixin;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.FullbrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright: after Minecraft works out this frame's lighting, apply a night-vision
 * style brightness boost (client-side only, no real potion effect).
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void cyclient$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		FullbrightModule fullbright = ModuleManager.get(FullbrightModule.class);
		if (!fullbright.isEnabled()) return;
		float s = fullbright.strength.get() / 100F;
		// In the lightmap shader the base light is max(AmbientColor, nightVision), so lifting the
		// ambient floor toward white is what actually makes dark areas fully bright. At 100% this is
		// true fullbright; night vision alone could only reach the night-vision colour's brightness.
		org.joml.Vector3fc a = state.ambientColor;
		state.ambientColor = new org.joml.Vector3f(Math.max(a.x(), s), Math.max(a.y(), s), Math.max(a.z(), s));
		state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, s);
		state.brightness = Math.max(state.brightness, s);
		if (fullbright.removeDarkness.get()) state.darknessEffectScale = 0F;
	}
}
