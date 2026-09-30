package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Scrollable list of item cards — used by both item libraries, the
 * watchlist and the marketplace. Vanilla entries render the real in-game
 * item icon (looked up by baseItem); rare entries render their custom
 * texture, fetched and cached the first time they're shown (see
 * RemoteTextureCache). Rows can carry a coloured badge and a "View" button.
 */
public class ItemListWidget extends AbstractSelectionList<ItemListWidget.ItemEntry> {

	public static final int ROW_HEIGHT = 24;

	public ItemListWidget(Minecraft client, int x, int y, int w, int h) {
		super(client, w, h, y, ROW_HEIGHT);
		updateSizeAndPosition(w, h, x, y);
	}

	@Override
	public int getRowWidth() {
		return Math.max(60, Math.min(520, width - 12));
	}

	@Override
	protected int scrollBarX() {
		return Math.min(getRowRight() + 3, getX() + width - 4);
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor g) {}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor g) {}

	@Override
	protected void extractSelection(GuiGraphicsExtractor g, ItemEntry entry, int color) {}

	@Override
	protected void extractScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (scrollable()) UiLists.scrollbar(g, scrollBarX(), getY(), getHeight(), scrollBarY(), scrollerHeight(), mouseX, mouseY);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		// no accessibility narration for this first pass
	}

	public void clearAllEntries() {
		clearEntries();
	}

	public void addItemEntry(ItemEntry entry) {
		addEntry(entry);
	}

	public int size() {
		return getItemCount();
	}

	public static ItemEntry forVanilla(String name, String baseItem, Runnable onClick) {
		return forVanilla(name, baseItem, null, onClick);
	}

	public static ItemEntry forVanilla(String name, String baseItem, String subtitle, Runnable onClick) {
		return forVanilla(name, baseItem, subtitle, onClick, null);
	}

	/** onOpenPage, if given, draws a secondary "View" button at the row's right edge — see ItemEntry. */
	public static ItemEntry forVanilla(String name, String baseItem, String subtitle, Runnable onClick, Runnable onOpenPage) {
		return new ItemEntry(name, subtitle, Draw.stackFor(baseItem), null, onClick, onOpenPage);
	}

	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick) {
		return forRare(name, category, textureUrl, onClick, null);
	}

	/** onOpenPage, if given, draws a secondary "View" button at the row's right edge — see ItemEntry. */
	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick, Runnable onOpenPage) {
		return new ItemEntry(name, category, ItemStack.EMPTY, textureUrl, onClick, onOpenPage);
	}

	public static final class ItemEntry extends AbstractSelectionList.Entry<ItemEntry> {
		// Secondary button at the row's right edge — jumps straight to the
		// item's detail page (current listings, price history) instead of
		// whatever the whole-row click does (add / options / open).
		private static final int VIEW_W = 34;

		private final String name;
		private final String subtitle;
		private final ItemStack vanillaIcon;
		private final String textureUrl;
		private final Runnable onClick;
		private final Runnable onOpenPage;
		private String badge;
		private int badgeColor = Theme.ACCENT;

		private ItemEntry(String name, String subtitle, ItemStack vanillaIcon, String textureUrl, Runnable onClick, Runnable onOpenPage) {
			this.name = name;
			this.subtitle = subtitle;
			this.vanillaIcon = vanillaIcon;
			this.textureUrl = textureUrl;
			this.onClick = onClick;
			this.onOpenPage = onOpenPage;
		}

		/** A small coloured label on the row, e.g. "Watching" or "Selling". */
		public ItemEntry withBadge(String text, int color) {
			this.badge = text;
			this.badgeColor = color;
			return this;
		}

		private int viewX() { return getX() + getWidth() - 5 - VIEW_W; }
		private int viewY() { return getY() + (getHeight() - 14) / 2; }

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			var font = Minecraft.getInstance().font;
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			UiLists.rowCard(g, x, y, w, h, hovered);
			Draw.iconSlot(g, x + 4, y + (h - 20) / 2, 16, vanillaIcon.isEmpty() ? null : vanillaIcon, vanillaIcon.isEmpty() ? textureUrl : null);

			int right = onOpenPage != null ? viewX() - 6 : x + w - 14;
			if (badge != null) {
				int bw = Draw.pillWidth(font, badge);
				right -= bw;
				Draw.pill(g, font, badge, right, y + (h - 11) / 2, Theme.alpha(badgeColor, 0x33), badgeColor);
				right -= 6;
			}

			int tx = x + 28;
			int textW = right - tx;
			if (subtitle != null && !subtitle.isEmpty()) {
				g.text(font, Draw.trim(font, name, textW), tx, y + 3, Theme.TEXT, false);
				g.text(font, Draw.trim(font, subtitle, textW), tx, y + 13, Theme.MUTED, false);
			} else {
				g.text(font, Draw.trim(font, name, textW), tx, y + (h - 8) / 2, Theme.TEXT, false);
			}

			if (onOpenPage != null) {
				boolean over = UiLists.rowButton(g, viewX(), viewY(), VIEW_W, 14, "View", Theme.PANEL_ALT, Theme.ACCENT_DIM, Theme.TEXT, mouseX, mouseY);
				if (over) g.setTooltipForNextFrame(font, Component.literal("Open this item's page: listings and price history"), mouseX, mouseY);
			} else {
				g.text(font, "›", x + w - 10, y + (h - 8) / 2, hovered ? Theme.ACCENT : Theme.FAINT, false);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (onOpenPage != null && UiLists.over(viewX(), viewY(), VIEW_W, 14, event.x(), event.y())) {
				onOpenPage.run();
				return true;
			}
			if (onClick != null) onClick.run();
			return true;
		}
	}
}
