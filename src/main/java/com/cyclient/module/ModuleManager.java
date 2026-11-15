package com.cyclient.module;

import com.cyclient.module.modules.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds every module. To add a new module, create its class and add it to the list below. */
public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {}

	public static void init() {
		MODULES.add(new ClientSettingsModule());
		MODULES.add(new FpsModule());
		MODULES.add(new CpsModule());
		MODULES.add(new PingModule());
		MODULES.add(new CoordinatesModule());
		MODULES.add(new ClockModule());
		MODULES.add(new ToggleSprintModule());
		MODULES.add(new KeystrokesModule());
		MODULES.add(new CrosshairModule());
		MODULES.add(new ZoomModule());
		MODULES.add(new FullbrightModule());
		MODULES.add(new ArmorHudModule());
		MODULES.add(new PotionHudModule());
		MODULES.add(new ScoreboardModule());
		MODULES.add(new ComboModule());
		MODULES.add(new HitColorModule());
		MODULES.add(new NickHiderModule());
	}

	/** Find a module by its class, e.g. ModuleManager.get(CrosshairModule.class). */
	public static <T extends Module> T get(Class<T> type) {
		for (Module module : MODULES) {
			if (type.isInstance(module)) return type.cast(module);
		}
		throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
	}

	public static List<Module> getModules() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<HudModule> getHudModules() {
		List<HudModule> hud = new ArrayList<>();
		for (Module module : MODULES) {
			if (module instanceof HudModule hudModule) hud.add(hudModule);
		}
		return hud;
	}

	public static Module get(String name) {
		for (Module module : MODULES) {
			if (module.getName().equalsIgnoreCase(name)) return module;
		}
		return null;
	}

	public static void tick() {
		for (Module module : MODULES) {
			if (module.isEnabled()) module.onTick();
		}
	}
}
