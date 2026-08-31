package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.MinecraftClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "What's new at this shop since your last visit?" — fires at most once per
 * hour per (world, seller), the first time either the manual or silent scan
 * path reads a valid sign there. Last-visited times live entirely in the
 * player's own local config (same file WorldSelection etc. already use) —
 * the server is never told who's visiting whose shop, only ever asked for
 * the shop's own public listing data it already serves everyone.
 */
public final class ShopVisitAlert {

	private static final long COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour
	private static final String CONFIG_RARES_ONLY = "visitAlerts/raresOnly";

	private ShopVisitAlert() {}

	public static boolean isRaresOnly() {
		return Config.getOrCreate(CONFIG_RARES_ONLY, Boolean.class, false);
	}

	public static void setRaresOnly(boolean value) {
		Config.update(CONFIG_RARES_ONLY, value);
	}

	private static String configKey(String world, String seller) {
		return "shopVisits/" + world.toLowerCase(Locale.ROOT) + "|" + seller.toLowerCase(Locale.ROOT);
	}

	/** Call after any scan (manual or silent) that read a valid sign — world/seller come straight off that sign. */
	public static void maybeAlert(MinecraftClient client, String world, String seller) {
		if (world == null || seller == null) return;

		String key = configKey(world, seller);
		long now = System.currentTimeMillis();
		Long stored = Config.get(key, Long.class);
		boolean firstVisit = stored == null;
		if (!firstVisit && now - stored < COOLDOWN_MS) return; // on cooldown — do nothing at all, not even a silent check

		// Consumed immediately, win or lose — a failed fetch below just means
		// this particular visit doesn't get a message; it doesn't retry until
		// the next hour, same as if the check had never happened.
		Config.update(key, now);
		if (firstVisit) return; // nothing to compare against yet — just start tracking

		long previousVisit = stored;
		WebDataClient.fetchListings()
				.thenCombine(WebDataClient.fetchRareCatalog(), FetchResult::new)
				.thenAccept(res -> client.execute(() -> report(client, seller, filterSeller(res.listings, world, seller), res.rares, previousVisit)))
				.exceptionally(ex -> null);
	}

	private record FetchResult(List<Listing> listings, List<RareItem> rares) {}

	private static List<Listing> filterSeller(List<Listing> all, String world, String seller) {
		List<Listing> mine = new ArrayList<>();
		for (Listing l : all) {
			if (seller.equalsIgnoreCase(l.seller) && world.equalsIgnoreCase(l.world)) mine.add(l);
		}
		return mine;
	}

	private static void report(MinecraftClient client, String seller, List<Listing> sellerListings, List<RareItem> rareCatalog, long previousVisit) {
		boolean raresOnly = isRaresOnly();
		List<String> newRares = new ArrayList<>();
		List<String> newOthers = new ArrayList<>();

		for (Listing l : sellerListings) {
			Instant since = parseInstant(l.availableSince);
			if (since == null || since.toEpochMilli() <= previousVisit) continue; // not new, or unknown first-seen date

			if (isRareItem(l.itemName, rareCatalog)) {
				newRares.add(l.itemName);
			} else if (!raresOnly) {
				newOthers.add(l.itemName);
			}
		}

		if (newRares.isEmpty() && newOthers.isEmpty()) {
			ChatFormat.send(client, ChatFormat.NEUTRAL, "No new items at " + seller + "'s shop since your last visit.");
			return;
		}

		List<String> ordered = new ArrayList<>(newRares); // rares first
		ordered.addAll(newOthers);
		ChatFormat.send(client, ChatFormat.SUCCESS, "New at " + seller + "'s shop: " + String.join(", ", ordered));
	}

	private static boolean isRareItem(String itemName, List<RareItem> rareCatalog) {
		for (RareItem r : rareCatalog) {
			if (MatchUtil.isRareNameMatch(itemName, r.name)) return true;
		}
		return false;
	}

	private static Instant parseInstant(String s) {
		if (s == null || s.isEmpty()) return null;
		try {
			return Instant.parse(s);
		} catch (Exception e) {
			return null;
		}
	}
}
