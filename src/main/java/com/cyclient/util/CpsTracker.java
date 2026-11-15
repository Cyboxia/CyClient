package com.cyclient.util;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts left and right clicks per second.
 * It checks the attack/use keys every frame and records each new press.
 * (Version 1 approach: very accurate at normal frame rates. A later version can
 * hook the mouse directly for perfect accuracy at very low FPS.)
 */
public final class CpsTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();
	private static boolean leftWasDown;
	private static boolean rightWasDown;

	private CpsTracker() {}

	/** Call once per frame. */
	public static void update() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();

		boolean leftDown = mc.options.keyAttack.isDown();
		boolean rightDown = mc.options.keyUse.isDown();

		if (leftDown && !leftWasDown) LEFT.addLast(now);
		if (rightDown && !rightWasDown) RIGHT.addLast(now);
		leftWasDown = leftDown;
		rightWasDown = rightDown;

		prune(LEFT, now);
		prune(RIGHT, now);
	}

	private static void prune(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) {
			clicks.removeFirst();
		}
	}

	public static int getLeftCps() {
		return LEFT.size();
	}

	public static int getRightCps() {
		return RIGHT.size();
	}
}
