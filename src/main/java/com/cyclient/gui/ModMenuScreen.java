package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.module.Module;
import com.cyclient.module.ModuleManager;
import com.cyclient.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The main Cy Client menu (Right Shift). Lists every module with an on/off button. */
public class ModMenuScreen extends Screen {
	private static final int BUTTON_W = 150;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;
	private static final int SETTINGS_W = 22;

	private final Screen parent;

	public ModMenuScreen(Screen parent) {
		super(Component.literal("Cy Client"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<Module> modules = ModuleManager.getModules();
		int columns = 2;
		int gridWidth = columns * BUTTON_W + (columns - 1) * GAP;
		int left = (this.width - gridWidth) / 2;
		int top = 40;

		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			int col = i % columns;
			int row = i / columns;
			int bx = left + col * (BUTTON_W + GAP);
			int by = top + row * (BUTTON_H + GAP);

			boolean hasSettings = !module.getSettings().isEmpty();
			int toggleWidth = hasSettings ? BUTTON_W - SETTINGS_W - 2 : BUTTON_W;

			Button button = Button.builder(label(module), btn -> {
				module.toggle();
				btn.setMessage(label(module));
			}).bounds(bx, by, toggleWidth, BUTTON_H).build();
			this.addRenderableWidget(button);

			if (hasSettings) {
				this.addRenderableWidget(Button.builder(Component.literal("..."), btn ->
						this.minecraft.gui.setScreen(new ModuleSettingsScreen(this, module))
				).bounds(bx + toggleWidth + 2, by, SETTINGS_W, BUTTON_H).build());
			}
		}

		int rows = (modules.size() + columns - 1) / columns;
		int bottom = top + rows * (BUTTON_H + GAP) + 10;

		this.addRenderableWidget(Button.builder(Component.literal("Edit HUD Layout"), btn ->
				this.minecraft.gui.setScreen(new HudEditorScreen(this))
		).bounds(left, bottom, BUTTON_W, BUTTON_H).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
				.bounds(left + BUTTON_W + GAP, bottom, BUTTON_W, BUTTON_H).build());
	}

	private static Component label(Module module) {
		return Component.literal(module.getName() + ": " + (module.isEnabled() ? "ON" : "OFF"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String title = "Cy Client";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, Colors.ACCENT, true);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
