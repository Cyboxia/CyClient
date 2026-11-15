package com.cyclient.util;

import com.cyclient.CyClient;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.FontDescription;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * Turns the bytes of a .ttf/.otf file into a Minecraft Font, using the same FreeType steps
 * vanilla uses for resource-pack TrueType fonts. Must be called on the render thread.
 */
public final class FontLoader {
	private static int counter;

	private FontLoader() {}

	/** A loaded font plus the resources to free when it's no longer needed. */
	public record Loaded(Font font, FontSet set, GlyphProvider provider) implements AutoCloseable {
		@Override
		public void close() {
			set.close();
			provider.close();
		}
	}

	public static Loaded load(byte[] bytes, float size, int oversample, float offsetY) throws IOException {
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes).flip();
		FT_Face face = null;
		GlyphProvider provider = null;
		try {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer pointer = stack.mallocPointer(1);
				FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buffer, 0L, pointer), "Initializing font face");
				face = FT_Face.create(pointer.get());
			}

			String format = FreeType.FT_Get_Font_Format(face);
			if (!"TrueType".equals(format)) {
				throw new IOException("This font uses " + format + " outlines; only TrueType outlines are supported");
			}

			// Minecraft's glyph baker only accepts 8-bit grayscale outlines. Color/emoji fonts (COLR, CBDT,
			// sbix) and bitmap-only fonts render as color or bitmap glyphs and crash deep inside the renderer
			// when a glyph bakes, which we can't catch there. Reject them up front instead.
			long faceFlags = face.face_flags();
			if ((faceFlags & FreeType.FT_FACE_FLAG_COLOR) != 0) {
				throw new IOException("Color/emoji fonts aren't supported");
			}
			if ((faceFlags & FreeType.FT_FACE_FLAG_SCALABLE) == 0) {
				throw new IOException("Bitmap fonts aren't supported");
			}

			FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");

			// From here the provider owns the buffer and face, and frees them when closed.
			provider = new TrueTypeGlyphProvider(buffer, face, size, oversample, 0F, offsetY, "");
			buffer = null;
			face = null;

			FontSet set = new FontSet(new GlyphStitcher(Minecraft.getInstance().getTextureManager(), CyClient.id("custom_font_" + (counter++))));
			set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
			Font font = new Font(new Font.Provider() {
				@Override
				public GlyphSource glyphs(FontDescription description) {
					return set.source(false);
				}

				@Override
				public EffectGlyph effect() {
					return set.whiteGlyph();
				}
			});
			return new Loaded(font, set, provider);
		} catch (IOException | RuntimeException e) {
			if (provider != null) provider.close();
			if (face != null) FreeType.FT_Done_Face(face);
			if (buffer != null) MemoryUtil.memFree(buffer);
			throw e;
		}
	}
}
