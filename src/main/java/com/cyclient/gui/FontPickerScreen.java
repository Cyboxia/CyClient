package com.cyclient.gui;

import com.cyclient.config.Config;
import com.cyclient.setting.StringSetting;
import com.cyclient.util.FontPreviewCache;
import com.cyclient.util.MouseButtons;
import com.cyclient.util.Render2D;
import com.cyclient.util.SystemFonts;
import com.cyclient.util.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Pick a font from the fonts installed on this computer (for the menu, the HUD, or one widget).
 * Type to search, scroll the list, click a font to apply it instantly.
 * The first entry is the "default" choice (an empty path), e.g. Minecraft's font or "use the HUD font".
 */
public class FontPickerScreen extends Screen {
	private static final int ROW_H = 14;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFF8B929D;
	private static final String PREVIEW = "The quick brown fox jumps over the lazy dog  0123456789";

	private final Screen parent;
	private final String title;
	private final StringSetting target;       // where the chosen font file path is stored
	private final String defaultLabel;        // what the empty choice means
	private final Supplier<Font> currentFont; // the font as it will actually be used (for the preview)
	private final Supplier<String> currentError;
	private final FontPreviewCache previews = new FontPreviewCache();
	private String query = "";
	private int scroll;
	private int maxScroll;

	// Layout, recalculated every frame
	private int px, py, pw, ph, listY1, listY2;

	public FontPickerScreen(Screen parent, String title, StringSetting target, String defaultLabel,
							Supplier<Font> currentFont, Supplier<String> currentError) {
		super(Component.literal(title));
		this.parent = parent;
		this.title = title;
		this.target = target;
		this.defaultLabel = defaultLabel;
		this.currentFont = currentFont;
		this.currentError = currentError;
	}

	@Override
	protected void init() {
		super.init();
		this.minecraft.textInputManager().startTextInput(this); // needed to receive typed characters in 26.x
	}

	@Override
	public void removed() {
		this.minecraft.textInputManager().stopTextInput(this);
		previews.close(); // free every preview font when the picker closes
		super.removed();
	}

	/** "Minecraft (default)" first (null), then matching fonts sorted by name. */
	private List<File> entries() {
		String q = query.toLowerCase(Locale.ROOT);
		List<File> list = new ArrayList<>();
		for (File f : SystemFonts.list()) {
			if (q.isEmpty() || SystemFonts.name(f).toLowerCase(Locale.ROOT).contains(q)
					|| f.getName().toLowerCase(Locale.ROOT).contains(q)) list.add(f);
		}
		list.sort(Comparator.comparing(f -> SystemFonts.name(f).toLowerCase(Locale.ROOT)));
		if (q.isEmpty() || defaultLabel.toLowerCase(Locale.ROOT).contains(q)) list.add(0, null);
		return list;
	}

