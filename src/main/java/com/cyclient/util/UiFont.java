package com.cyclient.util;

import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ClientSettingsModule;
import net.minecraft.client.gui.Font;

/** The fonts chosen in Client Settings: one for the menu, one for HUD widgets. */
public final class UiFont {
	private UiFont() {}

	private static ClientSettingsModule s() {
		return ModuleManager.get(ClientSettingsModule.class);
	}

	/** Font for the Cy Client menu. */
	public static Font get() {
		String path = s().uiFontPath.get();
		if (path == null || path.isEmpty()) return Fonts.getBundled(s().uiFontSize.get(), s().uiFontOffset.get());
		return Fonts.get(path, s().uiFontSize.get(), s().uiFontOffset.get());
	}

	public static String getError() {
		return Fonts.getError(s().uiFontPath.get(), s().uiFontSize.get(), s().uiFontOffset.get());
	}

	/** Font for HUD widgets. path = a widget's own font, or "" to use the global HUD font. */
	public static Font hud(String widgetPath) {
		String path = widgetPath == null || widgetPath.isEmpty() ? s().hudFontPath.get() : widgetPath;
		return Fonts.get(path, s().hudFontSize.get(), s().hudFontOffset.get());
	}

	public static String hudError(String widgetPath) {
		String path = widgetPath == null || widgetPath.isEmpty() ? s().hudFontPath.get() : widgetPath;
		return Fonts.getError(path, s().hudFontSize.get(), s().hudFontOffset.get());
	}
}
