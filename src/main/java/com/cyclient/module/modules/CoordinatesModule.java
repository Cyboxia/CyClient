package com.cyclient.module.modules;

import com.cyclient.module.HudModule;
import com.cyclient.setting.BooleanSetting;
import com.cyclient.setting.IntSetting;
import com.cyclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** XYZ position, facing direction, biome, and Nether/Overworld conversion. */
public class CoordinatesModule extends HudModule {
	private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
	private static final String[] DIRECTION_NAMES = {"South", "South West", "West", "North West", "North", "North East", "East", "South East"};
	private static final int PADDING = 5;
	private static final int LINE_H = 10;

	public final ModeSetting layout = add(new ModeSetting("Layout", "Single Line", "Single Line", "Vertical"));
	public final IntSetting decimals = add(new IntSetting("Decimals", 0, 0, 2));
	public final ModeSetting direction = add(new ModeSetting("Direction", "Short", "Off", "Short", "Full"));
	public final BooleanSetting showBiome = add(new BooleanSetting("Show Biome", false));
	public final BooleanSetting otherDimension = add(new BooleanSetting("Nether/Overworld Coords", false));

	public CoordinatesModule() {
		super("Coordinates", "Shows your XYZ position and the direction you face.", false, 4, 58);
	}

	/** Each entry is {label, value}. */
	private List<String[]> lines(boolean editor) {
		List<String[]> out = new ArrayList<>();
		double x = 0, y = 64, z = 0;
		float yaw = 180F;
		String biome = "Plains";
		boolean nether = false, overworld = true;

		if (mc.player != null && mc.level != null) {
			x = mc.player.getX();
			y = mc.player.getY();
			z = mc.player.getZ();
			yaw = mc.player.getYRot();
			biome = prettyBiome(mc.level.getBiome(mc.player.blockPosition()).getRegisteredName());
			nether = mc.level.dimension() == Level.NETHER;
			overworld = mc.level.dimension() == Level.OVERWORLD;
		} else if (!editor) {
			return out;
		}

		// Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east.
		int dirIndex = Math.floorMod(Math.round(yaw / 45F), 8);
		String dir = direction.is("Full") ? DIRECTION_NAMES[dirIndex] : DIRECTIONS[dirIndex];

		if (layout.is("Vertical")) {
			out.add(new String[]{"X: ", num(x)});
			out.add(new String[]{"Y: ", num(y)});
			out.add(new String[]{"Z: ", num(z)});
			if (!direction.is("Off")) out.add(new String[]{"Facing: ", dir});
		} else {
			String xyz = num(x) + ", " + num(y) + ", " + num(z);
			if (!direction.is("Off")) xyz += " (" + dir + ")";
			out.add(new String[]{"XYZ: ", xyz});
		}
		if (showBiome.get()) out.add(new String[]{"Biome: ", biome});
		if (otherDimension.get()) {
			if (nether) out.add(new String[]{"Overworld: ", num(x * 8) + ", " + num(z * 8)});
			else if (overworld) out.add(new String[]{"Nether: ", num(x / 8) + ", " + num(z / 8)});
		}
		return out;
	}

	private String num(double v) {
		if (decimals.get() == 0) return String.valueOf((int) Math.floor(v));
		return String.format(Locale.ROOT, "%." + decimals.get() + "f", v);
	}

	/** "minecraft:dark_forest" -> "Dark Forest" */
	private static String prettyBiome(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder sb = new StringBuilder();
		for (String word : path.split("_")) {
			if (word.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return sb.toString();
	}

	@Override
	public int getWidth() {
		int max = 0;
		for (String[] line : lines(true)) max = Math.max(max, font().width(line[0] + line[1]));
		return max + PADDING * 2;
	}

	@Override
	public int getHeight() {
		return Math.max(1, lines(true).size()) * LINE_H + 6;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, boolean editor) {
		List<String[]> lines = lines(editor);
		if (lines.isEmpty()) return;
		int w = getWidth();
		int h = lines.size() * LINE_H + 6;
		drawBox(graphics, x, y, w, h);

		int ty = y + 4;
		for (String[] line : lines) {
			int tx = x + PADDING;
			drawText(graphics, line[0], tx, ty, labelColor(tx, ty));
			drawText(graphics, line[1], tx + font().width(line[0]), ty, valueTextColor(tx, ty));
			ty += LINE_H;
		}
	}
}
