package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.module.Module;
import com.cyclient.setting.ActionSetting;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.setting.PixelGridSetting;
import com.cyclient.setting.Setting;
import com.cyclient.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shows every setting of one module, plus a live preview if the module has one. */
public class ModuleSettingsScreen extends Screen {
	private static final int WIDGET_W = 160;
	private static final int WIDGET_H = 20;
	private static final int GAP = 4;
	private static final int TOP = 36;
	private static final int PREVIEW_SPACE = 110;

	private final Screen parent;
	private final Module module;
	private int previewX;

	public ModuleSettingsScreen(Screen parent, Module module) {
		super(Component.literal(module.getName() + " Settings"));
		this.parent = parent;
		this.module = module;
	}

	@Override
	protected void init() {
		// Pixel grids have their own editor, so they don't get a widget here.
		List<Setting<?>> settings = module.getSettings().stream()
				.filter(setting -> !(setting instanceof PixelGridSetting))
				.toList();

		// Fit as many rows as the screen allows, then wrap into more columns.
		int rowsAvailable = Math.max(1, (this.height - TOP - 40) / (WIDGET_H + GAP));
		int columns = Math.max(1, (settings.size() + rowsAvailable - 1) / rowsAvailable);
		int rows = Math.min(settings.size(), rowsAvailable);

		int gridWidth = columns * WIDGET_W + (columns - 1) * GAP;
		int totalWidth = gridWidth + (module.hasPreview() ? PREVIEW_SPACE : 0);
		int left = (this.width - totalWidth) / 2;
		previewX = left + gridWidth + PREVIEW_SPACE / 2;

		for (int i = 0; i < settings.size(); i++) {
			int x = left + (i / rows) * (WIDGET_W + GAP);
			int y = TOP + (i % rows) * (WIDGET_H + GAP);
			this.addRenderableWidget(createWidget(settings.get(i), x, y));
		}

		int doneY = TOP + rows * (WIDGET_H + GAP) + 8;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
				.bounds((this.width - WIDGET_W) / 2, doneY, WIDGET_W, WIDGET_H).build());
	}

	private AbstractWidget createWidget(Setting<?> setting, int x, int y) {
		if (setting instanceof IntSetting intSetting) {
			return new IntSlider(x, y, WIDGET_W, WIDGET_H, intSetting);
		}
		if (setting instanceof BooleanSetting bool) {
			return Button.builder(boolLabel(bool), btn -> {
				bool.toggle();
				btn.setMessage(boolLabel(bool));
			}).bounds(x, y, WIDGET_W, WIDGET_H).build();
		}
		if (setting instanceof ModeSetting mode) {
			return Button.builder(modeLabel(mode), btn -> {
				mode.cycle();
				btn.setMessage(modeLabel(mode));
			}).bounds(x, y, WIDGET_W, WIDGET_H).build();
		}
		if (setting instanceof ActionSetting action) {
			return Button.builder(Component.literal(action.getName()), btn -> action.run(this))
					.bounds(x, y, WIDGET_W, WIDGET_H).build();
		}
		throw new IllegalStateException("No widget for setting type " + setting.getClass());
	}

	private static Component boolLabel(BooleanSetting s) {
		return Component.literal(s.getName() + ": " + (s.get() ? "ON" : "OFF"));
	}

	private static Component modeLabel(ModeSetting s) {
		return Component.literal(s.getName() + ": " + s.get());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String title = module.getName() + " Settings";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 16, Colors.ACCENT, true);

		if (module.hasPreview()) {
			// Preview sits to the right of the settings grid.
			module.renderPreview(graphics, previewX, TOP + 50);
		}
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
