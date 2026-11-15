package com.cyclient.mixin;

import com.cyclient.module.modules.NickHiderModule;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Nick Hider: every string Minecraft draws or measures passes through StringDecomposer,
 * so swapping the name here covers chat, tab list, nametags, scoreboard and menus.
 */
@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {
	@ModifyVariable(method = "iterate", at = @At("HEAD"), argsOnly = true)
	private static String cyclient$nickPlain(String text) {
		return NickHiderModule.apply(text, false);
	}

	@ModifyVariable(method = "iterateBackwards", at = @At("HEAD"), argsOnly = true)
	private static String cyclient$nickBackwards(String text) {
		return NickHiderModule.apply(text, false);
	}

	@ModifyVariable(
			method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			at = @At("HEAD"), argsOnly = true)
	private static String cyclient$nickFormatted(String text) {
		return NickHiderModule.apply(text, true);
	}
}
