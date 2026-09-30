package com.snailtools.shoplogger.gui.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item name -> rare texture, so any list that shows shop listings can draw a
 * rare's own texture instead of the vanilla item it's made from. The rare
 * catalog is fetched once in the background; until it arrives (or if it
 * can't be fetched) callers just get null and keep their vanilla icon.
 */
public final class RareIcons {

	private static final long RETRY_MS = 60_000L;

	private static volatile Map<String, String> textureByName = null;
	private static long lastRequestAt = 0L;
	private static boolean inFlight = false;

	private RareIcons() {}

	/** True once the catalog has loaded, i.e. a null from textureFor() really means "not a rare". */
	public static boolean isLoaded() {
		ensureLoaded();
		return textureByName != null;
	}

	/** The rare texture (site path, e.g. "/items/textures/rare_image1.png") for this item name, or null. */
	public static String textureFor(String itemName) {
		ensureLoaded();
		Map<String, String> map = textureByName;
		if (map == null || itemName == null) return null;
		return map.get(MatchUtil.rareNormalize(itemName));
	}

	private static synchronized void ensureLoaded() {
		if (textureByName != null || inFlight) return;
		long now = System.currentTimeMillis();
		if (now - lastRequestAt < RETRY_MS && lastRequestAt != 0L) return;
		lastRequestAt = now;
		inFlight = true;
		WebDataClient.fetchRareCatalog().thenAccept(RareIcons::index).whenComplete((v, ex) -> {
			synchronized (RareIcons.class) {
				inFlight = false;
			}
		});
	}

	private static void index(List<RareItem> items) {
		Map<String, String> map = new HashMap<>();
		for (RareItem it : items) {
			if (it.name == null || it.texture == null || it.texture.isEmpty()) continue;
			map.putIfAbsent(MatchUtil.rareNormalize(it.name), it.texture);
		}
		textureByName = map;
	}
}
