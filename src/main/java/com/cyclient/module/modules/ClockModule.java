package com.cyclient.module.modules;

import com.cyclient.module.TextHudModule;
import com.cyclient.setting.BooleanSetting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends TextHudModule {
	public final BooleanSetting twentyFourHour = add(new BooleanSetting("24 Hour", false));
	public final BooleanSetting showSeconds = add(new BooleanSetting("Show Seconds", false));
	public final BooleanSetting showDate = add(new BooleanSetting("Show Date", false));

	public ClockModule() {
		super("Clock", "Shows your real-world time.", false, 4, 76, false);
	}

	@Override
	protected String getValue(boolean editor) {
		String pattern = twentyFourHour.get()
				? (showSeconds.get() ? "HH:mm:ss" : "HH:mm")
				: (showSeconds.get() ? "h:mm:ss a" : "h:mm a");
		if (showDate.get()) pattern = "MMM d, " + pattern;
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
	}
}
