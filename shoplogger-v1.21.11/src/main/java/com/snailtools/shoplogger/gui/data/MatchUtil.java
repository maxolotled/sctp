package com.snailtools.shoplogger.gui.data;

/** Mirrors 404.html's rareNormalize/isRareNameMatch and itemKey conventions. */
public final class MatchUtil {

	private MatchUtil() {}

	public static String rareNormalize(String s) {
		if (s == null) return "";
		return s.toLowerCase().replaceAll("[^a-z0-9']", "");
	}

	/** Same rule as the website: normalized-exact-match only — see 404.html's isRareNameMatch comment for why. */
	public static boolean isRareNameMatch(String a, String b) {
		String na = rareNormalize(a);
		String nb = rareNormalize(b);
		return !na.isEmpty() && na.equals(nb);
	}

	/** Matches worker.js's computeDailySnapshots() — "v:<baseItem>|<name>", lowercased. */
	public static String vanillaItemKey(String baseItem, String name) {
		return "v:" + (baseItem == null ? "" : baseItem.toLowerCase()) + "|" + (name == null ? "" : name.toLowerCase());
	}

	/** Matches index.html's alphaOnly() — used against the /rare-items rentable-name lists (stricter, a-z only, than rareNormalize above). */
	public static String alphaOnly(String s) {
		if (s == null) return "";
		return s.toLowerCase().replaceAll("[^a-z]", "");
	}
}
