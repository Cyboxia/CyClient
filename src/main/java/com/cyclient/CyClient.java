package com.cyclient;

import com.cyclient.config.Config;
import com.cyclient.gui.HudEditorScreen;
import com.cyclient.gui.ClickGuiScreen;
import com.cyclient.module.HudModule;
import com.cyclient.module.Module;
import com.cyclient.module.ModuleManager;
import com.cyclient.module.modules.ClientSettingsModule;
import com.cyclient.module.modules.CrosshairModule;
import com.cyclient.module.modules.PotionHudModule;
import com.cyclient.module.modules.ScoreboardModule;
import com.cyclient.module.modules.ZoomModule;
import com.cyclient.util.CpsTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CyClient implements ClientModInitializer {
	public static final String MOD_ID = "cyclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cy Client");

	// Keys that were held down last tick, so we only act on a fresh press (not while held).
	private static java.util.Set<Integer> keysDownLastTick = new java.util.HashSet<>();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		Config.load();

		// One-time move to the launcher's monochrome look: bump the old blue defaults, keep custom colors.
		ClientSettingsModule cs = ModuleManager.get(ClientSettingsModule.class);
		if (cs.accent.get() == 0x4FC3F7) cs.accent.set(0xECEEF2);
		if (cs.cardColor.get() == 0x1D2127) cs.cardColor.set(0x1B1D21);

		// Zoom (C by default, rebindable in Minecraft's Controls).
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		ModuleManager.get(ZoomModule.class).registerKey(category);

		// The ClickGUI open key and per-module toggle hotkeys are rebindable inside Cy Client,
		// so they're polled here each tick instead of being fixed vanilla key mappings.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			handleHotkeys(client);
			ModuleManager.tick();
		});

		// One HUD layer draws every enabled HUD module, drawn just before (under) chat.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud"), CyClient::renderHud);

		// Swap the vanilla crosshair for ours. When the Crosshair module is off, vanilla's draws as normal.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, deltaTracker) -> {
			CrosshairModule crosshair = ModuleManager.get(CrosshairModule.class);
			if (crosshair.isEnabled()) {
				crosshair.renderInGame(graphics);
			} else {
				vanilla.extractRenderState(graphics, deltaTracker);
			}
		});

		// Hide vanilla's potion icons (top right) when the Potion Effects module replaces them.
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, deltaTracker) -> {
			PotionHudModule potions = ModuleManager.get(PotionHudModule.class);
			if (potions.isEnabled() && potions.hideVanilla.get()) return;
			vanilla.extractRenderState(graphics, deltaTracker);
		});

		// Hide vanilla's sidebar when the Scoreboard module draws its own.
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (graphics, deltaTracker) -> {
			if (ModuleManager.get(ScoreboardModule.class).isEnabled()) return;
			vanilla.extractRenderState(graphics, deltaTracker);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save());

		LOGGER.info("Cy Client loaded with {} modules", ModuleManager.getModules().size());
	}

	/**
	 * Polls the configurable binds each tick and acts on a fresh key press:
	 * the open key toggles the ClickGUI, and each module's hotkey toggles that module.
	 */
	private static void handleHotkeys(Minecraft client) {
		ClientSettingsModule cs = ModuleManager.get(ClientSettingsModule.class);
		int openKey = cs.openKey.get();

		java.util.Set<Integer> down = new java.util.HashSet<>();
		if (openKey >= 0 && InputConstants.isKeyDown(openKey)) down.add(openKey);
		for (Module module : ModuleManager.getModules()) {
			if (!module.canToggle()) continue;
			int key = module.getToggleKey();
			if (key >= 0 && InputConstants.isKeyDown(key)) down.add(key);
		}

		// Only act on keys not held last tick, and only when no screen is open (so typing is safe).
		if (client.gui.screen() == null) {
			if (openKey >= 0 && down.contains(openKey) && !keysDownLastTick.contains(openKey)) {
				client.gui.setScreen(new ClickGuiScreen(null));
			} else {
				for (Module module : ModuleManager.getModules()) {
					if (!module.canToggle()) continue;
					int key = module.getToggleKey();
					if (key >= 0 && key != openKey && down.contains(key) && !keysDownLastTick.contains(key)) {
						module.toggle();
					}
				}
			}
		}
		keysDownLastTick = down;
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CpsTracker.update();

		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof HudEditorScreen) return; // the editor draws modules itself

		for (HudModule module : ModuleManager.getHudModules()) {
			if (module.isEnabled()) {
				module.renderScaled(graphics, false);
			}
		}
	}
}