	private void layout() {
		pw = Math.min(360, this.width - 20);
		ph = Math.min(290, this.height - 20);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		listY1 = py + 46;
		listY2 = py + ph - 58;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		layout();
		int r = Theme.radius();
		Render2D.roundedRect(g, px, py, px + pw, py + ph, r + 2, Theme.panel());

		g.text(this.font, title, px + 10, py + 10, Theme.accent(), true);
		String count = SystemFonts.list().size() + " fonts found";
		g.text(this.font, count, px + pw - 10 - this.font.width(count), py + 10, TEXT_DIM, false);

		// Search box
		int sx1 = px + 10, sx2 = px + pw - 10, sy = py + 24;
		Render2D.roundedRect(g, sx1, sy, sx2, sy + 16, Math.min(3, r), Theme.control());
		boolean cursor = (System.currentTimeMillis() / 500) % 2 == 0;
		if (query.isEmpty()) g.text(this.font, "Type to search fonts...", sx1 + 5, sy + 4, TEXT_DIM, false);
		else g.text(this.font, query + (cursor ? "_" : ""), sx1 + 5, sy + 4, TEXT, false);

		// Font list
		List<File> list = entries();
		maxScroll = Math.max(0, list.size() * ROW_H - (listY2 - listY1));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		String current = target.get();

		previews.beginFrame();
		g.enableScissor(px + 6, listY1, px + pw - 6, listY2);
		for (int i = 0; i < list.size(); i++) {
			int y = listY1 + i * ROW_H - scroll;
			if (y + ROW_H < listY1 || y > listY2) continue;
			File f = list.get(i);
			boolean selected = f == null ? current.isEmpty() : f.getPath().equals(current);
			boolean hover = Render2D.inside(mouseX, mouseY, px + 6, Math.max(y, listY1), px + pw - 6, Math.min(y + ROW_H, listY2));
			if (selected || hover) {
				Render2D.roundedRect(g, px + 8, y, px + pw - 8, y + ROW_H - 1, Math.min(3, r),
						selected ? Theme.accent() : Theme.cardHover());
			}
			String name = f == null ? defaultLabel : SystemFonts.name(f);
			int color = selected ? Theme.textOnAccent() : TEXT;
			int dim = selected ? color : TEXT_DIM;

			// Each name is drawn in its own font once loaded (Minecraft's font until then).
			Font preview = f == null ? null : previews.get(f);
			boolean unsupported = f != null && previews.isUnsupported(f);
			g.text(preview != null ? preview : this.font, name, px + 14, y + 3, unsupported ? dim : color, false);

			String tag = unsupported ? "No preview" : (hover && preview != null ? name : null);
			if (tag != null) { // on hover, also show the name in a readable font (for symbol fonts)
				g.text(this.font, tag, px + pw - 14 - this.font.width(tag), y + 3, dim, false);
			}
		}
		g.disableScissor();
		previews.endFrame();

		// Preview in the chosen font (or an error if it couldn't load)
		int prevY = listY2 + 6;
		Render2D.roundedRect(g, px + 8, prevY, px + pw - 8, prevY + 24, Math.min(3, r), Theme.card());
		String error = currentError.get();
		if (error != null && !current.isEmpty()) {
			g.text(this.font, "Can't use this font: " + error, px + 14, prevY + 8, 0xFFFF5555, false);
		} else {
			g.enableScissor(px + 10, prevY, px + pw - 10, prevY + 24);
			g.text(currentFont.get(), PREVIEW, px + 14, prevY + 8, TEXT, false);
			g.disableScissor();
		}

		// Done button
		int bw = 80, bx = px + (pw - bw) / 2, by = py + ph - 24;
		boolean doneHover = Render2D.inside(mouseX, mouseY, bx, by, bx + bw, by + 16);
		Render2D.roundedRect(g, bx, by, bx + bw, by + 16, Math.min(3, r), doneHover ? Theme.shade(Theme.accent(), 25) : Theme.accent());
		g.text(this.font, "Done", bx + (bw - this.font.width("Done")) / 2, by + 4, Theme.textOnAccent(), false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!MouseButtons.isLeft(event.button())) return super.mouseClicked(event, doubleClick);
		double mx = event.x(), my = event.y();

		int bw = 80, bx = px + (pw - bw) / 2, by = py + ph - 24;
		if (Render2D.inside(mx, my, bx, by, bx + bw, by + 16)) {
			onClose();
			return true;
		}

		if (Render2D.inside(mx, my, px + 6, listY1, px + pw - 6, listY2)) {
			int index = (int) ((my - listY1 + scroll) / ROW_H);
			List<File> list = entries();
			if (index >= 0 && index < list.size()) {
				File f = list.get(index);
				target.set(f == null ? "" : f.getPath());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(scrollY * ROW_H * 3)));
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int c = event.codepoint();
		if (c >= 32 && c != 127 && query.length() < 32) {
			query += Character.toString(c);
			scroll = 0;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			scroll = 0;
			return true;
		}
		return super.keyPressed(event); // Esc -> onClose
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
