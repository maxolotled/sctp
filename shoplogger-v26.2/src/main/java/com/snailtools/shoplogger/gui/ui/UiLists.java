package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look for the scrollable lists (ListingListWidget, ItemListWidget):
 * card rows, small in-row action buttons and a slim rounded scrollbar. The
 * lists themselves override the vanilla background/separator/scrollbar
 * hooks and call into these.
 */
public final class UiLists {

	private UiLists() {}

	/** Standard row card: rounded, lighter on hover, 2px gap between rows. */
	public static void rowCard(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		Draw.card(g, x, y + 1, w, h - 2, hovered ? Theme.PANEL_HI : Theme.PANEL, hovered ? Theme.LINE : Theme.LINE_SOFT);
	}

	/** Small rounded action button inside a row; returns true if the mouse is on it. */
	public static boolean rowButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int bg, int bgHover, int fg, int mouseX, int mouseY) {
		boolean hover = over(x, y, w, h, mouseX, mouseY);
		Draw.round(g, x, y, w, h, hover ? bgHover : bg);
		var font = Minecraft.getInstance().font;
		Draw.centered(g, font, label, x + w / 2, y + (h - 8) / 2, fg);
		return hover;
	}

	public static boolean over(int x, int y, int w, int h, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Slim scrollbar: a faint track and a rounded thumb that lights up on hover. */
	public static void scrollbar(GuiGraphicsExtractor g, int x, int top, int h, int thumbY, int thumbH, int mouseX, int mouseY) {
		Draw.round(g, x + 1, top, 2, h, Theme.LINE_SOFT);
		boolean hover = mouseX >= x - 2 && mouseX <= x + 5 && mouseY >= top && mouseY <= top + h;
		Draw.round(g, x, thumbY, 4, thumbH, hover ? Theme.ACCENT_DIM : Theme.LINE);
	}
}
