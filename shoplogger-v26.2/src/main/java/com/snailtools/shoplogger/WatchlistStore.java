package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only list of items the player wants to be notified about, each with
 * its own optional max-price cap and "skip display/no-price listings" toggle
 * — see WatchlistScreen (add/remove/edit UI, X menu) and WatchlistAlert (the
 * scan-time chat notification). Stored as a plain WatchedItem[] (not
 * List&lt;WatchedItem&gt;) because Config's Class&lt;T&gt;-based API can't
 * deserialize a generic List correctly, but arrays are fine.
 */
public final class WatchlistStore {

	private static final String CONFIG_PATH = "watchlist/items";

	private WatchlistStore() {}

	public static List<WatchedItem> getAll() {
		WatchedItem[] stored = Config.getOrCreate(CONFIG_PATH, WatchedItem[].class, new WatchedItem[0]);
		List<WatchedItem> out = new ArrayList<>();
		for (WatchedItem w : stored) {
			if (w != null && w.itemName != null && !w.itemName.isBlank()) out.add(w);
		}
		return out;
	}

	public static WatchedItem find(String name) {
		String normalized = MatchUtil.alphaOnly(name);
		if (normalized.isEmpty()) return null;
		for (WatchedItem w : getAll()) {
			if (MatchUtil.alphaOnly(w.itemName).equals(normalized)) return w;
		}
		return null;
	}

	public static boolean isWatching(String name) {
		return find(name) != null;
	}

	public static void add(String name) {
		if (name == null || name.isBlank() || isWatching(name)) return;
		List<WatchedItem> all = getAll();
		all.add(new WatchedItem(name));
		Config.update(CONFIG_PATH, all.toArray(new WatchedItem[0]));
	}

	public static void remove(String name) {
		String normalized = MatchUtil.alphaOnly(name);
		List<WatchedItem> all = getAll();
		all.removeIf(w -> MatchUtil.alphaOnly(w.itemName).equals(normalized));
		Config.update(CONFIG_PATH, all.toArray(new WatchedItem[0]));
	}

	/** Updates an existing entry's options in place — no-op if it's not being watched. */
	public static void updateOptions(String name, Double maxPrice, boolean excludeNoPriceOrDisplay) {
		String normalized = MatchUtil.alphaOnly(name);
		List<WatchedItem> all = getAll();
		for (WatchedItem w : all) {
			if (MatchUtil.alphaOnly(w.itemName).equals(normalized)) {
				w.maxPrice = maxPrice;
				w.excludeNoPriceOrDisplay = excludeNoPriceOrDisplay;
				break;
			}
		}
		Config.update(CONFIG_PATH, all.toArray(new WatchedItem[0]));
	}
}
