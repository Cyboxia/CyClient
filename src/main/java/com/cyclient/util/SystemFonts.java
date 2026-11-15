package com.cyclient.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds font files installed on the computer.
 * Display names ("Segoe UI Bold" instead of "segoeuib.ttf") are read in the background
 * so the list appears instantly and the names fill in a moment later.
 */
public final class SystemFonts {
	private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
	private static List<File> files;
	private static boolean namesStarted;

	private SystemFonts() {}

	public static synchronized List<File> list() {
		if (files == null) {
			files = scan();
			startNameLookup();
		}
		return files;
	}

	/** A readable name for the font file (its real name once known, else the file name). */
	public static String name(File file) {
		String name = NAMES.get(file.getPath());
		if (name != null) return name;
		String n = file.getName();
		int dot = n.lastIndexOf('.');
		return dot > 0 ? n.substring(0, dot) : n;
	}

	private static List<File> scan() {
		List<File> found = new ArrayList<>();
		List<File> dirs = new ArrayList<>();
		String windir = System.getenv("WINDIR");
		dirs.add(new File(windir != null ? windir : "C:\\Windows", "Fonts"));
		String localAppData = System.getenv("LOCALAPPDATA");
		if (localAppData != null) dirs.add(new File(localAppData, "Microsoft\\Windows\\Fonts")); // per-user installs
		String home = System.getProperty("user.home");
		dirs.add(new File(home, "Library/Fonts"));         // macOS
		dirs.add(new File("/Library/Fonts"));
		dirs.add(new File("/usr/share/fonts"));            // Linux
		dirs.add(new File(home, ".local/share/fonts"));

		for (File dir : dirs) collect(dir, found, 0);
		return Collections.unmodifiableList(found);
	}

	private static void collect(File dir, List<File> out, int depth) {
		File[] children = dir.listFiles();
		if (children == null || depth > 3) return;
		for (File f : children) {
			if (f.isDirectory()) {
				collect(f, out, depth + 1);
				continue;
			}
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc")) out.add(f);
		}
	}

	private static void startNameLookup() {
		if (namesStarted) return;
		namesStarted = true;
		Thread thread = new Thread(() -> {
			for (File f : files) {
				try {
					java.awt.Font[] fonts = java.awt.Font.createFonts(f);
					if (fonts.length > 0) NAMES.put(f.getPath(), fonts[0].getFontName(Locale.ROOT));
				} catch (Throwable ignored) {
					// Unreadable font: keep the file name.
				}
			}
		}, "Cy Client font names");
		thread.setDaemon(true);
		thread.start();
	}
}
