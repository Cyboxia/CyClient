package com.cyclient.module.modules;

import com.cyclient.gui.CrosshairEditorScreen;
import com.cyclient.module.Category;
import com.cyclient.module.Module;
import com.cyclient.setting.ActionSetting;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.setting.PixelGridSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Replaces the vanilla crosshair with a customizable one.
 * All sizes are in GUI pixels (they scale with your GUI Scale setting).
 */
public class CrosshairModule extends Module {
	private static final int OUTLINE_COLOR_RGB = 0x000000;
	private static final int TARGET_COLOR_RGB = 0xFF5555;
	private static final int GRID_SIZE = 15;

	public final ModeSetting style = add(new ModeSetting("Style", "Cross",
			"Cross", "Cross + Dot", "Dot", "Circle", "T-Shape", "Custom"));
	public final ActionSetting drawCustom = add(new ActionSetting("Draw Custom Crosshair...",
			screen -> mc.gui.setScreen(new CrosshairEditorScreen(screen, this))));
	public final IntSetting size = add(new IntSetting("Size", 4, 1, 20));
	public final IntSetting gap = add(new IntSetting("Gap", 2, 0, 15));
	public final IntSetting thickness = add(new IntSetting("Thickness", 1, 1, 6));
	public final BooleanSetting outline = add(new BooleanSetting("Outline", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final IntSetting opacity = add(new IntSetting("Opacity", 100, 10, 100));
	public final BooleanSetting targetColor = add(new BooleanSetting("Red On Target", true));
	public final BooleanSetting thirdPerson = add(new BooleanSetting("Show In Third Person", false));
	public final BooleanSetting cooldown = add(new BooleanSetting("Cooldown Indicator", true));
	public final PixelGridSetting pixels = add(new PixelGridSetting("Pixels", GRID_SIZE, defaultPixels()));

	public CrosshairModule() {
		super("Crosshair", "Replaces the vanilla crosshair with a custom one.", true);
	}

	@Override
	public Category getCategory() {
		return Category.VISUAL;
	}

	/** Starting drawing: a plus with a small gap in the middle. */
	private static boolean[] defaultPixels() {
		boolean[] grid = new boolean[GRID_SIZE * GRID_SIZE];
		int c = GRID_SIZE / 2;
		for (int d = 2; d <= 5; d++) {
			grid[c * GRID_SIZE + (c - d)] = true;
			grid[c * GRID_SIZE + (c + d)] = true;
			grid[(c - d) * GRID_SIZE + c] = true;
			grid[(c + d) * GRID_SIZE + c] = true;
		}
		return grid;
	}

	/** Called by the HUD every frame in place of the vanilla crosshair. */
	public void renderInGame(GuiGraphicsExtractor graphics) {
		if (!thirdPerson.get() && !mc.options.getCameraType().isFirstPerson()) return; // vanilla hides it in third person
		int cx = mc.getWindow().getGuiScaledWidth() / 2;
		int cy = mc.getWindow().getGuiScaledHeight() / 2;
		boolean onTarget = targetColor.get() && mc.crosshairPickEntity != null;
		draw(graphics, cx, cy, onTarget);

		if (cooldown.get() && mc.player != null) {
			float charge = mc.player.getAttackStrengthScale(0.0F);
			if (charge < 1.0F) drawCooldown(graphics, cx, cy, charge);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void renderPreview(GuiGraphicsExtractor graphics, int centerX, int centerY) {
		int box = 40;
		graphics.fill(centerX - box, centerY - box, centerX + box, centerY + box, 0xFF6A8CAF); // sky-colored backdrop
		graphics.fill(centerX - box, centerY + 20, centerX + box, centerY + box, 0xFF5B8C32); // grass-colored ground
		draw(graphics, centerX, centerY, false);
		if (cooldown.get()) drawCooldown(graphics, centerX, centerY, 0.6F); // sample: 60% charged
	}

	/** The crosshair color as 0xAARRGGBB. */
	public int getColor(boolean onTarget) {
		int alpha = Math.round(opacity.get() / 100f * 255f);
		int rgb = onTarget ? TARGET_COLOR_RGB : color.get();
		return (alpha << 24) | rgb;
	}

	private int getOutlineColor() {
		int alpha = Math.round(opacity.get() / 100f * 255f);
		return (alpha << 24) | OUTLINE_COLOR_RGB;
	}

	/** Attack cooldown bar under the crosshair (replaces vanilla's attack indicator). */
	private void drawCooldown(GuiGraphicsExtractor graphics, int cx, int cy, float charge) {
		int width = 16;
		int x1 = cx - width / 2;
		int y1 = cy + 10;
		int filled = Math.round(width * Math.max(0F, Math.min(1F, charge)));
		graphics.fill(x1 - 1, y1 - 1, x1 + width + 1, y1 + 3, 0xA0000000);   // dark border/background
		graphics.fill(x1, y1, x1 + filled, y1 + 2, getColor(false) | 0xFF000000); // progress in crosshair color
	}

	private void draw(GuiGraphicsExtractor graphics, int cx, int cy, boolean onTarget) {
		int color = getColor(onTarget);
		int outlineColor = getOutlineColor();

		if (style.is("Custom")) {
			drawCustom(graphics, cx, cy, color, outlineColor);
			return;
		}

		int t = thickness.get();
		int s = size.get();
		int g = gap.get();

		// The center block, t x t pixels, around the exact screen center.
		int left = cx - t / 2;
		int top = cy - t / 2;
		int right = left + t;
		int bottom = top + t;

		if (style.is("Circle")) {
			drawCircle(graphics, cx, cy, g + s, t, color, outlineColor);
			return;
		}

		boolean arms = !style.is("Dot");
		boolean dot = style.is("Dot") || style.is("Cross + Dot");
		boolean topArm = !style.is("T-Shape");

		// Draw all outlines first so they never cover the colored parts.
		if (outline.get()) {
			if (arms) {
				rectOutline(graphics, left - g - s, top, left - g, bottom, outlineColor);
				rectOutline(graphics, right + g, top, right + g + s, bottom, outlineColor);
				rectOutline(graphics, left, bottom + g, right, bottom + g + s, outlineColor);
				if (topArm) rectOutline(graphics, left, top - g - s, right, top - g, outlineColor);
			}
			if (dot) rectOutline(graphics, left, top, right, bottom, outlineColor);
		}

		if (arms) {
			graphics.fill(left - g - s, top, left - g, bottom, color);       // left arm
			graphics.fill(right + g, top, right + g + s, bottom, color);     // right arm
			graphics.fill(left, bottom + g, right, bottom + g + s, color);   // bottom arm
			if (topArm) graphics.fill(left, top - g - s, right, top - g, color); // top arm
		}
		if (dot) graphics.fill(left, top, right, bottom, color);
	}

	/** Draws the hand-drawn pixel crosshair, one GUI pixel per grid cell, centered on the screen. */
	private void drawCustom(GuiGraphicsExtractor graphics, int cx, int cy, int color, int outlineColor) {
		int n = pixels.getSize();
		int originX = cx - n / 2;
		int originY = cy - n / 2;

		if (outline.get()) {
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					if (pixels.get(x, y)) {
						int px = originX + x;
						int py = originY + y;
						graphics.fill(px - 1, py - 1, px + 2, py + 2, outlineColor);
					}
				}
			}
		}
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (pixels.get(x, y)) {
					int px = originX + x;
					int py = originY + y;
					graphics.fill(px, py, px + 1, py + 1, color);
				}
			}
		}
	}

	private static void rectOutline(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, color);
	}

	/** A ring drawn pixel by pixel: every pixel whose distance from the center is within the ring. */
	private void drawCircle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int t, int color, int outlineColor) {
		double outer = radius + 0.5;
		double inner = Math.max(0, radius - t + 0.5);
		int reach = radius + 2;

		if (outline.get()) {
			for (int dy = -reach; dy <= reach; dy++) {
				for (int dx = -reach; dx <= reach; dx++) {
					double d = Math.sqrt(dx * dx + dy * dy);
					if (d < outer + 1 && d >= Math.max(0, inner - 1)) {
						graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, outlineColor);
					}
				}
			}
		}
		for (int dy = -reach; dy <= reach; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d < outer && d >= inner) {
					graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
				}
			}
		}
	}
}
