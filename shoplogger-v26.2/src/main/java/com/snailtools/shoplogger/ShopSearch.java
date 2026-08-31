package com.snailtools.shoplogger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Handles /search <item> when the chat-output preference is chosen instead of
 * the GUI (see SearchPreferences) — looks the term up against the same shared
 * listing data the website reads, via the Trading Post Worker's public GET
 * /listings endpoint (backed by D1, edge-cached ~30s), no auth needed since
 * it's a public read. Prints matches to chat.
 */
public final class ShopSearch {

	private static final String DATA_URL = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev/listings";
	private static final int MAX_RESULTS = 15;

	// Same convention as index.html's price sort/filter fix: normalize to a
	// common "worth in diamonds" basis before comparing across currencies.
	// Real player-market rates: 64 iron = 1 diamond, 18 gold = 1 diamond, 1
	// netherite ingot = 18 diamonds — block variants use the standard
	// 9-per-block crafting ratio.
	private static final Map<String, Double> CURRENCY_VALUE = Map.ofEntries(
			Map.entry("diamond", 1.0), Map.entry("diamondblock", 9.0),
			Map.entry("iron", 1.0 / 64), Map.entry("ironingot", 1.0 / 64), Map.entry("ironblock", 9.0 / 64),
			Map.entry("gold", 1.0 / 18), Map.entry("goldingot", 1.0 / 18), Map.entry("goldblock", 9.0 / 18),
			Map.entry("netherite", 18.0), Map.entry("netheriteingot", 18.0), Map.entry("netheriteblock", 162.0)
	);

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Gson GSON = new Gson();
	private static final Type LISTINGS_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

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

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(DATA_URL))
				.GET()
				.timeout(Duration.ofSeconds(15))
				.build();

		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> client.execute(() -> handleResponse(client, q, world, response)))
				.exceptionally(ex -> {
					client.execute(() -> message(client, ChatFormat.ERROR, "Search failed: " + ex.getMessage()));
					return null;
				});
	}

	private static void handleResponse(Minecraft client, String query, ShopWorld world, HttpResponse<String> response) {
		if (response.statusCode() != 200) {
			message(client, ChatFormat.ERROR, "Search failed (HTTP " + response.statusCode() + ")");
			return;
		}

		List<Map<String, Object>> rows;
		try {
			rows = GSON.fromJson(response.body(), LISTINGS_TYPE);
		} catch (Exception e) {
			message(client, ChatFormat.ERROR, "Search failed: couldn't read Trading Post data (" + e.getMessage() + ")");
			return;
		}
		if (rows == null) rows = List.of();

		String needle = query.toLowerCase();
		List<Map<String, Object>> matches = rows.stream()
				.filter(r -> sameWorld(r, world))
				.filter(r -> matches(r, needle))
				.sorted(Comparator
						.comparing((Map<String, Object> r) -> !isExactMatch(r, needle)) // exact matches first
						.thenComparingDouble(ShopSearch::priceInDiamonds)) // then lowest price first
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
	}

	private static boolean sameWorld(Map<String, Object> row, ShopWorld world) {
		return str(row, "world").equalsIgnoreCase(world.label());
	}

	private static boolean matches(Map<String, Object> row, String needle) {
		String itemName = str(row, "itemName").toLowerCase();
		String baseItem = str(row, "baseItem").toLowerCase();
		return itemName.contains(needle) || baseItem.contains(needle);
	}

	private static boolean isExactMatch(Map<String, Object> row, String needle) {
		String itemName = str(row, "itemName").trim().toLowerCase();
		String baseItem = str(row, "baseItem").trim().toLowerCase();
		return itemName.equals(needle) || baseItem.equals(needle);
	}

	private static double priceInDiamonds(Map<String, Object> row) {
		double price = num(row, "price");
		double mult = CURRENCY_VALUE.getOrDefault(str(row, "currency").toLowerCase(), 1.0);
		return price * mult;
	}

	private static String formatRow(Map<String, Object> row) {
		String priceLabel = str(row, "priceLabel");
		String stackSize = fmt(num(row, "stackSize"));
		String seller = str(row, "seller");
		String world = str(row, "world");
		String amount = fmt(num(row, "amount"));

		return str(row, "itemName") + " — " + priceLabel + " (per " + stackSize + ") — "
				+ seller + " (" + world + ") — " + amount + " in stock";
	}

	private static String str(Map<String, Object> row, String key) {
		Object v = row.get(key);
		return v == null ? "" : String.valueOf(v);
	}

	private static double num(Map<String, Object> row, String key) {
		Object v = row.get(key);
		return v instanceof Number n ? n.doubleValue() : 0.0;
	}

	private static String fmt(double n) {
		return n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
	}

	private static void message(Minecraft client, ChatFormatting color, String text) {
		ChatFormat.send(client, color, text);
	}
}
