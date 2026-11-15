package com.cyclient.util;

import com.cyclient.CyClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The module grid's card backgrounds, baked once into a single texture and blitted as one image
 * every frame instead of re-drawing ~16 rounded rectangles plus outlines each time. This is both
 * much faster (one draw instead of hundreds) and perfectly smooth, since the corners are
 * anti-aliased once at full resolution.
 *
 * Re-baked only when something that affects the pixels changes (layout, colors, opacity, GUI scale).
 */
public final class GridBackground implements AutoCloseable {
	private static int counter;

	private Identifier id;
	private String signature = "";
	private int widthPx, heightPx, gridWGui, gridHGui;

	/** Ensure the texture matches the given grid; returns true if it's ready to blit. */
	public boolean ensure(int cols, int rows, int cardW, int cardH, int gap, int radius,
						   int cardColor, int outlineColor, int scale) {
		if (cols <= 0 || rows <= 0) return false;
		String sig = cols + "," + rows + "," + cardW + "," + cardH + "," + gap + "," + radius + ","
				+ cardColor + "," + outlineColor + "," + scale;
		if (sig.equals(signature) && id != null) return true;

		try {
			int s = Math.max(1, scale);
			gridWGui = cols * cardW + (cols - 1) * gap;
			gridHGui = rows * (cardH + gap);
			widthPx = gridWGui * s;
			heightPx = gridHGui * s;

			NativeImage img = new NativeImage(widthPx, heightPx, true); // zero-filled = transparent
			int thickness = s; // a 1px GUI outline, scaled to physical pixels
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					int x = c * (cardW + gap) * s;
					int y = r * (cardH + gap) * s;
					paintCard(img, x, y, cardW * s, cardH * s, radius * s, thickness, cardColor, outlineColor);
				}
			}

			release();
			DynamicTexture tex = new DynamicTexture(() -> "cyclient_grid", img);
			Identifier newId = CyClient.id("grid_bg/" + (counter++));
			Minecraft.getInstance().getTextureManager().register(newId, tex);
			id = newId;
			signature = sig;
			return true;
		} catch (Exception e) {
			CyClient.LOGGER.warn("Grid background bake failed: {}", e.getMessage());
			release();
			return false;
		}
	}

	public Identifier id() { return id; }
	public int widthPx() { return widthPx; }
	public int heightPx() { return heightPx; }
	public int gridWGui() { return gridWGui; }
	public int gridHGui() { return gridHGui; }

	private void release() {
		if (id != null) {
			try { Minecraft.getInstance().getTextureManager().release(id); } catch (Exception ignored) {}
			id = null;
		}
	}

	@Override
	public void close() {
		release();
		signature = "";
	}

	// ---------------------------------------------------------------- software rasterizer

	/** Paints one card (fill + outline) with anti-aliased corners into the image. */
	private static void paintCard(NativeImage img, int x, int y, int w, int h, int radius, int t, int fill, int outline) {
		int r = Math.min(radius, Math.min(w, h) / 2);
		for (int py = y; py < y + h; py++) {
			for (int px = x; px < x + w; px++) {
				double outer = coverage(px, py, x, y, x + w, y + h, r);
				if (outer <= 0) continue;
				double inner = coverage(px, py, x + t, y + t, x + w - t, y + h - t, Math.max(0, r - t));
				double ring = outer - inner;
				if (ring < 0) ring = 0;
				int color = over(withAlpha(outline, ring), withAlpha(fill, outer));
				img.setPixelABGR(px, py, swapRedBlue(color));
			}
		}
	}

	/** Coverage (0..1) of the pixel by a rounded rect: 1 inside, anti-aliased at the corners. */
	private static double coverage(int px, int py, int x1, int y1, int x2, int y2, int r) {
		int w = x2 - x1, h = y2 - y1;
		if (w <= 0 || h <= 0) return 0;
		if (px < x1 || px >= x2 || py < y1 || py >= y2) return 0;
		r = Math.min(r, Math.min(w, h) / 2);
		double cx = px + 0.5, cy = py + 0.5;
		double dx = 0, dy = 0;
		if (cx < x1 + r) dx = (x1 + r) - cx; else if (cx > x2 - r) dx = cx - (x2 - r);
		if (cy < y1 + r) dy = (y1 + r) - cy; else if (cy > y2 - r) dy = cy - (y2 - r);
		if (dx <= 0 || dy <= 0) return 1; // straight edge or interior
		double dist = Math.sqrt(dx * dx + dy * dy);
		double c = r + 0.5 - dist;
		return c <= 0 ? 0 : (c >= 1 ? 1 : c);
	}

	private static int withAlpha(int argb, double coverage) {
		if (coverage <= 0) return 0;
		if (coverage > 1) coverage = 1;
		int a = (int) Math.round(((argb >>> 24) & 0xFF) * coverage);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Straight-alpha "src over dst", both ARGB. */
	private static int over(int src, int dst) {
		int sa = (src >>> 24) & 0xFF;
		if (sa == 0) return dst;
		if (sa == 255) return src;
		int da = (dst >>> 24) & 0xFF;
		int outA = sa + da * (255 - sa) / 255;
		if (outA == 0) return 0;
		int sr = (src >> 16) & 0xFF, sg = (src >> 8) & 0xFF, sb = src & 0xFF;
		int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
		int rr = (sr * sa + dr * da * (255 - sa) / 255) / outA;
		int gg = (sg * sa + dg * da * (255 - sa) / 255) / outA;
		int bb = (sb * sa + db * da * (255 - sa) / 255) / outA;
		return (outA << 24) | (rr << 16) | (gg << 8) | bb;
	}

	/** ARGB <-> ABGR (NativeImage stores pixels in ABGR order). */
	private static int swapRedBlue(int c) {
		return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
	}
}
