package com.cyclient.util;

import com.cyclient.CyClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Loads the launcher's offline skin (saved by Cy Launcher at %APPDATA%/.cyclient/offline-skin.png)
 * and registers it as a game texture, so offline / unsigned players can show a custom skin in-game.
 * Loaded once per session; set a skin in the launcher before launching.
 */
public final class OfflineSkin {
	private static boolean tried;
	private static Identifier id;

	/** The registered skin texture id, or null if there is no offline skin to use. */
	public static Identifier texture() {
		if (tried) return id;
		tried = true;
		try {
			NativeImage img = load();
			if (img == null) return null;
			Identifier newId = CyClient.id("offline_skin");
			Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(() -> "cyclient_offline_skin", img));
			id = newId;
		} catch (Exception e) {
			CyClient.LOGGER.warn("Offline skin load failed: {}", e.getMessage());
		}
		return id;
	}

	/** The user's saved offline skin if present, otherwise the default skin bundled in the mod. */
	private static NativeImage load() throws Exception {
		File f = file();
		if (f != null && f.isFile()) {
			try (InputStream in = new FileInputStream(f)) {
				CyClient.LOGGER.info("Using offline skin {}", f);
				return NativeImage.read(in);
			}
		}
		try (InputStream in = OfflineSkin.class.getResourceAsStream("/cyclient-default-skin.png")) {
			if (in != null) {
				CyClient.LOGGER.info("Using bundled default skin");
				return NativeImage.read(in);
			}
		}
		CyClient.LOGGER.info("No offline skin available");
		return null;
	}

	private static File file() {
		String appdata = System.getenv("APPDATA");
		if (appdata == null || appdata.isEmpty()) return null;
		return new File(appdata, ".cyclient" + File.separator + "offline-skin.png");
	}

	private OfflineSkin() {}
}
