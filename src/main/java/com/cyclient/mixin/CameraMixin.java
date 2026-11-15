package com.cyclient.mixin;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: divides the world field of view while zooming. The hand keeps its normal FOV. */
@Mixin(Camera.class)
public class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void cyclient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float divisor = ModuleManager.get(ZoomModule.class).getFovDivisor();
		if (divisor != 1F) cir.setReturnValue(cir.getReturnValue() / divisor);
	}
}
