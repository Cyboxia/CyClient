package com.cyclient.module.modules;

import com.cyclient.module.HudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lists your active potion effects with icons, levels and time left. */
public class PotionHudModule extends HudModule {
	private static final int PAD = 3;
	private static final int ICON = 18;
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	public final BooleanSetting showIcons = add(new BooleanSetting("Show Icons", true));
	public final BooleanSetting showLevel = add(new BooleanSetting("Show Level", true));
	public final BooleanSetting showDuration = add(new BooleanSetting("Show Duration", true));
	public final ModeSetting nameColor = add(new ModeSetting("Name Color", "Effect Color", "Effect Color", "Text Color"));
	public final BooleanSetting blink = add(new BooleanSetting("Blink When Ending", true));
	public final ModeSetting sort = add(new ModeSetting("Sort By", "Duration", "Duration", "Name", "None"));
	public final BooleanSetting showAmbient = add(new BooleanSetting("Show Beacon Effects", true));
	public final BooleanSetting hideVanilla = add(new BooleanSetting("Hide Vanilla Effects", true));

	/** One row: what to draw. */
	private record Entry(Holder<MobEffect> effect, String name, String time, int color, boolean ending) {}

	public PotionHudModule() {
		super("Potion Effects", "Lists your active potion effects and time left.", true, 90, 4);
	}

	private List<Entry> entries(boolean editor) {
		List<Entry> list = new ArrayList<>();
		if (mc.player != null) {
			List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
			if (sort.is("Duration")) effects.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
			if (sort.is("Name")) effects.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));
			for (MobEffectInstance e : effects) {
				if (!showAmbient.get() && e.isAmbient()) continue;
				list.add(new Entry(e.getEffect(), name(e.getEffect(), e.getAmplifier()), time(e),
						0xFF000000 | e.getEffect().value().getColor(),
						!e.isInfiniteDuration() && e.getDuration() < 200));
			}
		}
		if (editor && list.isEmpty()) {
			list.add(new Entry(MobEffects.SPEED, name(MobEffects.SPEED, 1), "1:30", 0xFF000000 | MobEffects.SPEED.value().getColor(), false));
			list.add(new Entry(MobEffects.STRENGTH, name(MobEffects.STRENGTH, 0), "0:45", 0xFF000000 | MobEffects.STRENGTH.value().getColor(), false));
		}
		return list;
	}

	private String name(Holder<MobEffect> effect, int amplifier) {
		String n = effect.value().getDisplayName().getString();
		if (showLevel.get() && amplifier > 0) {
			n += " " + (amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1));
		}
		return n;
	}

	private static String time(MobEffectInstance e) {
		if (e.isInfiniteDuration()) return "**:**";
		int seconds = e.getDuration() / 20;
		int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}

	private int rowHeight() {
		return showIcons.get() ? ICON + 2 : (showDuration.get() ? 20 : 10);
	}

	private int textX() {
		return showIcons.get() ? ICON + 4 : 0;
	}

	@Override
	public int getWidth() {
		int w = 60;
		for (Entry e : entries(true)) {
			int tw = font().width(e.name());
			if (showDuration.get()) tw = Math.max(tw, font().width(e.time()));
			w = Math.max(w, tw);
		}
		return PAD * 2 + textX() + w;
	}

	@Override
	public int getHeight() {
		return PAD * 2 + Math.max(1, entries(true).size()) * rowHeight() - 2;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, boolean editor) {
		List<Entry> list = entries(editor);
		if (list.isEmpty()) return;
		drawBox(g, x, y, getWidth(), PAD * 2 + list.size() * rowHeight() - 2);

		boolean blinkOff = (System.currentTimeMillis() / 400) % 2 == 0;
		for (int i = 0; i < list.size(); i++) {
			Entry e = list.get(i);
			int rx = x + PAD;
			int ry = y + PAD + i * rowHeight();
			boolean dim = blink.get() && e.ending() && blinkOff;
			int alpha = dim ? 0x60 : 0xFF;

			if (showIcons.get()) {
				int white = (alpha << 24) | 0xFFFFFF;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.effect()), rx, ry, ICON, ICON, white);
			}

			int tx = rx + textX();
			int nameY = showIcons.get() ? (showDuration.get() ? ry + 1 : ry + 5) : ry;
			int nameColor = this.nameColor.is("Effect Color") ? e.color() : labelColor(tx, nameY);
			drawText(g, e.name(), tx, nameY, withAlpha(nameColor, alpha));

			if (showDuration.get()) {
				int timeY = nameY + 9;
				drawText(g, e.time(), tx, timeY, withAlpha(valueTextColor(tx, timeY), Math.min(alpha, 0xC0)));
			}
		}
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0xFFFFFF);
	}
}
