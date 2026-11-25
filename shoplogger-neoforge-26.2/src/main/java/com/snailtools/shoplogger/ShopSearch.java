package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.data.AveragePrices;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Handles /search <item> when the chat-output preference is chosen instead of
 * the GUI (see SearchPreferences) — looks the term up against the same shared
 * listing data the website reads (WebDataClient.fetchListings, the public GET
 * /listings) and prints matches to chat. Like everywhere else, a listing shows
 * the item's average price, not the shop's own (see AveragePrices).
 */
public final class ShopSearch {

	private static final int MAX_RESULTS = 15;

	private ShopSearch() {}

	public static void searchAsync(Minecraft client, String query) {
		String q = query.trim();
		if (q.isEmpty()) {
			message(client, ChatFormat.INFO, "Usage: /search <item name>");
			return;
		}
		ShopWorld world = WorldSelection.get();
		if (world == null) {
			message(client, ChatFormat.INFO, "Set your world first — /setworld firefly or /setworld honeybee");
			return;
		}
		message(client, ChatFormat.INFO, "Searching Trading Post for \"" + q + "\" on " + world.label() + "…");

		WebDataClient.fetchListings()
				.thenAccept(rows -> client.execute(() -> show(client, q, world, rows)))
				.exceptionally(ex -> {
					client.execute(() -> message(client, ChatFormat.ERROR, "Search failed: " + ex.getMessage()));
					return null;
				});
	}

	private static void show(Minecraft client, String query, ShopWorld world, List<Listing> rows) {
		if (rows == null) rows = List.of();
		String needle = query.toLowerCase(Locale.ROOT);
		List<Listing> matches = rows.stream()
				.filter(r -> world.label().equalsIgnoreCase(r.world))
				.filter(r -> !(SearchPreferences.hideDisplayListings() && r.isDisplay()))
				.filter(r -> matches(r, needle))
				.sorted(Comparator
						.comparing((Listing r) -> !isExactMatch(r, needle)) // exact matches first
						.thenComparingDouble(Listing::pricePerItemInDiamonds)) // then lowest price first
				.toList();

		if (matches.isEmpty()) {
			message(client, ChatFormat.INFO, "No listings found for \"" + query + "\".");
			return;
		}

		message(client, ChatFormat.INFO, matches.size() + " result" + (matches.size() == 1 ? "" : "s") + " for \"" + query + "\", exact matches and lowest price first:");
		matches.stream().limit(MAX_RESULTS).forEach(r -> message(client, ChatFormat.RESULT, formatRow(r)));
		if (matches.size() > MAX_RESULTS) {
			message(client, ChatFormat.INFO, "…and " + (matches.size() - MAX_RESULTS) + " more — refine your search to narrow it down.");
		}
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
					.append(Component.literal("Prices are averages: live prices were removed at the request of the Snailcraft admins. ").withStyle(ChatFormat.NEUTRAL))
					.append(Component.literal("[Why?]").setStyle(Style.EMPTY
							.withColor(ChatFormatting.AQUA)
							.withUnderlined(true)
							.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(AveragePrices.STATEMENT_URL)))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Read our statement on sctp.nl"))))));
		}
	}

	private static boolean matches(Listing row, String needle) {
		return lower(row.itemName).contains(needle) || lower(row.baseItem).contains(needle);
	}

	private static boolean isExactMatch(Listing row, String needle) {
		return lower(row.itemName).trim().equals(needle) || lower(row.baseItem).trim().equals(needle);
	}

	private static String lower(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}

	private static String formatRow(Listing row) {
		AveragePrices.Avg avg = AveragePrices.of(row);
		String price = "auction".equals(row.marketplaceType) ? "Auction · " + row.priceLabel
				: row.isDisplay() ? "Display only"
				: avg != null ? AveragePrices.label(avg) : "no average yet";
		return row.itemName + " — " + price + " — " + row.seller + " (" + row.world + ") — " + row.amount + " in stock";
	}

	private static void message(Minecraft client, ChatFormatting color, String text) {
		ChatFormat.send(client, color, text);
	}
}
