package com.snailtools.shoplogger.gui.data;

import java.util.Map;

/** Mirrors one entry from GET /marketplace/listings — see worker.js's handleGetMarketplaceListings. */
public class MarketplaceListing {

	// Same real player-market rates as Listing.java/worker.js's CURRENCY_VALUE.
	private static final Map<String, Double> CURRENCY_VALUE = Map.ofEntries(
			Map.entry("diamond", 1.0), Map.entry("diamondblock", 9.0),
			Map.entry("iron", 1.0 / 64), Map.entry("ironingot", 1.0 / 64), Map.entry("ironblock", 9.0 / 64),
			Map.entry("gold", 1.0 / 18), Map.entry("goldingot", 1.0 / 18), Map.entry("goldblock", 9.0 / 18),
			Map.entry("netherite", 18.0), Map.entry("netheriteingot", 18.0), Map.entry("netheriteblock", 162.0)
	);

	public String id;
	public String type; // "selling" or "lookingFor"
	public String itemName;
	public String baseItem;
	public String world;
	public int quantity;
	public String notes;
	public Double askingPrice;
	public String askingCurrency;
	public Double startingBid;
	public String startingBidCurrency;
	public Double budget;
	public String budgetCurrency;
	public String createdAt;
	public String expiresAt;
	public String seller;
	public boolean sellerVerified;
	public int bidCount;
	public Bid highestBid;

	public static class Bid {
		public double amount;
		public String currency;
	}

	/** Best available price info as {amount, currency, label}, or null if none set (a lookingFor post with no budget, or a selling post with neither an asking price nor a starting bid). */
	public PriceInfo priceInfo() {
		if ("selling".equals(type)) {
			if (askingPrice != null) return new PriceInfo(askingPrice, askingCurrency, "asking");
			if (startingBid != null) return new PriceInfo(startingBid, startingBidCurrency, "starting bid");
			return null;
		}
		if (budget != null) return new PriceInfo(budget, budgetCurrency, "budget");
		return null;
	}

	/** Diamonds-equivalent value of priceInfo(), or POSITIVE_INFINITY if no price is set — so a "no price" post never slips under a watchlist max-price cap by mistake. */
	public double diamondValue() {
		PriceInfo info = priceInfo();
		if (info == null) return Double.POSITIVE_INFINITY;
		double mult = CURRENCY_VALUE.getOrDefault(info.currency == null ? "" : info.currency.toLowerCase(), 1.0);
		return info.amount * mult;
	}

	public static class PriceInfo {
		public final double amount;
		public final String currency;
		public final String label;

		public PriceInfo(double amount, String currency, String label) {
			this.amount = amount;
			this.currency = currency;
			this.label = label;
		}
	}
}
