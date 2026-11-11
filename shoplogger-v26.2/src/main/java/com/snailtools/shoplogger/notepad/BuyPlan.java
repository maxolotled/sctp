package com.snailtools.shoplogger.notepad;

import com.snailtools.shoplogger.gui.data.Listing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The cheapest way to get a number of items from the shops that sell them.
 * Not simply "the lowest price per item": shops sell in fixed amounts (a bulk
 * shop's 2 stacks for 1 db), so for 1 item a 2 dia single beats that bulk shop,
 * and for 72 the best is often a mix (a stack here, 8 there). Prices are
 * compared in diamonds (Listing.pricePerItemInDiamonds).
 */
public final class BuyPlan {

	/** One shop to buy from: how many times its sign (batches), the items that gives, and the cost in diamonds. */
	public record Stop(Listing listing, int batches, int items, double diamonds) {
		/** "2× 1 db", or the sign's price for one batch. */
		public String costLabel() {
			String p = listing.priceLabel == null ? "?" : listing.priceLabel;
			return batches == 1 ? p : batches + "× " + p;
		}
	}

	public final int wanted;
	public final List<Stop> stops;
	public final int items;        // what the stops give together (can be a bit more than wanted, or less when not enough is for sale)
	public final double diamonds;  // what they cost together

	private BuyPlan(int wanted, List<Stop> stops) {
		this.wanted = wanted;
		stops.sort(Comparator.comparingInt((Stop s) -> -s.items()).thenComparingDouble(Stop::diamonds));
		this.stops = stops;
		int it = 0;
		double d = 0;
		for (Stop s : stops) { it += s.items(); d += s.diamonds(); }
		this.items = it;
		this.diamonds = d;
	}

	public boolean enough() { return items >= wanted; }
	public boolean empty() { return stops.isEmpty(); }

	/** Above this (items wanted × pieces), the exact search is swapped for a quick one that's nearly as good. */
	private static final long EXACT_LIMIT = 6_000_000L;
	/** A hair per shop, so that of two equally cheap ways the one with fewer shops wins. */
	private static final double PER_SHOP = 1e-6;

	private static final class Offer {
		final Listing l;
		final int size;       // items per batch
		final double cost;    // diamonds per batch
		int stock;            // batches you can still buy
		int take;
		Offer(Listing l, int size, double cost, int stock) { this.l = l; this.size = size; this.cost = cost; this.stock = stock; }
	}

	/**
	 * The cheapest way to get {@code wanted} items from {@code listings} (already
	 * narrowed to the item and world). {@code alreadyBought}: per seller, items
	 * you bought there since the listings were loaded, taken off their stock.
	 */
	public static BuyPlan cheapest(List<Listing> listings, int wanted, Map<String, Integer> alreadyBought) {
		List<Offer> offers = new ArrayList<>();
		for (Listing l : listings) {
			if (l.marketplace || l.isDisplay()) continue;
			double perItem = l.pricePerItemInDiamonds();
			if (!Double.isFinite(perItem) || perItem < 0) continue;
			int size = Math.max(1, l.stackSize);
			int amount = l.amount - (alreadyBought == null || l.seller == null ? 0 : alreadyBought.getOrDefault(l.seller.toLowerCase(java.util.Locale.ROOT), 0));
			int stock = Math.max(0, amount) / size;
			if (stock > 0) offers.add(new Offer(l, size, perItem * size, stock));
		}
		if (wanted <= 0 || offers.isEmpty()) return new BuyPlan(Math.max(0, wanted), new ArrayList<>());

		long forSale = 0;
		for (Offer o : offers) forSale += (long) o.stock * o.size;
		if (forSale <= wanted) {
			// not enough (or just enough) for sale: everything there is
			for (Offer o : offers) o.take = o.stock;
		} else {
			// split each shop's stock into pieces of 1, 2, 4, ... batches, so "how many batches here" is a yes/no per piece
			List<int[]> pieces = new ArrayList<>(); // {offer index, batches}
			for (int i = 0; i < offers.size(); i++) {
				// never more batches than it takes to cover everything on its own
				int left = Math.min(offers.get(i).stock, (wanted + offers.get(i).size - 1) / offers.get(i).size);
				for (int k = 1; left > 0; k <<= 1) {
					int b = Math.min(k, left);
					pieces.add(new int[] {i, b});
					left -= b;
				}
			}
			if ((long) pieces.size() * (wanted + 1) <= EXACT_LIMIT) exact(offers, pieces, wanted);
			else quick(offers, wanted);
		}

		List<Stop> stops = new ArrayList<>();
		for (Offer o : offers) if (o.take > 0) stops.add(new Stop(o.l, o.take, o.take * o.size, o.take * o.cost));
		return new BuyPlan(wanted, stops);
	}

	/** best[q] = the least it costs to get at least q items; each piece is used once or not at all. */
	private static void exact(List<Offer> offers, List<int[]> pieces, int wanted) {
		double[] best = new double[wanted + 1];
		java.util.Arrays.fill(best, Double.POSITIVE_INFINITY);
		best[0] = 0;
		BitSet[] used = new BitSet[pieces.size()];
		for (int p = 0; p < pieces.size(); p++) {
			Offer o = offers.get(pieces.get(p)[0]);
			int b = pieces.get(p)[1];
			int qty = b * o.size;
			double cost = b * o.cost + PER_SHOP;
			BitSet u = new BitSet(wanted + 1);
			for (int q = wanted; q >= 1; q--) {
				double from = best[Math.max(0, q - qty)];
				if (from + cost < best[q] - 1e-12) { best[q] = from + cost; u.set(q); }
			}
			used[p] = u;
		}
		// walk back from "all of them"
		int q = wanted;
		for (int p = pieces.size() - 1; p >= 0 && q > 0; p--) {
			if (!used[p].get(q)) continue;
			Offer o = offers.get(pieces.get(p)[0]);
			o.take += pieces.get(p)[1];
			q = Math.max(0, q - pieces.get(p)[1] * o.size);
		}
	}

	/** For very large amounts: lowest price per item first in whole batches, then the cheapest way to cover what's left. */
	private static void quick(List<Offer> offers, int wanted) {
		List<Offer> byPrice = new ArrayList<>(offers);
		byPrice.sort(Comparator.comparingDouble(o -> o.cost / o.size));
		int left = wanted;
		for (Offer o : byPrice) {
			if (left <= 0) break;
			int b = Math.min(o.stock, left / o.size);
			o.take += b;
			left -= b * o.size;
		}
		while (left > 0) {
			Offer pick = null;
			int pickB = 0;
			double pickCost = Double.POSITIVE_INFINITY;
			for (Offer o : offers) {
				int room = o.stock - o.take;
				if (room <= 0) continue;
				int b = Math.min(room, (left + o.size - 1) / o.size);
				// the cost per item it covers, so a shop that can't cover all of it still gets a fair comparison
				double perCovered = b * o.cost / Math.min(left, b * o.size);
				if (perCovered < pickCost) { pickCost = perCovered; pick = o; pickB = b; }
			}
			if (pick == null) break;
			pick.take += pickB;
			left -= pickB * pick.size;
		}
	}
}
