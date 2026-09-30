package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
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
	// A scan doesn't upload synchronously — items it just read aren't actually
	// stamped with a real availableSince server-side until the next periodic
	// upload (ShopLoggerClient's 15-minute timer, or a manual upload). Without
	// this, items first discovered THIS visit would get an availableSince
	// landing after the timestamp just recorded for this same visit, making
	// them look "new since you were last here" on the very next check — even
	// though nothing has actually changed since you saw them yourself. Padding
	// the recorded timestamp forward absorbs that lag; safe to be smaller than
	// COOLDOWN_MS (it only ever needs to cover the WORST-case upload delay,
	// not the gap between two real checks, which COOLDOWN_MS already
	// guarantees is at least an hour).
	private static final long UPLOAD_LAG_GRACE_MS = 20 * 60 * 1000L; // 20 minutes
	private static final String CONFIG_ENABLED = "visitAlerts/enabled";
	private static final String CONFIG_RARES_ONLY = "visitAlerts/raresOnly";
	private static final String CONFIG_SHOP_INFO_ON_VISIT = "visitAlerts/shopInfoOnVisit";

	private ShopVisitAlert() {}

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	public static boolean isRaresOnly() {
		return Config.getOrCreate(CONFIG_RARES_ONLY, Boolean.class, false);
	}

	public static void setRaresOnly(boolean value) {
		Config.update(CONFIG_RARES_ONLY, value);
	}

	/** Off by default — an extra server command per shop visit is easy chat clutter to not want by surprise. */
	public static boolean isShopInfoOnVisitEnabled() {
		return Config.getOrCreate(CONFIG_SHOP_INFO_ON_VISIT, Boolean.class, false);
	}

	public static void setShopInfoOnVisitEnabled(boolean value) {
		Config.update(CONFIG_SHOP_INFO_ON_VISIT, value);
	}

	private static String configKey(String world, String seller) {
		return "shopVisits/" + world.toLowerCase(Locale.ROOT) + "|" + seller.toLowerCase(Locale.ROOT);
	}

	/** Call after any scan (manual or silent) that read a valid sign — world/seller come straight off that sign. */
	public static void maybeAlert(Minecraft client, String world, String seller) {
		if (world == null || seller == null || !isEnabled()) return;

		String key = configKey(world, seller);
		long now = System.currentTimeMillis();
		Long stored = Config.get(key, Long.class);
		boolean firstVisit = stored == null;
		if (!firstVisit && now - stored < COOLDOWN_MS) return; // on cooldown — do nothing at all, not even a silent check

		// Consumed immediately, win or lose — a failed fetch below just means
		// this particular visit doesn't get a message; it doesn't retry until
		// the next hour, same as if the check had never happened. Padded by
		// UPLOAD_LAG_GRACE_MS — see its comment for why.
		Config.update(key, now + UPLOAD_LAG_GRACE_MS);

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

	private static void report(Minecraft client, String seller, List<Listing> sellerListings, List<RareItem> rareCatalog, long previousVisit) {
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

	// ---------------- shop info on visit ----------------
	// Deliberately independent of the "what's new" check above: its own
	// toggle, its own per-(world, seller) cooldown/config namespace, and not
	// nested inside maybeAlert() — so it fires on its own regardless of
	// whether "New-item alerts" is enabled, and isn't skipped by that check's
	// early-return. Delayed by SHOP_INFO_DELAY_MS so it doesn't fire the
	// instant a container's opened, before the client's even settled.

	private static final long SHOP_INFO_COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour, same idea as COOLDOWN_MS but its own clock
	private static final long SHOP_INFO_DELAY_MS = 2000L;
	private static final List<Long> pendingShopInfoFireTimes = new ArrayList<>();

	private static String shopInfoConfigKey(String world, String seller) {
		return "shopInfoOnVisit/lastRun/" + world.toLowerCase(Locale.ROOT) + "|" + seller.toLowerCase(Locale.ROOT);
	}

	/** Call after any scan (manual or silent) that read a valid sign — same call sites as maybeAlert(), but entirely separate state/gating. */
	public static void maybeSendShopInfo(String world, String seller) {
		if (world == null || seller == null || !isShopInfoOnVisitEnabled()) return;

		String key = shopInfoConfigKey(world, seller);
		long now = System.currentTimeMillis();
		Long last = Config.get(key, Long.class);
		if (last != null && now - last < SHOP_INFO_COOLDOWN_MS) return;
		Config.update(key, now);

		pendingShopInfoFireTimes.add(now + SHOP_INFO_DELAY_MS);
	}

	/** Call every client tick — fires any queued "shops plot info" command whose delay has elapsed. */
	public static void tick(Minecraft client) {
		if (pendingShopInfoFireTimes.isEmpty() || client.getConnection() == null) return;
		// Never while a silent open is in flight: any command counts as "the
		// player may be opening a screen" (see ShopAutoScanner#onPlayerMayOpenScreen),
		// which would release that open and pop the chest up on the player's
		// screen. Wait for it to finish; the next tick is soon enough.
		if (SilentScreenCoordinator.isArmed()) return;
		long now = System.currentTimeMillis();
		Iterator<Long> it = pendingShopInfoFireTimes.iterator();
		while (it.hasNext()) {
			long fireAt = it.next();
			if (now >= fireAt) {
				client.getConnection().sendCommand("shops plot info");
				it.remove();
			}
		}
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
