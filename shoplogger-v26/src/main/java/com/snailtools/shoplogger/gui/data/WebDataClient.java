package com.snailtools.shoplogger.gui.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only counterpart to ShopUploader — GET-only, no auth (all of these are
 * the same public unauthenticated endpoints the website itself calls), used
 * to power the in-game item library GUI. Same HttpClient/Gson pattern as
 * ShopUploader, just without ever needing to serialize an outgoing body.
 */
public final class WebDataClient {

	private static final String API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final String SITE_BASE = "https://sctp.nl";

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Gson GSON = new Gson();

	private WebDataClient() {}

	public static CompletableFuture<List<Listing>> fetchListings() {
		return getJson(API_BASE + "/listings", new TypeToken<List<Listing>>() {}.getType());
	}

	public static CompletableFuture<List<HistoryPoint>> fetchItemHistory(String itemKey) {
		String url = API_BASE + "/items/history?itemKey=" + URLEncoder.encode(itemKey, StandardCharsets.UTF_8);
		return getJson(url, new TypeToken<List<HistoryPoint>>() {}.getType());
	}

	public static CompletableFuture<List<VanillaItem>> fetchVanillaCatalog() {
		return getJson(SITE_BASE + "/data/vanilla-items.json", new TypeToken<List<VanillaItem>>() {}.getType());
	}

	public static CompletableFuture<List<RareItem>> fetchRareCatalog() {
		return getJson(SITE_BASE + "/data/rare-items.json", new TypeToken<List<RareItem>>() {}.getType());
	}

	public static CompletableFuture<List<SharedShop>> fetchSharedShops() {
		return getJson(API_BASE + "/shared-shops", new TypeToken<List<SharedShop>>() {}.getType());
	}

	public static CompletableFuture<RareRentals> fetchRareRentals() {
		return getJson(API_BASE + "/rare-items", RareRentals.class);
	}

	public static CompletableFuture<List<MarketplaceListing>> fetchMarketplaceListings() {
		return getJson(API_BASE + "/marketplace/listings", new TypeToken<List<MarketplaceListing>>() {}.getType());
	}

	/** Also marks the returned notifications as delivered server-side — call at most once per join. */
	public static CompletableFuture<List<MarketplaceNotification>> fetchMarketplaceNotifications(String mcUsername) {
		String url = API_BASE + "/marketplace/notifications/for-mc?mcUsername=" + URLEncoder.encode(mcUsername, StandardCharsets.UTF_8);
		return getJson(url, new TypeToken<List<MarketplaceNotification>>() {}.getType());
	}

	/** Downloads raw bytes — used for item/avatar textures (not JSON). */
	public static CompletableFuture<byte[]> fetchBytes(String url) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(15))
				.build();
		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> {
					if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
					return response.body();
				});
	}

	private static <T> CompletableFuture<T> getJson(String url, java.lang.reflect.Type type) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(15))
				.build();
		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
					return (T) GSON.fromJson(response.body(), type);
				});
	}
}
