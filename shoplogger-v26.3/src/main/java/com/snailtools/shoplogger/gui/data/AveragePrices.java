package com.snailtools.shoplogger.gui.data;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A shop listing's own price is no longer shown (the Snailcraft admins asked
 * for live prices to be removed — see sctp.nl/statement). In its place: the
 * average price per single item, in diamonds, across every current listing of
 * that same item in that same world. Worked out from the full GET /listings
 * list each time it's fetched (WebDataClient.fetchListings), so pages that
 * only show part of it (one item, one seller) still get the whole average.
 * Same grouping as the website: [DISPLAY] signs, marketplace posts and the
 * hunt's TheShellkeeper never count.
 */
public final class AveragePrices {

	public static final String STATEMENT_URL = "https://sctp.nl/statement";
	public static final String EXPLANATION = "Live prices were removed at the request of the Snailcraft admins. This is the average price of one of these, across every shop listing it in this world.";

	private static final Set<String> EXCLUDED_SELLERS = Set.of("theshellkeeper");

	public record Avg(double perItem, int listings) {}

	private static volatile Map<String, Avg> averages = Map.of();

	private AveragePrices() {}

	private static String key(String world, String baseItem, String itemName) {
		return (String.valueOf(world) + "|" + baseItem + "|" + itemName).toLowerCase(Locale.ROOT);
	}

	/** Whether a listing takes part in (and gets) an average at all. */
	private static boolean counts(Listing l) {
		if (l.marketplace || l.isDisplay()) return false;
		return l.seller == null || !EXCLUDED_SELLERS.contains(l.seller.toLowerCase(Locale.ROOT));
	}

	/** Recompute from the full listings list. */
	public static void update(List<Listing> all) {
		Map<String, double[]> sums = new HashMap<>();
		for (Listing l : all) {
			if (!counts(l)) continue;
			double each = l.pricePerItemInDiamonds();
			if (!Double.isFinite(each) || each < 0) continue;
			double[] s = sums.computeIfAbsent(key(l.world, l.baseItem, l.itemName), k -> new double[2]);
			s[0] += each;
			s[1]++;
		}
		Map<String, Avg> out = new HashMap<>();
		for (var e : sums.entrySet()) out.put(e.getKey(), new Avg(e.getValue()[0] / e.getValue()[1], (int) e.getValue()[1]));
		averages = out;
	}

	/** The average for this listing's item and world, or null (display signs, auction lots, not loaded yet). */
	public static Avg of(Listing l) {
		if (l.marketplace || l.isDisplay()) return null;
		return averages.get(key(l.world, l.baseItem, l.itemName));
	}

	/** Same as the website: "0.52 dia", or in diamond blocks above 9 diamonds. */
	public static String format(double diamonds) {
		if (diamonds > 9) return trim(diamonds / 9) + " db";
		return trim(diamonds) + " dia";
	}

	private static String trim(double v) {
		double r = Math.round(v * 100) / 100.0;
		if (r == Math.floor(r)) return String.valueOf((long) r);
		return String.valueOf(r);
	}

	/** The info icon's tooltip next to an average: why there's no real price, and where to read more. */
	public static java.util.List<net.minecraft.util.FormattedCharSequence> tooltip(net.minecraft.client.gui.Font font, Avg a) {
		java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
		out.addAll(font.split(net.minecraft.network.chat.Component.literal("Average price (live prices removed)")
				.withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD), 220));
		out.addAll(font.split(net.minecraft.network.chat.Component.literal(EXPLANATION).withStyle(net.minecraft.ChatFormatting.GRAY), 220));
		if (a != null) out.addAll(font.split(net.minecraft.network.chat.Component.literal("Based on " + a.listings() + " listing" + (a.listings() == 1 ? "" : "s") + ".")
				.withStyle(net.minecraft.ChatFormatting.GRAY), 220));
		out.addAll(font.split(net.minecraft.network.chat.Component.literal("Click to read our statement ↗").withStyle(net.minecraft.ChatFormatting.AQUA), 220));
		return out;
	}

	/** "Avg 0.52 dia each" */
	public static String label(Avg a) {
		return "Avg " + format(a.perItem()) + " each";
	}
}
