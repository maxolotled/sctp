package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

/**
 * A text field in the site's style: a rounded frame (lime border when
 * focused) with an optional search glyph and a unit suffix, around a
 * borderless vanilla EditBox. Add frame() before box to a screen.
 */
public final class UiField {

	public final EditBox box;
	private final int x, y, w, h;
	private final boolean searchIcon;
	private String suffix;

	public UiField(Font font, int x, int y, int w, int h, String hint, boolean searchIcon) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.searchIcon = searchIcon;
		int left = searchIcon ? 18 : 7;
		box = new EditBox(font, x + left, y + (h - 8) / 2, w - left - 6, 10, Component.literal(hint));
		box.setBordered(false);
		box.setMaxLength(128);
		box.setTextColor(Theme.TEXT);
		box.setHint(Component.literal(hint).withColor(Theme.FAINT & 0xFFFFFF));
	}

	/** Shows a unit ("DB", "min") at the right edge of the field. */
	public UiField suffix(String suffix, Font font) {
		this.suffix = suffix;
		box.setWidth(w - (searchIcon ? 18 : 7) - 10 - font.width(suffix));
		return this;
	}

	public Renderable frame() {
		return (g, mouseX, mouseY, delta) -> {
			boolean focused = box.isFocused();
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			Draw.card(g, x, y, w, h, Theme.SLOT, focused ? Theme.ACCENT_DIM : hover ? Theme.LINE : Theme.LINE_SOFT);
			if (searchIcon) Draw.glyph(g, Draw.GLYPH_SEARCH, x + 7, y + (h - 7) / 2, focused ? Theme.ACCENT : Theme.MUTED);
			if (suffix != null) {
				var font = net.minecraft.client.Minecraft.getInstance().font;
				Draw.right(g, font, suffix, x + w - 7, y + (h - 8) / 2, Theme.MUTED);
			}
		};
	}

	public String value() {
		return box.getValue();
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
