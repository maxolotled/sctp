package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.TeleportHighlight;
import com.snailtools.shoplogger.gui.data.Listing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.util.math.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scrollable list of listings — seller, world, price, stock, and (optionally) item name. Click a row to open that seller's profile. */
public class ListingListWidget extends EntryListWidget<ListingListWidget.ListingEntry> {

	public ListingListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
		super(client, width, height, y, itemHeight);
	}

	@Override
	public int getRowWidth() {
		return Math.min(420, width - 20);
	}

	@Override
	public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		// no accessibility narration for this first pass
	}

	public void clearAllEntries() {
		clearEntries();
	}

	public void addListingEntry(ListingEntry entry) {
		addEntry(entry);
	}

	/** Use on a single item's page — item name is already known from context, so it's left off the row. */
	public static ListingEntry of(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, false, onClickSeller);
	}

	/** Use on a browse-everything view (ListingsScreen) — shows the item name on each row since it varies per row. */
	public static ListingEntry withItemName(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, true, onClickSeller);
	}

	public static final class ListingEntry extends EntryListWidget.Entry<ListingEntry> {
		// Reserved column at the row's right edge for the "Teleport" button —
		// price/stock text is right-aligned to the left of it instead of the
		// row's true edge, so nothing overlaps.
		private static final int TELEPORT_BUTTON_WIDTH = 18;
		private static final Pattern POSITION_PATTERN = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");

		private final Listing listing;
		private final boolean showItemName;
		private final java.util.function.Consumer<String> onClickSeller;
		private final BlockPos teleportTarget; // null if listing.position isn't parseable coordinates

		private ListingEntry(Listing listing, boolean showItemName, java.util.function.Consumer<String> onClickSeller) {
			this.listing = listing;
			this.showItemName = showItemName;
			this.onClickSeller = onClickSeller;
			this.teleportTarget = parsePosition(listing.position);
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
				return new BlockPos(
						Integer.parseInt(m.group(1)),
						Integer.parseInt(m.group(2)),
						Integer.parseInt(m.group(3)));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		@Override
		public void render(DrawContext context, int index, int rowY, boolean hovered, float tickDelta) {
			var tr = MinecraftClient.getInstance().textRenderer;
			int x = getX() + 4;
			int y = getY();
			int textRightEdge = getX() + getWidth() - (teleportTarget != null ? TELEPORT_BUTTON_WIDTH + 4 : 0);

			if (hovered) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
			}

			String type = listing.bulk ? "Bulk" : listing.bundled ? "Bundled" : "Single";
			if (showItemName) {
				context.drawTextWithShadow(tr, listing.itemName, x, y + 2, 0xFFFFFFFF);
				context.drawTextWithShadow(tr, listing.seller + " - " + listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			} else {
				context.drawTextWithShadow(tr, listing.seller, x, y + 2, 0xFFB7E23D);
				context.drawTextWithShadow(tr, listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			}

			String price = listing.priceLabel + " / " + listing.stackSize;
			int priceWidth = tr.getWidth(price);
			context.drawTextWithShadow(tr, price, textRightEdge - priceWidth - 6, y + 2, 0xFFD9C89A);

			String stock = "x" + listing.amount + " (" + listing.stacksInStock + ")";
			int stockWidth = tr.getWidth(stock);
			context.drawTextWithShadow(tr, stock, textRightEdge - stockWidth - 6, y + 12, 0xFF8FA593);

			if (teleportTarget != null) {
				int btnX = getX() + getWidth() - TELEPORT_BUTTON_WIDTH;
				context.fill(btnX, getY(), getX() + getWidth(), getY() + getHeight(), 0xFF2E6B45);
				context.drawCenteredTextWithShadow(tr, "TP", btnX + TELEPORT_BUTTON_WIDTH / 2, getY() + getHeight() / 2 - 4, 0xFFFFFFFF);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
			if (teleportTarget != null) {
				int btnX = getX() + getWidth() - TELEPORT_BUTTON_WIDTH;
				if (click.x() >= btnX && click.x() < getX() + getWidth()
						&& click.y() >= getY() && click.y() < getY() + getHeight()) {
					MinecraftClient client = MinecraftClient.getInstance();
					if (client.player != null && client.getNetworkHandler() != null) {
						client.getNetworkHandler().sendChatCommand("shop " + listing.seller);
					}
					TeleportHighlight.getInstance().arm(listing.world, teleportTarget);
					return true;
				}
			}
			if (onClickSeller != null) onClickSeller.accept(listing.seller);
			return true;
		}
	}
}
