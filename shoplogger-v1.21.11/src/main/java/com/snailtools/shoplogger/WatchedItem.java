package com.snailtools.shoplogger;

/** One entry in the local watchlist — see WatchlistStore. */
public class WatchedItem {
	public String itemName;
	/** In diamonds-equivalent (see the various pricePerItemInDiamonds()/diamondValue() helpers) — null means no cap. */
	public Double maxPrice;
	/** Skip [DISPLAY] shop signs and marketplace posts with no price/budget set. */
	public boolean excludeNoPriceOrDisplay;

	public WatchedItem() {}

	public WatchedItem(String itemName) {
		this.itemName = itemName;
	}
}
