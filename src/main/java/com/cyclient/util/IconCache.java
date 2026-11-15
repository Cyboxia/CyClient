package com.cyclient.util;

import com.cyclient.CyClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Crisp icon rendering.
 *
 * Minecraft's GUI blit samples textures with nearest-neighbour, so an icon PNG drawn at a size
 * different from its native resolution looks jagged. We resample each icon once (high quality) to
 * the exact pixel size it will be drawn at, upload it as a dynamic texture, and draw that 1:1 so
 * the sampling is exact. Rebuilt when the GUI scale changes; textures are freed on close.
 */
public final class IconCache implements AutoCloseable {
	private static int counter;
	private static final String DYNAMIC_PREFIX = "scaled_icons/";

	private final Map<String, Identifier> cache = new HashMap<>();
	private int scale = -1;

	/**
	 * An Identifier for {@code source} resampled to sizePx x sizePx, or {@code source} itself if it
	 * couldn't be resampled (so the caller still draws something).
	 */
	public Identifier scaled(Identifier source, int sizePx) {
		int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		if (gs != scale) { clear(); scale = gs; }
		if (sizePx <= 0) return source;

		String key = source.toString() + "@" + sizePx;
		Identifier cached = cache.get(key);
		if (cached != null) return cached;

		Identifier made = build(source, sizePx);
		cache.put(key, made != null ? made : source); // remember the fallback too, so we don't retry each frame
		return made != null ? made : source;
	}

	private Identifier build(Identifier source, int sizePx) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(source);
			if (res.isEmpty()) return null;
			NativeImage dst;
			try (InputStream in = res.get().open()) {
				NativeImage src = NativeImage.read(in);
				dst = new NativeImage(sizePx, sizePx, true);
				src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), dst);
				src.close();
			}
			DynamicTexture tex = new DynamicTexture(() -> "cyclient_icon", dst);
			Identifier id = CyClient.id(DYNAMIC_PREFIX + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, tex);
			return id;
		} catch (Exception e) {
			CyClient.LOGGER.warn("Icon resample failed for {}: {}", source, e.getMessage());
			return null;
		}
	}

	private void clear() {
		for (Identifier id : cache.values()) {
			if (id.getPath().startsWith(DYNAMIC_PREFIX)) {
				try { Minecraft.getInstance().getTextureManager().release(id); } catch (Exception ignored) {}
			}
		}
		cache.clear();
	}

	@Override
	public void close() {
		clear();
	}
}
