package com.cyclient.module;

import com.cyclient.setting.KeybindSetting;
import com.cyclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every Cy Client feature.
 * A module has a name, can be switched on or off, and can run code every game tick.
 */
public abstract class Module {
	protected final Minecraft mc = Minecraft.getInstance();

	private final String name;
	private final String description;
	private final boolean enabledByDefault;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();

	/** Optional hotkey that toggles this module with one key press. Null for always-on modules. */
	private final KeybindSetting toggleKey;

	protected Module(String name, String description, boolean enabledByDefault) {
		this.name = name;
		this.description = description;
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
		// Only modules the user can switch on and off get a toggle hotkey.
		this.toggleKey = canToggle() ? add(new KeybindSetting("Keybind", KeybindSetting.NONE)) : null;
	}

	/** The setting backing this module's toggle hotkey, or null if it can't be toggled. */
	public KeybindSetting toggleKeySetting() {
		return toggleKey;
	}

	/** The GLFW key code that toggles this module, or -1 if none/unbindable. */
	public int getToggleKey() {
		return toggleKey == null ? KeybindSetting.NONE : toggleKey.get();
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Runs once when the module is switched on. */
	protected void onEnable() {}

	/** Runs once when the module is switched off. */
	protected void onDisable() {}

	/** Runs 20 times per second while the module is enabled. */
	public void onTick() {}

	/** Register a setting so it shows in the settings screen and is saved to the config. */
	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public Setting<?> getSetting(String name) {
		for (Setting<?> setting : getSettings()) {
			if (setting.getName().equalsIgnoreCase(name)) return setting;
		}
		return null;
	}

	/** Hidden modules don't appear as cards in the menu (e.g. Client Settings). */
	public boolean isHidden() {
		return false;
	}

	/** False for modules that are always on (e.g. Client Settings). */
	public boolean canToggle() {
		return true;
	}

	/** Puts the module back to how it was on first install. */
	public void resetToDefaults() {
		setEnabled(enabledByDefault);
		for (Setting<?> setting : getSettings()) setting.reset();
	}

	/**
	 * The card icon: assets/cyclient/textures/gui/icons/<name>.png, e.g. "Armor HUD" -> armor_hud.png.
	 */
	public Identifier getIcon() {
		String slug = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		return Identifier.fromNamespaceAndPath("cyclient", "textures/gui/icons/" + slug + ".png");
	}

	/** Which tab of the menu this module appears under. */
	public Category getCategory() {
		return Category.UTILITY;
	}

	/** Optional live preview drawn in the settings screen, centered on (centerX, centerY). */
	public void renderPreview(GuiGraphicsExtractor graphics, int centerX, int centerY) {}

	public boolean hasPreview() {
		return false;
	}
}
