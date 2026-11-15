package com.cyclient.mixin;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ComboModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combo Counter: notices when you attack something. */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void cyclient$onAttack(Player player, Entity target, CallbackInfo ci) {
		ModuleManager.get(ComboModule.class).onAttack(target);
	}
}
