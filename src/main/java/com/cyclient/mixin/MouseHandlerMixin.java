package com.cyclient.mixin;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	/** Zoom: lower mouse sensitivity while zoomed in, so aiming stays controllable. */
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Double;doubleValue()D", ordinal = 0))
	private double cyclient$zoomSensitivity(Double sensitivity) {
		return sensitivity * ModuleManager.get(ZoomModule.class).getSensitivityMultiplier();
	}

	/** Zoom: while zooming, the scroll wheel changes the zoom level instead of the hotbar slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void cyclient$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ModuleManager.get(ZoomModule.class).onScroll(yOffset)) ci.cancel();
	}
}
