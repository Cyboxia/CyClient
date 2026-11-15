package com.cyclient.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hit Color: gives access to the small texture that holds the red "hurt" tint. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
	@Accessor("texture")
	DynamicTexture cyclient$getTexture();
}
