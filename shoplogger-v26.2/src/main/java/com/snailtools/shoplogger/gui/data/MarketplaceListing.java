package com.snailtools.shoplogger.gui.data;

/** Mirrors one entry from GET /marketplace/listings — see worker.js's handleGetMarketplaceListings. */
public class MarketplaceListing {

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

	/** Best available price info as {amount, currency, label}, or null if none set (shouldn't happen). */
	public PriceInfo priceInfo() {
		if ("selling".equals(type)) {
			if (askingPrice != null) return new PriceInfo(askingPrice, askingCurrency, "asking");
			if (startingBid != null) return new PriceInfo(startingBid, startingBidCurrency, "starting bid");
			return null;
		}
		if (budget != null) return new PriceInfo(budget, budgetCurrency, "budget");
		return null;
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
