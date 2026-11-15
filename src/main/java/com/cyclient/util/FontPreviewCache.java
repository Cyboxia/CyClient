package com.cyclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads fonts for the font picker's live previews without stalling the game:
 * - only fonts that are on screen are requested,
 * - files are read from disk on a background thread,
 * - building fonts (which must happen on the render thread) is limited to a few ms per frame,
 * - at most MAX_LOADED fonts stay in memory; the least recently shown are freed first.
 */
public final class FontPreviewCache implements AutoCloseable {
	private static final int MAX_LOADED = 48;
	private static final long FRAME_BUDGET_NANOS = 3_000_000L; // 3 ms of font building per frame
	private static final long MAX_FILE_BYTES = 25L * 1024 * 1024; // skip huge fonts (mostly CJK)
	private static final float PREVIEW_SIZE = 9F;

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Cy Client font preview reader");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** Access-ordered, so iteration starts with the least recently used font. */
	private final LinkedHashMap<String, FontLoader.Loaded> loaded = new LinkedHashMap<>(64, 0.75F, true);
	private final Map<String, CompletableFuture<byte[]>> reading = new HashMap<>();
	private final Set<String> failed = new HashSet<>();
	private final Set<String> requestedThisFrame = new HashSet<>();
	private long frameStart;
	private int scale = -1;

	/** Call once at the start of each frame, before any {@link #get} calls. */
	public void beginFrame() {
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		if (guiScale != scale) { // fonts are rasterized for a GUI scale, so rebuild if it changes
			closeLoaded();
			scale = guiScale;
		}
		requestedThisFrame.clear();
		frameStart = System.nanoTime();
	}

	/** Call once at the end of each frame: cancels reads for fonts that scrolled out of view. */
	public void endFrame() {
		Iterator<Map.Entry<String, CompletableFuture<byte[]>>> it = reading.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, CompletableFuture<byte[]>> e = it.next();
			if (!requestedThisFrame.contains(e.getKey()) && !e.getValue().isDone()) {
				e.getValue().cancel(false);
				it.remove();
			}
		}
	}

	/** The preview font for a file, or null while it's still loading (or if it can't be used). */
	public Font get(File file) {
		String key = file.getPath();
		requestedThisFrame.add(key);

		FontLoader.Loaded font = loaded.get(key);
		if (font != null) return font.font();
		if (failed.contains(key)) return null;

		CompletableFuture<byte[]> read = reading.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> readSmall(file), IO));
		if (!read.isDone() || System.nanoTime() - frameStart > FRAME_BUDGET_NANOS) return null;

		reading.remove(key);
		try {
			byte[] bytes = read.join();
			if (bytes == null) {
				failed.add(key);
				return null;
			}
			font = FontLoader.load(bytes, PREVIEW_SIZE, scale, 0F);
			loaded.put(key, font);
			evictOld();
			return font.font();
		} catch (Exception e) {
			failed.add(key); // e.g. CFF-outline .otf fonts
			return null;
		}
	}

	/** True if this font can't be previewed (unsupported format or too large). */
	public boolean isUnsupported(File file) {
		return failed.contains(file.getPath());
	}

	private void evictOld() {
		Iterator<Map.Entry<String, FontLoader.Loaded>> it = loaded.entrySet().iterator();
		while (loaded.size() > MAX_LOADED && it.hasNext()) {
			Map.Entry<String, FontLoader.Loaded> oldest = it.next();
			if (requestedThisFrame.contains(oldest.getKey())) continue; // never free what's on screen
			oldest.getValue().close();
			it.remove();
		}
	}

	private static byte[] readSmall(File file) {
		try {
			if (file.length() > MAX_FILE_BYTES) return null;
			return Files.readAllBytes(file.toPath());
		} catch (Exception e) {
			return null;
		}
	}

	private void closeLoaded() {
		for (FontLoader.Loaded f : loaded.values()) f.close();
		loaded.clear();
	}

	@Override
	public void close() {
		closeLoaded();
		for (CompletableFuture<byte[]> f : reading.values()) f.cancel(false);
		reading.clear();
	}
}
