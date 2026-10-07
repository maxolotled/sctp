package com.snailtools.shoplogger;

import com.google.gson.Gson;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts the current shop log to the Trading Post API — no manual CSV/Excel
 * export needed. Fill in API_BASE and API_KEY after deploying the Worker
 * (see trading-post-api/README.md).
 */
public class ShopUploader {

	private static final String API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final String API_KEY = "JjabYIfRtghvBJNoy6857TFVHbjknlMOi6754E5dcfvhgBHNI6b564";

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Gson GSON = new Gson();

	private static boolean uploadInFlight = false;

	/** @param manual whether this was triggered by the upload hotkey — only manual uploads report their result in chat. */
	public static void uploadAsync(Minecraft client, boolean manual) {
		if (uploadInFlight) return;

		List<ShopEntry> entries = new ArrayList<>(ShopLog.getAll());
		List<String> scannedPositions = ShopLog.getScannedPositions();
		if (entries.isEmpty() && scannedPositions.isEmpty()) return;

		List<Map<String, Object>> rows = new ArrayList<>();
		for (ShopEntry e : entries) rows.add(toUploadRow(e));

		// scannedPositions tells the Worker which (world, position) pairs are
		// authoritative right now, so it can prune anything previously logged
		// there that's no longer present. Older mod versions never send this,
		// so the Worker just skips pruning for them — fully backward compatible.
		List<Map<String, Object>> scans = new ArrayList<>();
		for (String entry : scannedPositions) {
			int sep = entry.indexOf('|');
			if (sep < 0) continue;
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("world", entry.substring(0, sep));
			m.put("position", entry.substring(sep + 1));
			// The shop's uncollected payment at scan time (2.4+): the Worker only
			// counts a real sale when stock dropped AND this went up since the
			// previous scan of the same shop (by anyone).
			ShopLog.Payment pay = ShopLog.getPayment(entry);
			if (pay != null) {
				m.put("payment", pay.count());
				m.put("paymentCurrency", pay.currency());
				m.put("scannedAt", java.time.Instant.ofEpochMilli(pay.scannedAtMillis()).toString());
			}
			scans.add(m);
		}

		// Lets the Worker tell whether this client's scannedPositions-driven
		// removals are trustworthy — versions before the chunk-load false-removal
		// fix (see ShopAutoScanner#discoverNearbyShops) could report shops as gone
		// when they were actually just in a not-yet-loaded chunk.
		String json = GSON.toJson(Map.of("rows", rows, "scannedPositions", scans, "modVersion", modVersion()));
		uploadInFlight = true;

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(API_BASE + "/listings"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + API_KEY)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				// Was 15s — a big scan-session upload legitimately needs several
				// chunked round-trips server-side (see handleUploadListings), and
				// 15s cut it close even after parallelizing those. More headroom,
				// not a fix for anything that should routinely take this long.
				.timeout(Duration.ofSeconds(30))
				.build();

		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> client.execute(() -> {
					uploadInFlight = false;
					if (response.statusCode() == 200) {
						ShopLog.clearScannedPositions();
						// Clear the local shop log too, not just the scan-position
						// tracking — otherwise every entry ever scanned this session
						// keeps getting re-sent (and its lastSeen re-bumped) on every
						// future upload cycle forever, even for shops the player
						// hasn't actually revisited in hours. The auto-scanner's own
						// "known shop" memory (ShopAutoScanner#knownShops) is separate
						// and unaffected, so it'll keep silently rediscovering/
						// rescanning nearby known shops on its normal schedule and
						// repopulating this before the next upload. Only side effect:
						// the CSV/Excel export hotkey now reflects "since the last
						// upload" rather than the whole play session.
						ShopLog.clear();
					}
					if (!manual) return;
					if (response.statusCode() == 200) {
						ChatFormat.send(client, ChatFormat.SUCCESS, "Uploaded to Trading Post: " + response.body());
					} else {
						ChatFormat.send(client, ChatFormat.ERROR, "Upload failed (" + response.statusCode() + "): " + response.body());
					}
				}))
				.exceptionally(ex -> {
					client.execute(() -> {
						uploadInFlight = false;
						if (manual) {
							ChatFormat.send(client, ChatFormat.ERROR, "Upload error: " + ex.getMessage());
						}
					});
					return null;
				});
	}

	private static String modVersion() {
		return ModList.get().getModContainerById("shoplogger")
				.map(c -> c.getModInfo().getVersion().toString())
				.orElse("unknown");
	}

	/**
	 * Field names the website/Worker are expected to read (see index.html's
	 * normalizeRow). NOTE: "price" replaces the old "pricePerItem"/"pricePerStack"
	 * pair with a single full-stack price — the website/Worker need a matching
	 * update to read "price"/"priceLabel" instead of the removed fields.
	 */
	private static Map<String, Object> toUploadRow(ShopEntry e) {
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
}
