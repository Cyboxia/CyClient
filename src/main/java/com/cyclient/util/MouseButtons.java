package com.cyclient.util;

/**
 * Minecraft 26.x numbers mouse buttons starting at 1 (left = 1, middle = 2, right = 3).
 * Older versions started at 0 (left = 0, right = 1, middle = 2).
 * These helpers follow the 26.x numbering and also accept 0 as left.
 */
public final class MouseButtons {
	private MouseButtons() {}

	public static boolean isLeft(int button) {
		return button == 1 || button == 0;
	}

	/** Right (3) or middle (2) click. */
	public static boolean isRightOrMiddle(int button) {
		return button == 2 || button == 3;
	}
}
