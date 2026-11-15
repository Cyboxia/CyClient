package com.cyclient.module.modules;

import com.cyclient.module.Module;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.StringSetting;
import net.minecraft.client.Minecraft;

/**
 * Replaces your real username with a nickname everywhere text is drawn
 * (chat, tab list, nametags, scoreboard...). Client-side only: nobody else sees it.
 * The replacement happens in StringDecomposerMixin.
 */
public class NickHiderModule extends Module {
	/** Set as soon as the module is created, so text drawn very early never crashes. */
	private static NickHiderModule instance;

	public final StringSetting nickname = add(new StringSetting("Nickname", "You", 32));
	public final BooleanSetting colorCodes = add(new BooleanSetting("Use & Color Codes", false));

	public NickHiderModule() {
		super("Nick Hider", "Shows a nickname instead of your real name (only you see it).", false);
		instance = this;
	}

	/**
	 * Called for every piece of text Minecraft draws.
	 * @param formatted true if this text supports color codes (§)
	 */
	public static String apply(String text, boolean formatted) {
		NickHiderModule nick = instance;
		if (nick == null || text == null || !nick.isEnabled()) return text;

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getUser() == null) return text;
		String realName = mc.getUser().getName();
		if (realName == null || realName.isEmpty() || !text.contains(realName)) return text;

		String replacement = nick.nickname.get();
		if (replacement.isEmpty()) return text;
		if (nick.colorCodes.get()) {
			// "&c" -> "§c" where colors work; strip the codes where they would show as raw symbols.
			replacement = formatted
					? replacement.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1") + "§r"
					: replacement.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
		}
		return text.replace(realName, replacement);
	}
}
