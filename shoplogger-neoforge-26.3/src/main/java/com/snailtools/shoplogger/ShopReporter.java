package com.snailtools.shoplogger;

import com.google.gson.Gson;
import com.snailtools.shoplogger.gui.data.Listing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-click "this listing looks wrong" reports straight from the game — the
 * same POST /reports the website's Report button uses, but with no reason to
 * pick: it always goes in as "other" and the admin queue tells it apart by the
 * "[In-game report]" details prefix. Each listing can only be reported once
 * per game session, so mashing the button can't flood the queue.
 *
 * Chat buttons (watchlist alerts) can't carry a whole listing in a click
 * command, so they register the listing here and run "/watchreport <id>".
 */
public final class ShopReporter {

	private static final String API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final String API_KEY = "JjabYIfRtghvBJNoy6857TFVHbjknlMOi6754E5dcfvhgBHNI6b564";
	private static final int MAX_PENDING = 200;

	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();

	private static final Set<String> SENT_KEYS = ConcurrentHashMap.newKeySet();
	private static final Map<Integer, Map<String, Object>> PENDING = new LinkedHashMap<>();
	private static int nextId = 1;

	private ShopReporter() {}

	/** Same key the Worker stores a listing under — see worker.js rowKey(). */
	public static String rowKey(Map<String, Object> l) {
		return (l.get("world") + "|" + l.get("seller") + "|" + l.get("baseItem") + "|" + l.get("itemName")
				+ "|" + (Boolean.TRUE.equals(l.get("bulk")) ? 1 : 0) + "|" + (Boolean.TRUE.equals(l.get("bundled")) ? 1 : 0))
				.toLowerCase(Locale.ROOT);
	}

	public static Map<String, Object> fromListing(Listing l) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("itemName", l.itemName);
		m.put("baseItem", l.baseItem);
		m.put("bulk", l.bulk);
		m.put("bundled", l.bundled);
		m.put("mixedContents", l.mixedContents);
		m.put("price", l.price);
		m.put("priceLabel", l.priceLabel);
		m.put("stackSize", l.stackSize);
		m.put("amount", l.amount);
		m.put("stacksInStock", l.stacksInStock);
		m.put("currency", l.currency);
		m.put("seller", l.seller);
		m.put("world", l.world);
		m.put("position", l.position);
		m.put("lastSeen", l.lastSeen);
		return m;
	}

	public static Map<String, Object> fromEntry(ShopEntry e) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("itemName", e.itemName());
		m.put("baseItem", e.baseItem());
		m.put("bulk", e.bulk());
		m.put("bundled", e.bundled());
		m.put("mixedContents", false);
		m.put("price", e.price());
		m.put("priceLabel", e.priceLabel());
		m.put("stackSize", e.stackSize());
		m.put("amount", e.amountAvailable());
		m.put("stacksInStock", e.stacksInStock());
		m.put("currency", e.currency());
		m.put("seller", e.seller());
		m.put("world", e.world());
		m.put("position", e.containerPos().toShortString());
		m.put("lastSeen", Instant.ofEpochMilli(e.lastSeenEpochMillis()).toString());
		return m;
	}

	public static boolean alreadyReported(Map<String, Object> listing) {
		return SENT_KEYS.contains(rowKey(listing));
	}

	/** For a chat [Report] button: stores the listing and returns the id its "/watchreport <id>" click command carries. */
	public static synchronized int register(Map<String, Object> listing) {
		int id = nextId++;
		PENDING.put(id, listing);
		if (PENDING.size() > MAX_PENDING) {
			Integer oldest = PENDING.keySet().iterator().next();
			PENDING.remove(oldest);
		}
		return id;
	}

	/** The red [Report] chat button — hover explains it, click sends the report straight away. */
	public static Component buildChatButton(Map<String, Object> listing) {
		int id = register(listing);
		return Component.literal("[Report]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/watchreport " + id))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Report this listing as wrong or outdated"))));
	}

	public static void reportPending(Minecraft client, int id) {
		Map<String, Object> listing;
		synchronized (ShopReporter.class) {
			listing = PENDING.get(id);
		}
		if (listing == null) {
			ChatFormat.send(client, ChatFormat.ERROR, "That report link has expired — use the website's Report button instead.");
			return;
		}
		report(client, listing);
	}

	/** Sends the report in the background and confirms in chat. Returns false if this listing was already reported this session. */
	public static boolean report(Minecraft client, Map<String, Object> listing) {
		String key = rowKey(listing);
		if (!SENT_KEYS.add(key)) {
			ChatFormat.send(client, ChatFormat.NEUTRAL, "You already reported that listing.");
			return false;
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("listingKey", key);
		body.put("listing", listing);
		body.put("reason", "other");
		String who = client.getUser() != null ? client.getUser().getName() : "unknown";
		body.put("details", "[In-game report] sent from the SCTP mod by " + who);
		body.put("reporter", who); // if you can deal with reports, the Worker accepts it straight away

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(API_BASE + "/reports"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + API_KEY)
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
				.timeout(Duration.ofSeconds(15))
				.build();

		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> client.execute(() -> {
					if (response.statusCode() == 200 && response.body().contains("\"autoAccepted\":true")) {
						ChatFormat.send(client, ChatFormat.SUCCESS, "Listing removed: you can deal with reports, so your report was accepted straight away.");
					} else if (response.statusCode() == 200) {
						ChatFormat.send(client, ChatFormat.SUCCESS, "Report sent — thanks for flagging that listing.");
					} else {
						SENT_KEYS.remove(key); // let them retry
						ChatFormat.send(client, ChatFormat.ERROR, "Couldn't send the report (HTTP " + response.statusCode() + ").");
					}
				}))
				.exceptionally(ex -> {
					SENT_KEYS.remove(key);
					client.execute(() -> ChatFormat.send(client, ChatFormat.ERROR, "Couldn't send the report: " + ex.getMessage()));
					return null;
				});
		return true;
	}
}
