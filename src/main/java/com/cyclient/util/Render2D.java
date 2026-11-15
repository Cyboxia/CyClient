package com.cyclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing helpers for the menu.
 *
 * Rounded corners use a cheap per-row fill: each row is one solid rectangle plus a single
 * edge pixel blended by coverage, so the corners are lightly anti-aliased without the
 * thousands of per-pixel draws that would wreck the framerate.
 */
public final class Render2D {
	private Render2D() {}

	/** A filled rectangle with lightly anti-aliased rounded corners of radius r. */
	public static void roundedRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int color) {
		int w = x2 - x1, h = y2 - y1;
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}
		int a = (color >>> 24) & 0xFF;
		int rgb = color & 0x00FFFFFF;
		g.fill(x1, y1 + r, x2, y2 - r, color); // middle band (full width)
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			double ext = Math.sqrt(r * r - dy * dy);
			double edge = r - ext;           // where the curve crosses this row
			int inset = (int) Math.floor(edge);
			int topY = y1 + i, botY = y2 - 1 - i;
			// Solid interior of the row (both corners share it).
			g.fill(x1 + inset + 1, topY, x2 - inset - 1, topY + 1, color);
			g.fill(x1 + inset + 1, botY, x2 - inset - 1, botY + 1, color);
			// One coverage-blended edge pixel on each side, top and bottom.
			int ea = (int) Math.round(a * (1.0 - (edge - inset)));
			if (ea > 0) {
				int ec = (ea << 24) | rgb;
				g.fill(x1 + inset, topY, x1 + inset + 1, topY + 1, ec);
				g.fill(x2 - inset - 1, topY, x2 - inset, topY + 1, ec);
				g.fill(x1 + inset, botY, x1 + inset + 1, botY + 1, ec);
				g.fill(x2 - inset - 1, botY, x2 - inset, botY + 1, ec);
			}
		}
	}

	/** A rounded border of the given thickness, following the same corners as roundedRect. */
	public static void roundedOutline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int thickness, int color) {
		int w = x2 - x1, h = y2 - y1;
		if (w <= 0 || h <= 0 || thickness <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x1, y1, x2, y1 + thickness, color);
			g.fill(x1, y2 - thickness, x2, y2, color);
			g.fill(x1, y1 + thickness, x1 + thickness, y2 - thickness, color);
			g.fill(x2 - thickness, y1 + thickness, x2, y2 - thickness, color);
			return;
		}
		int a = (color >>> 24) & 0xFF;
		int rgb = color & 0x00FFFFFF;
		// Straight edges.
		g.fill(x1 + r, y1, x2 - r, y1 + thickness, color);
		g.fill(x1 + r, y2 - thickness, x2 - r, y2, color);
		g.fill(x1, y1 + r, x1 + thickness, y2 - r, color);
		g.fill(x2 - thickness, y1 + r, x2, y2 - r, color);
		// Corner ring: one coverage-blended pixel per row on each edge.
		double ro = r, ri = r - thickness;
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			double eo = Math.sqrt(Math.max(0, ro * ro - dy * dy));
			double ei = Math.sqrt(Math.max(0, ri * ri - dy * dy));
			int topY = y1 + i, botY = y2 - 1 - i;
			paintRingRow(g, x1, x2, topY, r, eo, ei, a, rgb);
			paintRingRow(g, x1, x2, botY, r, eo, ei, a, rgb);
		}
	}

	private static void paintRingRow(GuiGraphicsExtractor g, int x1, int x2, int y, int r, double eo, double ei, int a, int rgb) {
		// The ring occupies the band between the inner and outer curve on this row.
		int outer = (int) Math.round(r - eo);
		int inner = (int) Math.round(r - ei);
		if (inner <= outer) inner = outer + 1;
		g.fill(x1 + outer, y, x1 + inner, y + 1, (a << 24) | rgb);         // left edge
		g.fill(x2 - inner, y, x2 - outer, y + 1, (a << 24) | rgb);         // right edge
	}

	/** Makes an ARGB color lighter by the given amount (0-255) per channel. */
	public static int lighten(int color, int amount) {
		int a = (color >>> 24) & 0xFF;
		int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
		int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
		int b = Math.min(255, (color & 0xFF) + amount);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Blends two ARGB colors: t = 0 gives a, t = 1 gives b. */
	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int oa = Math.round(aa + (ba - aa) * t);
		int or = Math.round(ar + (br - ar) * t);
		int og = Math.round(ag + (bg - ag) * t);
		int ob = Math.round(ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}
}
