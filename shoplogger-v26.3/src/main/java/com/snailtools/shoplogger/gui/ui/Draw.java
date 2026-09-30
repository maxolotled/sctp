package com.snailtools.shoplogger.gui.ui;

import com.snailtools.shoplogger.gui.RemoteTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Small drawing kit behind every screen: rounded cards and pills built from
 * plain fills (radius 2), scaled text, item/texture icons, tiny pixel-art
 * glyphs and the on/off switch. Nothing here keeps state.
 */
public final class Draw {

	private Draw() {}

	private static final Identifier PLACEHOLDER_ICON = Identifier.fromNamespaceAndPath("minecraft", "textures/item/barrier.png");

	// ---- shapes ----------------------------------------------------------

	public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		g.fill(x, y, x + w, y + h, color);
	}

	/** Filled rectangle with 2px rounded corners. */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		if (w < 5 || h < 5) { rect(g, x, y, w, h, color); return; }
		g.fill(x + 2, y, x + w - 2, y + 1, color);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	/** A card: rounded fill with a 1px rounded border. */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		round(g, x, y, w, h, border);
		round(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Soft drop shadow for floating cards. */
	public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		round(g, x + 1, y + 3, w, h, 0x30000000);
		round(g, x, y + 2, w, h, 0x28000000);
	}

	public static void hline(GuiGraphicsExtractor g, int x, int y, int w, int color) {
		rect(g, x, y, w, 1, color);
	}

	/** Vertical gradient, top colour to bottom colour. */
	public static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		if (w <= 0 || h <= 0) return;
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	// ---- text ------------------------------------------------------------

	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
	}

	public static void textShadow(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, true);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
		g.text(font, s, cx - font.width(s) / 2, y, color, false);
	}

	public static void right(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int color) {
		g.text(font, s, rightX - font.width(s), y, color, false);
	}

	/** Text drawn at a larger (or smaller) size, top-left anchored at (x, y). */
	public static void scaled(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale, boolean shadow) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.text(font, s, 0, 0, color, shadow);
		pose.popMatrix();
	}

	/** Cuts s down (with "...") so it fits in maxWidth pixels. */
	public static String trim(Font font, String s, int maxWidth) {
		if (s == null) return "";
		if (maxWidth <= 0) return "";
		if (font.width(s) <= maxWidth) return s;
		String cut = s;
		while (cut.length() > 1 && font.width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
		return cut + "...";
	}

	/** A rounded label with padding; returns its width. */
	public static int pill(GuiGraphicsExtractor g, Font font, String s, int x, int y, int bg, int fg) {
		int w = font.width(s) + 8;
		round(g, x, y, w, 11, bg);
		g.text(font, s, x + 4, y + 2, fg, false);
		return w;
	}

	public static int pillWidth(Font font, String s) {
		return font.width(s) + 8;
	}

	/** A pill with a small coloured dot in front, e.g. a world name. */
	public static int dotPill(GuiGraphicsExtractor g, Font font, String s, int x, int y, int dot, int bg, int fg) {
		int w = font.width(s) + 15;
		round(g, x, y, w, 11, bg);
		g.fill(x + 4, y + 4, x + 7, y + 7, dot);
		g.text(font, s, x + 10, y + 2, fg, false);
		return w;
	}

	// ---- icons -----------------------------------------------------------

	/** ItemStack for a registry id like "minecraft:diamond", or EMPTY. */
	public static ItemStack stackFor(String baseItem) {
		if (baseItem == null || baseItem.isEmpty()) return ItemStack.EMPTY;
		Identifier id = Identifier.tryParse(baseItem);
		if (id == null) return ItemStack.EMPTY;
		return stack(BuiltInRegistries.ITEM.getValue(id));
	}

	/**
	 * ItemStack for an item, or EMPTY if that isn't possible yet. Item data
	 * components are only bound once a world's registries have loaded, so
	 * building a stack on e.g. the title screen throws — icons just show as
	 * empty slots there instead of crashing the menu.
	 */
	public static ItemStack stack(Item item) {
		if (item == null) return ItemStack.EMPTY;
		try {
			if (!item.builtInRegistryHolder().areComponentsBound()) return ItemStack.EMPTY;
			return new ItemStack(item);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	/** Draws an item at any multiple of 16px. */
	public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
		if (stack == null || stack.isEmpty()) return;
		if (size == 16) { g.item(stack, x, y); return; }
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 16f, size / 16f);
		g.item(stack, 0, 0);
		pose.popMatrix();
	}

	/** Draws a website texture (fetched and cached), or a placeholder while it loads. */
	public static void remoteTexture(GuiGraphicsExtractor g, String url, int x, int y, int size) {
		Identifier tex = url == null ? null : RemoteTextureCache.get(Minecraft.getInstance(), url, () -> {});
		g.blit(RenderPipelines.GUI_TEXTURED, tex != null ? tex : PLACEHOLDER_ICON, x, y, 0, 0, size, size, size, size);
	}

	public static void texture(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, size, size);
	}

	/** A dark inventory-style slot with an icon inside it: item if given, else the remote texture, else empty. */
	public static void iconSlot(GuiGraphicsExtractor g, int x, int y, int size, ItemStack stack, String textureUrl) {
		round(g, x, y, size + 4, size + 4, Theme.SLOT);
		if (stack != null && !stack.isEmpty()) item(g, stack, x + 2, y + 2, size);
		else if (textureUrl != null) remoteTexture(g, textureUrl, x + 2, y + 2, size);
	}

	// ---- pixel glyphs ----------------------------------------------------

	/** Snail-shell spiral, the mod's logo mark. */
	public static final String[] GLYPH_SPIRAL = {
			".#######.",
			"#.......#",
			"#.#####.#",
			"#.#...#.#",
			"#.#.#.#.#",
			"#.#.###.#",
			"#.#.....#",
			"#.#######",
			"#........",
	};
	public static final String[] GLYPH_SEARCH = {
			".###...",
			"#...#..",
			"#...#..",
			"#...#..",
			".###...",
			"....##.",
			".....##",
	};
	public static final String[] GLYPH_LINK = {
			"...####",
			".....##",
			"....#.#",
			"#..#..#",
			"#.#....",
			"#......",
			"####...",
	};

	public static void glyph(GuiGraphicsExtractor g, String[] rows, int x, int y, int color) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			int start = -1;
			for (int c = 0; c <= row.length(); c++) {
				boolean on = c < row.length() && row.charAt(c) == '#';
				if (on && start < 0) start = c;
				if (!on && start >= 0) { g.fill(x + start, y + r, x + c, y + r + 1, color); start = -1; }
			}
		}
	}

	/** The logo: a lime rounded square with the spiral in it. */
	public static void logo(GuiGraphicsExtractor g, int x, int y) {
		round(g, x, y, 15, 15, Theme.ACCENT);
		glyph(g, GLYPH_SPIRAL, x + 3, y + 3, Theme.ACCENT_INK);
	}

	// ---- controls --------------------------------------------------------

	/** On/off switch, 20x10. */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hover) {
		int track = on ? (hover ? Theme.ACCENT_HI : Theme.ACCENT) : (hover ? Theme.LINE : Theme.LINE_SOFT);
		round(g, x, y, 20, 10, track);
		int kx = on ? x + 11 : x + 1;
		round(g, kx, y + 1, 8, 8, on ? Theme.ACCENT_INK : Theme.MUTED);
	}

	/** Three pulsing dots, for "loading". */
	public static void spinner(GuiGraphicsExtractor g, int cx, int y, int color) {
		long t = System.currentTimeMillis() / 180;
		for (int i = 0; i < 3; i++) {
			int a = ((t + i) % 3 == 0) ? 255 : 90;
			g.fill(cx - 7 + i * 5, y, cx - 4 + i * 5, y + 3, Theme.alpha(color, a));
		}
	}

	/** Friendly centred empty/loading/error state inside a box. */
	public static void emptyState(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, String hint, int titleColor) {
		int cy = y + h / 2 - (hint == null ? 4 : 9);
		centered(g, font, trim(font, title, w - 16), x + w / 2, cy, titleColor);
		if (hint != null) centered(g, font, trim(font, hint, w - 16), x + w / 2, cy + 12, Theme.FAINT);
	}
}
