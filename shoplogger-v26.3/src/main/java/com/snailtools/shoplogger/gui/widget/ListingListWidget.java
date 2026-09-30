package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.ShopReporter;
import com.snailtools.shoplogger.TeleportHighlight;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrollable list of listings as cards: item icon, name/seller, world and
 * type, price and stock, plus the [!] report and [TP] teleport buttons.
 * Clicking the rest of a row opens that seller's profile.
 */
public class ListingListWidget extends AbstractSelectionList<ListingListWidget.ListingEntry> {

	public static final int ROW_HEIGHT = 26;

	public ListingListWidget(Minecraft client, int x, int y, int w, int h) {
		super(client, w, h, y, ROW_HEIGHT);
		updateSizeAndPosition(w, h, x, y);
	}

	@Override
	public int getRowWidth() {
		return Math.max(60, Math.min(620, width - 12));
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
	protected void extractSelection(GuiGraphicsExtractor g, ListingEntry entry, int color) {}

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

	public void addListingEntry(ListingEntry entry) {
		addEntry(entry);
	}

	public int size() {
		return getItemCount();
	}

	/** Use on a single item's page — item name is already known from context, so it's left off the row. */
	public static ListingEntry of(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, false, onClickSeller);
	}

	/** Use on a browse-everything view (ListingsScreen) — shows the item name on each row since it varies per row. */
	public static ListingEntry withItemName(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, true, onClickSeller);
	}

	public static final class ListingEntry extends AbstractSelectionList.Entry<ListingEntry> {
		private static final int BTN = 16;
		private static final Pattern POSITION_PATTERN = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");

		private final Listing listing;
		private final boolean showItemName;
		private final java.util.function.Consumer<String> onClickSeller;
		private final BlockPos teleportTarget; // null if listing.position isn't parseable coordinates
		private final boolean canReport; // marketplace rows have no shop to report
		private final ItemStack icon;
		private boolean reported;

		private ListingEntry(Listing listing, boolean showItemName, java.util.function.Consumer<String> onClickSeller) {
			this.listing = listing;
			this.showItemName = showItemName;
			this.onClickSeller = onClickSeller;
			this.teleportTarget = parsePosition(listing.position);
			this.canReport = !listing.marketplace;
			this.reported = canReport && ShopReporter.alreadyReported(ShopReporter.fromListing(listing));
			this.icon = Draw.stackFor(listing.baseItem);
		}

		/**
		 * listing.position is BlockPos#toShortString() ("x, y, z") for scanned
		 * listings, but admin-added manual listings can carry arbitrary free
		 * text — return null rather than guessing so the button just doesn't
		 * render for those instead of misbehaving.
		 */
		private static BlockPos parsePosition(String position) {
			if (position == null) return null;
			Matcher m = POSITION_PATTERN.matcher(position.trim());
			if (!m.matches()) return null;
			try {
				return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		private int btnY() { return getY() + (getHeight() - BTN) / 2; }
		private int tpX() { return getX() + getWidth() - 5 - BTN; }
		private int reportX() { return (teleportTarget != null ? tpX() - BTN - 3 : getX() + getWidth() - 5 - BTN); }
		private int buttonsLeft() {
			if (canReport) return reportX();
			if (teleportTarget != null) return tpX();
			return getX() + getWidth() - 2;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			var font = Minecraft.getInstance().font;
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			UiLists.rowCard(g, x, y, w, h, hovered);

			Draw.iconSlot(g, x + 4, y + (h - 20) / 2, 16, icon.isEmpty() ? null : icon, null);

			int tx = x + 28;
			int rightEdge = buttonsLeft() - 6;
			String price = listing.priceLabel + " / " + listing.stackSize;
			String stock = "x" + listing.amount + " (" + listing.stacksInStock + ")";
			int priceW = Math.max(font.width(price), font.width(stock));
			int textW = rightEdge - priceW - 8 - tx;

			String type = listing.bulk ? "Bulk" : listing.bundled ? "Bundled" : "Single";
			if (showItemName) {
				g.text(font, Draw.trim(font, listing.itemName, textW), tx, y + 5, Theme.TEXT, false);
				int sx = tx;
				String seller = Draw.trim(font, listing.seller, Math.max(20, textW / 2));
				g.text(font, seller, sx, y + 15, Theme.ACCENT, false);
				sx += font.width(seller) + 4;
				sx = subParts(g, font, sx, y + 15, tx + textW, type);
			} else {
				g.text(font, Draw.trim(font, listing.seller, textW), tx, y + 5, Theme.ACCENT, false);
				subParts(g, font, tx, y + 15, tx + textW, type);
			}

			Draw.right(g, font, price, rightEdge, y + 5, Theme.SHELL);
			Draw.right(g, font, stock, rightEdge, y + 15, Theme.MUTED);

			if (canReport) {
				boolean over = UiLists.rowButton(g, reportX(), btnY(), BTN, BTN, reported ? "✓" : "!",
						reported ? Theme.PANEL_ALT : 0xFF6E2A22, reported ? Theme.PANEL_HI : Theme.BAD, Theme.TEXT, mouseX, mouseY);
				if (over) g.setTooltipForNextFrame(font, Component.literal(reported ? "Already reported" : "Report this listing (wrong price, gone, fake...)"), mouseX, mouseY);
			}
			if (teleportTarget != null) {
				boolean over = UiLists.rowButton(g, tpX(), btnY(), BTN, BTN, "TP", Theme.GO, 0xFF3C8A5A, Theme.TEXT, mouseX, mouseY);
				if (over) g.setTooltipForNextFrame(font, Component.literal("Teleport: /shop " + listing.seller + ", then a beam points to the chest"), mouseX, mouseY);
			}
		}

		/** " · World · Type" after the seller, with a coloured dot for the world. */
		private int subParts(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int sx, int y, int maxX, String type) {
			if (sx + 30 > maxX) return sx;
			g.fill(sx, y + 3, sx + 3, y + 6, Theme.worldColor(listing.world));
			sx += 6;
			String world = listing.world == null ? "?" : listing.world;
			g.text(font, world, sx, y, Theme.MUTED, false);
			sx += font.width(world);
			String t = " · " + type;
			if (sx + font.width(t) <= maxX) {
				g.text(font, t, sx, y, Theme.FAINT, false);
				sx += font.width(t);
			}
			return sx;
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (canReport && UiLists.over(reportX(), btnY(), BTN, BTN, event.x(), event.y())) {
				if (!reported) {
					reported = ShopReporter.report(Minecraft.getInstance(), ShopReporter.fromListing(listing)) || ShopReporter.alreadyReported(ShopReporter.fromListing(listing));
				}
				return true;
			}
			if (teleportTarget != null && UiLists.over(tpX(), btnY(), BTN, BTN, event.x(), event.y())) {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null && client.getConnection() != null) {
					client.getConnection().sendCommand("shop " + listing.seller);
				}
				TeleportHighlight.getInstance().arm(listing.world, teleportTarget);
				return true;
			}
			if (onClickSeller != null) onClickSeller.accept(listing.seller);
			return true;
		}
	}
}
