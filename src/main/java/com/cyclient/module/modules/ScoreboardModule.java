package com.cyclient.module.modules;

import com.cyclient.module.HudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The server's sidebar scoreboard, drawn by Cy Client so it can be moved, scaled
 * and restyled. Vanilla's sidebar is hidden while this is on.
 */
public class ScoreboardModule extends HudModule {
	private static final int PAD = 2;
	private static final int LINE_H = 9;
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator
			.comparing(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	public final BooleanSetting hideNumbers = add(new BooleanSetting("Hide Red Numbers", true));
	public final BooleanSetting showTitle = add(new BooleanSetting("Show Title", true));
	public final BooleanSetting titleBackground = add(new BooleanSetting("Darker Title Bar", true));
	public final IntSetting maxLines = add(new IntSetting("Max Lines", 15, 3, 15));

	private record Line(Component name, Component score) {}

	private record Board(Component title, List<Line> lines) {}

	public ScoreboardModule() {
		// -1 means "not placed yet": it starts on the right side like vanilla.
		super("Scoreboard", "Move, scale and restyle the server sidebar. Can hide the red numbers.", true, -1, -1);
	}

	@Override
	protected void placeDefault(int screenWidth, int screenHeight) {
		x = screenWidth - getScaledWidth() - 1;
		y = screenHeight / 2 - getScaledHeight() / 3;
	}

	private Board board(boolean editor) {
		if (mc.level != null && mc.player != null) {
			Scoreboard scoreboard = mc.level.getScoreboard();
			Objective objective = null;

			// Team-colored sidebars take priority, same as vanilla.
			PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
			if (team != null && team.getColor().isPresent()) {
				objective = scoreboard.getDisplayObjective(team.getColor().get().displaySlot());
			}
			if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

			if (objective != null) {
				NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
				List<Line> lines = new ArrayList<>();
				scoreboard.listPlayerScores(objective).stream()
						.filter(e -> !e.isHidden())
						.sorted(ORDER)
						.limit(maxLines.get())
						.forEach(e -> {
							PlayerTeam owner = scoreboard.getPlayersTeam(e.owner());
							lines.add(new Line(PlayerTeam.formatNameForTeam(owner, e.ownerName()), e.formatValue(format)));
						});
				return new Board(objective.getDisplayName(), lines);
			}
		}
		if (!editor) return null;

		// Sample for the HUD editor
		List<Line> sample = new ArrayList<>();
		String[] names = {"", "Kills: §a12", "Deaths: §c3", "", "§ecyclient.gg"};
		for (int i = 0; i < names.length; i++) {
			sample.add(new Line(Component.literal(names[i]), Component.literal("§c" + (names.length - i))));
		}
		return new Board(Component.literal("§e§lCY CLIENT"), sample);
	}

	private int scoreWidth(Board b) {
		if (hideNumbers.get()) return 0;
		int w = 0;
		for (Line l : b.lines()) w = Math.max(w, font().width(l.score()));
		return w;
	}

	private int contentWidth(Board b) {
		int w = showTitle.get() ? font().width(b.title()) : 0;
		int scoreW = scoreWidth(b);
		for (Line l : b.lines()) {
			w = Math.max(w, font().width(l.name()) + (scoreW > 0 ? font().width(" ") + scoreW : 0));
		}
		return w;
	}

	@Override
	public int getWidth() {
		Board b = board(true);
		return b == null ? 40 : contentWidth(b) + PAD * 2;
	}

	@Override
	public int getHeight() {
		Board b = board(true);
		if (b == null) return 20;
		return (b.lines().size() + (showTitle.get() ? 1 : 0)) * LINE_H + 2;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, boolean editor) {
		Board b = board(editor);
		if (b == null) return;
		int w = contentWidth(b) + PAD * 2;
		int scoreW = scoreWidth(b);
		int ty = y + 1;

		if (showTitle.get()) {
			if (background.get()) {
				int bg = backgroundArgb();
				int titleBg = titleBackground.get() ? ((Math.min(255, ((bg >>> 24) & 0xFF) + 0x20)) << 24) | (bg & 0xFFFFFF) : bg;
				g.fill(x, y, x + w, y + LINE_H + 1, titleBg);
			}
			g.text(font(), b.title(), x + (w - font().width(b.title())) / 2, ty, labelColor(x, ty), shadow());
			ty += LINE_H;
		}

		int linesTop = ty;
		if (background.get()) g.fill(x, linesTop, x + w, y + getHeight(), backgroundArgb());

		for (Line line : b.lines()) {
			g.text(font(), line.name(), x + PAD, ty, labelColor(x, ty), shadow());
			if (scoreW > 0) {
				g.text(font(), line.score(), x + w - PAD - font().width(line.score()), ty, valueTextColor(x, ty), shadow());
			}
			ty += LINE_H;
		}
	}
}
