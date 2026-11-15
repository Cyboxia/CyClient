package com.cyclient.module.modules;

import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends TextHudModule {
	public final BooleanSetting colorByPing = add(new BooleanSetting("Color By Ping", true));
	public final BooleanSetting hideSingleplayer = add(new BooleanSetting("Hide In Singleplayer", true));

	private int lastPing;

	public PingModule() {
		super("Ping", "Shows your connection latency to the server.", true, 4, 40, true);
	}

	@Override
	protected String getValue(boolean editor) {
		if (editor) return currentPing() + "ms";
		if (mc.player == null) return null;
		if (hideSingleplayer.get() && mc.hasSingleplayerServer()) return null;
		return currentPing() + "ms";
	}

	private int currentPing() {
		ClientPacketListener connection = mc.getConnection();
		if (mc.player == null || connection == null) return 0;
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		lastPing = info == null ? 0 : info.getLatency();
		return lastPing;
	}

	@Override
	protected int colorValue(String value, int defaultColor) {
		if (!colorByPing.get()) return defaultColor;
		return trafficLight(lastPing < 80, lastPing < 150);
	}
}
