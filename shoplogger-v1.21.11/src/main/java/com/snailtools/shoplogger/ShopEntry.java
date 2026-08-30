package com.snailtools.shoplogger;

import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** One row of the shop log: a single item type seen in a single shop. */
public record ShopEntry(
		String itemName,        // display name, e.g. "Enchanted Golden Apple"
		String baseItem,        // registry id, e.g. "minecraft:golden_apple"
		int amountAvailable,    // total count of this item currently in stock
		int price,              // raw price straight off the sign, unscaled
		int stackSize,          // how many the shop stocks per slot — what the price above buys
		double stacksInStock,   // amountAvailable / stackSize, rounded to 2 decimals
		boolean bulk,           // true if this item was found inside a shulker box
		boolean bundled,        // true if this item was found inside a bundle
		String currency,        // currency type from the sign, e.g. "diamond"
		String seller,
		String world,           // "Firefly" or "Honeybee" — see WorldSelection
		BlockPos containerPos,
		long lastSeenEpochMillis
) {
	private static final Map<String, String> CURRENCY_ABBREVIATIONS = Map.of(
			"diamond", "dia",
			"diamondblock", "db"
	);

	/**
	 * Dedup key: same seller selling the same item on the same world counts as
	 * "the same shop listing" even if the container moves (rebuilt, or a
	 * different double-chest half gets scanned) — the newest scan always
	 * replaces the older one. World is part of the key because the same
	 * seller can run shops on both worlds independently. bulk/bundled are
	 * also part of the key so a seller selling both a normal-priced stack AND
	 * a bulk/bundled batch of the same item ends up as two distinct listings
	 * instead of one overwriting the other.
	 */
	public String key() {
		return world + "|" + seller + "|" + baseItem + "|" + itemName + "|" + bulk + "|" + bundled;
	}

	/** Human-readable price, e.g. "2 dia" or "1 db" — or "DISPLAY" for a [DISPLAY] sign with no real price. */
	public String priceLabel() {
		if (ShopSign.DISPLAY_CURRENCY.equalsIgnoreCase(currency)) {
			return "DISPLAY";
		}
		return price + " " + CURRENCY_ABBREVIATIONS.getOrDefault(currency, currency);
	}
}
