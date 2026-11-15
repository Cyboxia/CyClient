package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.module.modules.CrosshairModule;
import com.cyclient.setting.PixelGridSetting;
import com.cyclient.util.Colors;
import com.cyclient.util.Theme;
import com.cyclient.util.MouseButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Pixel editor for the custom crosshair, like Lunar's.
 * Left click/drag paints with the current tool, right click/drag always erases.
 */
public class CrosshairEditorScreen extends Screen {
	private static final String[] MIRROR_MODES = {"Off", "Left-Right", "Up-Down", "4-Way"};
	private static final int BUTTON_W = 100;
	private static final int BUTTON_H = 20;

	private final Screen parent;
	private final CrosshairModule crosshair;
	private final PixelGridSetting grid;

	private boolean eraser;
	private int mirrorMode = 3; // 4-Way by default, most crosshairs are symmetrical

	// Grid layout, calculated in init()
	private int cellSize;
	private int gridX;
	private int gridY;

	// Painting state
	private boolean painting;
	private boolean paintValue;

	public CrosshairEditorScreen(Screen parent, CrosshairModule crosshair) {
		super(Component.literal("Crosshair Editor"));
		this.parent = parent;
		this.crosshair = crosshair;
		this.grid = crosshair.pixels;
	}

	@Override
	protected void init() {
		// Drawing the custom crosshair only makes sense with the Custom style selected.
		crosshair.style.set("Custom");

		int n = grid.getSize();
		cellSize = Math.max(4, Math.min(12, (this.height - 70) / n));
		int gridPixels = cellSize * n;
		int controlsWidth = BUTTON_W + 16;
		int totalWidth = gridPixels + controlsWidth + 100; // grid + buttons + preview
		gridX = (this.width - totalWidth) / 2;
		gridY = 34;

		int bx = gridX + gridPixels + 16;
		int by = gridY;

		this.addRenderableWidget(Button.builder(toolLabel(), btn -> {
			eraser = !eraser;
			btn.setMessage(toolLabel());
		}).bounds(bx, by, BUTTON_W, BUTTON_H).build());

		this.addRenderableWidget(Button.builder(mirrorLabel(), btn -> {
			mirrorMode = (mirrorMode + 1) % MIRROR_MODES.length;
			btn.setMessage(mirrorLabel());
		}).bounds(bx, by + 24, BUTTON_W, BUTTON_H).build());

		this.addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> grid.clear())
				.bounds(bx, by + 48, BUTTON_W, BUTTON_H).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
				.bounds(bx, by + 84, BUTTON_W, BUTTON_H).build());
	}

	private Component toolLabel() {
		return Component.literal("Tool: " + (eraser ? "Eraser" : "Pen"));
	}

	private Component mirrorLabel() {
		return Component.literal("Mirror: " + MIRROR_MODES[mirrorMode]);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		String title = "Crosshair Editor";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 14, Theme.accent(), true);

		if (painting) paintAt(mouseX, mouseY);

		drawGrid(graphics, mouseX, mouseY);

		// Live preview at real size, to the right of the buttons.
		int previewX = gridX + cellSize * grid.getSize() + 16 + BUTTON_W + 16 + 45;
		crosshair.renderPreview(graphics, previewX, gridY + 45);

		String hint = "Left: draw   Right: erase";
		graphics.text(this.font, hint, gridX, gridY + cellSize * grid.getSize() + 6, 0xFFAAAAAA, true);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int n = grid.getSize();
		int center = n / 2;
		int pixelColor = crosshair.getColor(false) | 0xFF000000; // full opacity in the editor so it's easy to see

		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int x1 = gridX + x * cellSize;
				int y1 = gridY + y * cellSize;

				int color;
				if (grid.get(x, y)) {
					color = pixelColor;
				} else if (x == center && y == center) {
					color = 0xFF505A66; // mark the exact center
				} else if (x == center || y == center) {
					color = 0xFF3A4048; // center guide lines
				} else {
					color = ((x + y) % 2 == 0) ? 0xFF2A2E33 : 0xFF24272B; // checkerboard
				}
				graphics.fill(x1, y1, x1 + cellSize - 1, y1 + cellSize - 1, color);
			}
		}

		int[] cell = cellAt(mouseX, mouseY);
		if (cell != null) {
			int x1 = gridX + cell[0] * cellSize;
			int y1 = gridY + cell[1] * cellSize;
			graphics.outline(x1 - 1, y1 - 1, cellSize + 1, cellSize + 1, Theme.accent());
		}
	}

	/** Returns {x, y} of the grid cell under the mouse, or null if outside the grid. */
	private int[] cellAt(double mouseX, double mouseY) {
		int n = grid.getSize();
		int x = (int) Math.floor((mouseX - gridX) / cellSize);
		int y = (int) Math.floor((mouseY - gridY) / cellSize);
		if (x < 0 || y < 0 || x >= n || y >= n) return null;
		return new int[]{x, y};
	}

	private void paintAt(double mouseX, double mouseY) {
		int[] cell = cellAt(mouseX, mouseY);
		if (cell == null) return;
		int n = grid.getSize() - 1;
		int x = cell[0];
		int y = cell[1];

		grid.set(x, y, paintValue);
		boolean lr = mirrorMode == 1 || mirrorMode == 3;
		boolean ud = mirrorMode == 2 || mirrorMode == 3;
		if (lr) grid.set(n - x, y, paintValue);
		if (ud) grid.set(x, n - y, paintValue);
		if (lr && ud) grid.set(n - x, n - y, paintValue);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (cellAt(event.x(), event.y()) != null) {
			if (MouseButtons.isLeft(event.button())) {
				painting = true;
				paintValue = !eraser;
			} else if (MouseButtons.isRightOrMiddle(event.button())) {
				painting = true;
				paintValue = false;
			}
			if (painting) {
				paintAt(event.x(), event.y());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		painting = false;
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
