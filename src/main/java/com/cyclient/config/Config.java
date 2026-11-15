package com.cyclient.config;

import com.cyclient.CyClient;
import com.cyclient.module.HudModule;
import com.cyclient.module.Module;
import com.cyclient.module.ModuleManager;
import com.cyclient.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Saves and loads module settings to .minecraft/config/cyclient.json.
 * Format: { "FPS": { "enabled": true, "x": 4, "y": 4 }, ... }
 */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cyclient.json");

	private Config() {}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null) return;
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				Module module = ModuleManager.get(entry.getKey());
				if (module == null || !entry.getValue().isJsonObject()) continue;
				JsonObject data = entry.getValue().getAsJsonObject();
				if (data.has("enabled")) module.setEnabled(data.get("enabled").getAsBoolean());
				if (module instanceof HudModule hud) {
					if (data.has("x")) hud.x = data.get("x").getAsInt();
					if (data.has("y")) hud.y = data.get("y").getAsInt();
				}
				if (data.has("settings") && data.get("settings").isJsonObject()) {
					JsonObject saved = data.getAsJsonObject("settings");
					for (Setting<?> setting : module.getSettings()) {
						if (!saved.has(setting.getName())) continue;
						try {
							setting.fromJson(saved.get(setting.getName()));
						} catch (Exception e) {
							CyClient.LOGGER.warn("Ignoring bad value for {} > {}", module.getName(), setting.getName());
						}
					}
				}
			}
		} catch (Exception e) {
			CyClient.LOGGER.error("Could not load Cy Client config, using defaults", e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		for (Module module : ModuleManager.getModules()) {
			JsonObject data = new JsonObject();
			data.addProperty("enabled", module.isEnabled());
			if (module instanceof HudModule hud) {
				data.addProperty("x", hud.x);
				data.addProperty("y", hud.y);
			}
			if (!module.getSettings().isEmpty()) {
				JsonObject settings = new JsonObject();
				for (Setting<?> setting : module.getSettings()) {
					var json = setting.toJson();
					if (json != null) settings.add(setting.getName(), json); // buttons have nothing to save
				}
				data.add("settings", settings);
			}
			root.add(module.getName(), data);
		}
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			CyClient.LOGGER.error("Could not save Cy Client config", e);
		}
	}
}
