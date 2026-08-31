package com.snailtools.shoplogger.gui.data;

/** Mirrors one row from GET /listings — see worker.js's `listings` table. */
public class Listing {
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
		double mult = "diamondblock".equalsIgnoreCase(currency) ? 9.0 : 1.0;
		int size = stackSize <= 0 ? 1 : stackSize;
		return (price * mult) / size;
	}
}
