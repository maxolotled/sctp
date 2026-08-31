package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only list of item display names the player wants to be notified
 * about — see WatchlistScreen (add/remove UI, X menu) and WatchlistAlert
 * (the scan-time chat notification). Stored as a plain String[] (not
 * List&lt;String&gt;) because Config's Class&lt;T&gt;-based API can't
 * deserialize a generic List correctly, but arrays are fine.
 */
public final class WatchlistStore {

	private static final String CONFIG_PATH = "watchlist/items";

	private WatchlistStore() {}

	public static List<String> getAll() {
		String[] stored = Config.getOrCreate(CONFIG_PATH, String[].class, new String[0]);
		List<String> out = new ArrayList<>();
		for (String s : stored) {
			if (s != null && !s.isBlank()) out.add(s);
		}
		return out;
	}

	public static boolean isWatching(String name) {
		String normalized = MatchUtil.alphaOnly(name);
		if (normalized.isEmpty()) return false;
		for (String watched : getAll()) {
			if (MatchUtil.alphaOnly(watched).equals(normalized)) return true;
		}
		return false;
	}

	public static void add(String name) {
		if (name == null || name.isBlank() || isWatching(name)) return;
		List<String> all = getAll();
		all.add(name);
		Config.update(CONFIG_PATH, all.toArray(new String[0]));
	}

	public static void remove(String name) {
		String normalized = MatchUtil.alphaOnly(name);
		List<String> all = getAll();
		all.removeIf(watched -> MatchUtil.alphaOnly(watched).equals(normalized));
		Config.update(CONFIG_PATH, all.toArray(new String[0]));
	}
}
