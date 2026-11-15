package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.module.HudModule;
import com.cyclient.module.ModuleManager;
import com.cyclient.util.Colors;
import com.cyclient.util.MouseButtons;
import com.cyclient.util.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Move and resize HUD modules.
 * - Drag a module to move it.
 * - Drag the small handle on a module's bottom-right corner to resize it.
 * - Or scroll the mouse wheel over a module to resize it in 5% steps.
 *
 * Click/release events track the left button; positions follow the mouse every frame.
 */
public class HudEditorScreen extends Screen {
	private static final int HANDLE = 5;        // size of the corner handle, in GUI pixels
	private static final int HANDLE_GRAB = 4;   // extra grab margin around the handle
	private static final int SCROLL_STEP = 5;   // % per scroll notch
	private static final int MIN_SCALE = 50;
	private static final int MAX_SCALE = 200;

	private final Screen parent;

	private HudModule dragging;
	private int offsetX;
	private int offsetY;

	private HudModule resizing;

	private boolean leftDown;
	private boolean wasLeftDown;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Cy Client HUD Editor"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		updateMouse(mouseX, mouseY);

		HudModule hovered = topModuleAt(mouseX, mouseY);
		HudModule active = resizing != null ? resizing : dragging != null ? dragging : hovered;

		for (HudModule module : ModuleManager.getHudModules()) {
			if (!module.isEnabled()) continue;
			module.renderScaled(graphics, true);

			boolean highlight = module == active;
			int color = highlight ? Theme.accent() : Colors.EDITOR_OUTLINE;
			int w = module.getScaledWidth();
			int h = module.getScaledHeight();
			graphics.outline(module.x - 1, module.y - 1, w + 2, h + 2, color);

			// Resize handle on the bottom-right corner
			boolean handleHover = isOnHandle(module, mouseX, mouseY);
			int hx = module.x + w - HANDLE + 2;
			int hy = module.y + h - HANDLE + 2;
			int handleColor = (handleHover || module == resizing) ? Theme.accent() : (highlight ? 0xFFFFFFFF : 0x80FFFFFF);
			graphics.fill(hx, hy, hx + HANDLE, hy + HANDLE, handleColor);
		}

		// Show the scale of the module being hovered, moved or resized.
		if (active != null && active.isEnabled()) {
			String label = active.scale.get() + "%";
			int lw = this.font.width(label);
			int lx = active.x + active.getScaledWidth() - lw;
			int ly = active.y - 11;
			if (ly < 2) ly = active.y + active.getScaledHeight() + 3; // flip below if at the top edge
			graphics.fill(lx - 3, ly - 2, lx + lw + 2, ly + 9, 0xC0000000);
			graphics.text(this.font, label, lx, ly, Theme.accent(), false);
		}

		String hint = "Drag to move  |  Drag the corner or scroll to resize  |  Esc to save";
		graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height - 14, Colors.TEXT, true);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (MouseButtons.isLeft(event.button())) leftDown = true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (MouseButtons.isLeft(event.button())) leftDown = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudModule module = topModuleAt(mouseX, mouseY);
		if (module == null || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		int step = scrollY > 0 ? SCROLL_STEP : -SCROLL_STEP;
		// Snap to multiples of 5 so values stay tidy.
		int snapped = Math.round(module.scale.get() / (float) SCROLL_STEP) * SCROLL_STEP;
		setScale(module, snapped + step);
		return true;
	}

	/** Called every frame: starts/continues/ends moving and resizing. */
	private void updateMouse(int mouseX, int mouseY) {
		if (leftDown && !wasLeftDown) {
			// Just pressed: a corner handle takes priority over moving.
			HudModule handleModule = topHandleAt(mouseX, mouseY);
			if (handleModule != null) {
				resizing = handleModule;
			} else {
				HudModule module = topModuleAt(mouseX, mouseY);
				if (module != null) {
					dragging = module;
					offsetX = mouseX - module.x;
					offsetY = mouseY - module.y;
				}
			}
		} else if (!leftDown) {
			dragging = null;
			resizing = null;
		}

		if (resizing != null) {
			// Scale so the bottom-right corner follows the mouse (whichever direction is pulled further).
			float sx = (mouseX - resizing.x) / (float) Math.max(1, resizing.getWidth());
			float sy = (mouseY - resizing.y) / (float) Math.max(1, resizing.getHeight());
			setScale(resizing, Math.round(Math.max(sx, sy) * 100F));
		}

		if (dragging != null) {
			dragging.x = clamp(mouseX - offsetX, 0, this.width - dragging.getScaledWidth());
			dragging.y = clamp(mouseY - offsetY, 0, this.height - dragging.getScaledHeight());
		}

		wasLeftDown = leftDown;
	}

	private void setScale(HudModule module, int percent) {
		module.scale.set(clamp(percent, MIN_SCALE, MAX_SCALE));
		// Keep it fully on screen after growing.
		module.x = clamp(module.x, 0, Math.max(0, this.width - module.getScaledWidth()));
		module.y = clamp(module.y, 0, Math.max(0, this.height - module.getScaledHeight()));
	}

	// ---------------------------------------------------------------- hit testing

	/** The top-most (last drawn) enabled module under the mouse, or null. */
	private HudModule topModuleAt(double mouseX, double mouseY) {
		List<HudModule> modules = ModuleManager.getHudModules();
		for (int i = modules.size() - 1; i >= 0; i--) {
			HudModule module = modules.get(i);
			if (module.isEnabled() && (module.isHovered(mouseX, mouseY) || isOnHandle(module, mouseX, mouseY))) {
				return module;
			}
		}
		return null;
	}

	private HudModule topHandleAt(double mouseX, double mouseY) {
		List<HudModule> modules = ModuleManager.getHudModules();
		for (int i = modules.size() - 1; i >= 0; i--) {
			HudModule module = modules.get(i);
			if (module.isEnabled() && isOnHandle(module, mouseX, mouseY)) return module;
		}
		return null;
	}

	private static boolean isOnHandle(HudModule module, double mouseX, double mouseY) {
		int cx = module.x + module.getScaledWidth();
		int cy = module.y + module.getScaledHeight();
		int reach = HANDLE / 2 + HANDLE_GRAB;
		return Math.abs(mouseX - cx) <= reach && Math.abs(mouseY - cy) <= reach;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
