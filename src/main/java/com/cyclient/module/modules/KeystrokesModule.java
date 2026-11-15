package com.cyclient.module.modules;

import com.cyclient.module.HudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ColorSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.ModeSetting;
import com.cyclient.util.CpsTracker;
import com.cyclient.util.Render2D;
import com.cyclient.util.Theme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The classic W/A/S/D + mouse + space keystrokes display. */
public class KeystrokesModule extends HudModule {
	private static final int GAP = 2;
	private static final int FADE_MS = 120;

	public final IntSetting keySize = add(new IntSetting("Key Size", 22, 16, 32));
	public final ModeSetting keyLabels = add(new ModeSetting("Key Labels", "Letters", "Letters", "Arrows"));
	public final BooleanSetting showMouse = add(new BooleanSetting("Show Mouse Buttons", true));
	public final ModeSetting mouseLabel = add(new ModeSetting("Mouse Label", "CPS", "CPS", "LMB / RMB"));
	public final BooleanSetting showSpace = add(new BooleanSetting("Show Space Bar", true));
	public final BooleanSetting fade = add(new BooleanSetting("Fade Animation", true));
	public final ColorSetting pressedColor = add(new ColorSetting("Pressed Color", 0xFFFFFF));
	public final IntSetting pressedOpacity = add(new IntSetting("Pressed Opacity", 70, 10, 100));
	public final ColorSetting pressedTextColor = add(new ColorSetting("Pressed Text Color", 0x000000));

	// Fade state for the 7 keys: W A S D LMB RMB Space
	private final boolean[] lastDown = new boolean[7];
	private final long[] changedAt = new long[7];

	public KeystrokesModule() {
		super("Keystrokes", "Shows which movement keys and mouse buttons you are pressing.", true, 4, 120);
	}

	private int mouseH() {
		return Math.round(keySize.get() * 0.8F);
	}

	private int spaceH() {
		return Math.max(8, keySize.get() / 2);
	}

	@Override
	public int getWidth() {
		return keySize.get() * 3 + GAP * 2;
	}

	@Override
	public int getHeight() {
		int k = keySize.get();
		int h = k * 2 + GAP;
		if (showMouse.get()) h += GAP + mouseH();
		if (showSpace.get()) h += GAP + spaceH();
		return h;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, boolean editor) {
		int k = keySize.get();
		int w = getWidth();
		boolean arrows = keyLabels.is("Arrows");

		drawKey(g, 0, x + k + GAP, y, k, k, arrows ? "↑" : "W", mc.options.keyUp.isDown());

		int row2 = y + k + GAP;
		drawKey(g, 1, x, row2, k, k, arrows ? "←" : "A", mc.options.keyLeft.isDown());
		drawKey(g, 2, x + k + GAP, row2, k, k, arrows ? "↓" : "S", mc.options.keyDown.isDown());
		drawKey(g, 3, x + (k + GAP) * 2, row2, k, k, arrows ? "→" : "D", mc.options.keyRight.isDown());

		int next = row2 + k + GAP;
		if (showMouse.get()) {
			int half = (w - GAP) / 2;
			boolean cps = mouseLabel.is("CPS");
			drawKey(g, 4, x, next, half, mouseH(), cps ? CpsTracker.getLeftCps() + " CPS" : "LMB", isDown(mc.options.keyAttack));
			drawKey(g, 5, x + half + GAP, next, w - half - GAP, mouseH(), cps ? CpsTracker.getRightCps() + " CPS" : "RMB", isDown(mc.options.keyUse));
			next += mouseH() + GAP;
		}

		if (showSpace.get()) {
			int sh = spaceH();
			float t = pressAmount(6, mc.options.keyJump.isDown());
			drawKeyBackground(g, x, next, w, sh, t);
			int barColor = Render2D.lerpColor(labelColor(x, next), 0xFF000000 | pressedTextColor.get(), t);
			int barY = next + sh / 2;
			g.fill(x + w / 2 - k / 2, barY, x + w / 2 + k / 2, barY + 1, barColor);
		}
	}

	private static boolean isDown(KeyMapping key) {
		return key.isDown();
	}

	private void drawKey(GuiGraphicsExtractor g, int index, int x, int y, int w, int h, String label, boolean down) {
		float t = pressAmount(index, down);
		drawKeyBackground(g, x, y, w, h, t);

		int textX = x + (w - font().width(label)) / 2;
		int textY = y + (h - font().lineHeight) / 2 + 1;
		int color = Render2D.lerpColor(labelColor(textX, textY), 0xFF000000 | pressedTextColor.get(), t);
		g.text(font(), label, textX, textY, color, shadow() && t < 0.5F);
	}

	/** Background that blends from the normal box to the pressed color. */
	private void drawKeyBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, float t) {
		int normal = background.get() ? backgroundArgb() : 0x00000000;
		int alpha = Math.round(pressedOpacity.get() / 100F * 255F);
		int pressed = (alpha << 24) | pressedColor.get();
		int color = Render2D.lerpColor(normal, pressed, t);
		Render2D.roundedRect(g, x, y, x + w, y + h, Theme.hudRounded() ? 3 : 0, color);
	}

	/** 0 = released, 1 = pressed, in between while fading. */
	private float pressAmount(int index, boolean down) {
		long now = System.currentTimeMillis();
		if (down != lastDown[index]) {
			lastDown[index] = down;
			changedAt[index] = now;
		}
		if (!fade.get()) return down ? 1F : 0F;
		float p = Math.min(1F, (now - changedAt[index]) / (float) FADE_MS);
		return down ? p : 1F - p;
	}
}
