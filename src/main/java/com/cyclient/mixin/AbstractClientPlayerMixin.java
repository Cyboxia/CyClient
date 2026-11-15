package com.cyclient.mixin;

import com.cyclient.util.OfflineSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows the launcher's offline skin on your own player when you have no real (signed-in) skin,
 * so a custom skin set in Cy Launcher carries into offline / cracked play. A genuine Mojang skin
 * is left untouched: we only replace the default skin.
 */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

	private static boolean cyclient$logged;

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void cyclient$offlineSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != self) return; // only our own player

		PlayerSkin current = cir.getReturnValue();
		if (current == null || current.body() == null) return;

		// Only override when still on a default skin (offline or not yet set), so a real skin stays.
		PlayerSkin def = DefaultPlayerSkin.get(self.getUUID());
		if (def.body() != null && !current.body().texturePath().equals(def.body().texturePath())) return;

		Identifier tex = OfflineSkin.texture();
		if (tex == null) return;

		if (!cyclient$logged) { cyclient$logged = true; com.cyclient.CyClient.LOGGER.info("Offline skin override active for {}", self.getName().getString()); }
		ClientAsset.Texture body = new ClientAsset.DownloadedTexture(tex, "");
		cir.setReturnValue(PlayerSkin.insecure(body, current.cape(), current.elytra(), PlayerModelType.WIDE));
	}
}
