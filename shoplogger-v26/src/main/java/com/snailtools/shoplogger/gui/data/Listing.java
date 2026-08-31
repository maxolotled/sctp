package com.snailtools.shoplogger.gui.data;

import java.util.Map;

/** Mirrors one row from GET /listings — see worker.js's `listings` table. */
public class Listing {

	// Real player-market rates: 64 iron = 1 diamond, 18 gold = 1 diamond, 1
	// netherite ingot = 18 diamonds — block variants use the standard
	// 9-per-block crafting ratio. Same table as the website's CURRENCY_VALUE.
	// Unrecognized currencies (emerald, coal, ...) fall back to 1:1 with diamond.
	private static final Map<String, Double> CURRENCY_VALUE = Map.ofEntries(
			Map.entry("diamond", 1.0), Map.entry("diamondblock", 9.0),
			Map.entry("iron", 1.0 / 64), Map.entry("ironingot", 1.0 / 64), Map.entry("ironblock", 9.0 / 64),
			Map.entry("gold", 1.0 / 18), Map.entry("goldingot", 1.0 / 18), Map.entry("goldblock", 9.0 / 18),
			Map.entry("netherite", 18.0), Map.entry("netheriteingot", 18.0), Map.entry("netheriteblock", 162.0)
	);
	public String itemName;
	public String baseItem;
	public boolean bulk;
	public boolean bundled;
	public boolean mixedContents;
	public double price;
	public String priceLabel;
	public int stackSize;
	public int amount;
	public double stacksInStock;
	public String currency;
	public String seller;
	public String world;
	public String position;
	public String lastSeen;
	public String availableSince; // null for listings that predate this field

	/** Price per single item in diamonds — same basis as the website's priceInDiamonds()/stackSize. */
	public double pricePerItemInDiamonds() {
		if ("display".equalsIgnoreCase(currency)) return Double.POSITIVE_INFINITY;
		double mult = CURRENCY_VALUE.getOrDefault(currency == null ? "" : currency.toLowerCase(), 1.0);
		int size = stackSize <= 0 ? 1 : stackSize;
		return (price * mult) / size;
	}
}
