package com.cyclient.util;

import com.cyclient.CyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared cache of custom fonts used by the menu and HUD widgets.
 * Each (file, size, offset, GUI scale) combination is loaded once and reused every frame.
 * An empty path means "Minecraft's font".
 */
public final class Fonts {
	private static final int MAX_LOADED = 24;

	private record Key(String path, int size, int offset, int scale) {}

	/** Access-ordered so the least recently used font is evicted first. */
	private static final LinkedHashMap<Key, FontLoader.Loaded> LOADED = new LinkedHashMap<>(16, 0.75F, true);
	private static final Map<Key, String> ERRORS = new HashMap<>();

	private Fonts() {}

	/**
	 * How finely to rasterize glyphs relative to GUI pixels. The atlas is rendered at
	 * size * oversample, so going well above the GUI scale supersamples the text and keeps
	 * edges smooth even at a low GUI scale (where scale alone would give none). Capped so the
	 * glyph atlas stays a sane size.
	 */
	private static int oversampleFor(int guiScale) {
		return Math.min(8, Math.max(2, guiScale) * 2);
	}

	// Bricolage Grotesque bundled in the mod, used as the default menu font (matches the launcher).
	private static byte[] bundledBytes;
	private static boolean bundledMissing;
	private static final LinkedHashMap<Key, FontLoader.Loaded> BUNDLED = new LinkedHashMap<>(8, 0.75F, true);

	public static Font getBundled(int size, int offset) {
		Minecraft mc = Minecraft.getInstance();
		if (bundledMissing) return mc.font;
		if (bundledBytes == null) {
			try (java.io.InputStream in = Fonts.class.getResourceAsStream("/assets/cyclient/fonts/bricolage.ttf")) {
				if (in == null) { bundledMissing = true; return mc.font; }
				bundledBytes = in.readAllBytes();
			} catch (Exception e) {
				bundledMissing = true;
				CyClient.LOGGER.warn("Bundled font missing: {}", e.getMessage());
				return mc.font;
			}
		}
		int scale = Math.max(1, mc.getWindow().getGuiScale());
		Key key = new Key("*bricolage", size, offset, scale);
		FontLoader.Loaded loaded = BUNDLED.get(key);
		if (loaded != null) return loaded.font();
		try {
			loaded = FontLoader.load(bundledBytes, size, oversampleFor(scale), offset);
			BUNDLED.put(key, loaded);
			while (BUNDLED.size() > 8) {
				Iterator<Map.Entry<Key, FontLoader.Loaded>> it = BUNDLED.entrySet().iterator();
				it.next().getValue().close(); it.remove();
			}
			return loaded.font();
		} catch (Exception e) {
			bundledMissing = true;
			CyClient.LOGGER.warn("Bundled font load failed: {}", e.getMessage());
			return mc.font;
		}
	}

	/** The font for this file at this size, or Minecraft's font if the path is empty or the file can't be used. */
	public static Font get(String path, int size, int offset) {
		Minecraft mc = Minecraft.getInstance();
		if (path == null || path.isEmpty()) return mc.font;

		Key key = new Key(path, size, offset, Math.max(1, mc.getWindow().getGuiScale()));
		FontLoader.Loaded loaded = LOADED.get(key);
		if (loaded != null) return loaded.font();
		if (ERRORS.containsKey(key)) return mc.font; // failed before: don't retry every frame

		try {
			loaded = FontLoader.load(Files.readAllBytes(Path.of(path)), size, oversampleFor(key.scale()), offset);
			LOADED.put(key, loaded);
			evictOld();
			return loaded.font();
		} catch (Exception e) {
			CyClient.LOGGER.warn("Could not load font {}: {}", path, e.getMessage());
			ERRORS.put(key, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			return mc.font;
		}
	}

	/** Why this font couldn't be used, or null if it's fine (or not loaded yet). */
	public static String getError(String path, int size, int offset) {
		if (path == null || path.isEmpty()) return null;
		int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		return ERRORS.get(new Key(path, size, offset, scale));
	}

	private static void evictOld() {
		Iterator<Map.Entry<Key, FontLoader.Loaded>> it = LOADED.entrySet().iterator();
		while (LOADED.size() > MAX_LOADED && it.hasNext()) {
			it.next().getValue().close();
			it.remove();
		}
	}
}
