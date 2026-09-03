package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MarketplaceListing;
import com.snailtools.shoplogger.gui.data.MarketplaceNotification;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time check, run right after the world becomes known following a join
 * or world switch: is anyone currently listing a watched item on the live
 * website data — regardless of whether this player has ever scanned that
 * shop themselves? Complements WatchlistAlert, which only reacts to fresh
 * local scans; this instead reacts to whatever's already on the server.
 *
 * Also doubles as the join-time delivery point for two marketplace checks:
 * matching active marketplace posts against the local watchlist (entirely
 * client-side, same as the shop-listings check — no server-side "what is
 * this player watching" ever exists), and delivering any pending marketplace
 * notifications (bids, accepts, etc.) for this player's own MC-verified
 * account, if any.
 */
public final class WatchlistJoinCheck {

	private static final Pattern POSITION_PATTERN = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");

	private static boolean pending = false;

	private WatchlistJoinCheck() {}

	/** Call from ClientPlayConnectionEvents.JOIN / ClientConfigurationConnectionEvents.COMPLETE. */
	public static void requestCheck() {
		pending = true;
	}

	/** Call every tick — runs the check exactly once, as soon as the world is actually known. */
	public static void tick(MinecraftClient client) {
		if (!pending || client.player == null) return;
		ShopWorld world = WorldSelection.get();
		if (world == null) return; // wait for WorldDetector to finish — no point nagging, see WorldSelection.ensureSet()
		pending = false;

		List<String> watched = WatchlistStore.getAll();
		if (!watched.isEmpty()) {
			WebDataClient.fetchListings()
					.thenAccept(listings -> client.execute(() -> report(client, world.label(), watched, listings)))
					.exceptionally(ex -> null);
			WebDataClient.fetchMarketplaceListings()
					.thenAccept(listings -> client.execute(() -> reportMarketplace(client, world.label(), watched, listings)))
					.exceptionally(ex -> null);
		}

		String self = client.getSession().getUsername();
		if (self != null && !self.isEmpty()) {
			WebDataClient.fetchMarketplaceNotifications(self)
					.thenAccept(notifications -> client.execute(() -> deliverNotifications(client, notifications)))
					.exceptionally(ex -> null);
		}
	}

	private static void reportMarketplace(MinecraftClient client, String world, List<String> watched, List<MarketplaceListing> listings) {
		for (MarketplaceListing m : listings) {
			if (!world.equalsIgnoreCase(m.world)) continue;
			boolean matches = false;
			for (String watchedName : watched) {
				if (MatchUtil.alphaOnly(m.itemName).equals(MatchUtil.alphaOnly(watchedName))) { matches = true; break; }
			}
			if (matches) reportMarketplaceMatch(client, m);
		}
	}

	private static void reportMarketplaceMatch(MinecraftClient client, MarketplaceListing m) {
		boolean selling = "selling".equals(m.type);
		MarketplaceListing.PriceInfo price = m.priceInfo();
		String priceText = price != null ? (price.amount + " " + (price.currency == null ? "?" : price.currency) + " (" + price.label + ")") : "no price set";
		String bidText = selling && m.bidCount > 0 ? ", " + m.bidCount + " bid" + (m.bidCount > 1 ? "s" : "") : "";

		MutableText msg = Text.literal("[ShopLogger] ").formatted(ChatFormat.PREFIX)
				.append(Text.literal("Marketplace: ").formatted(ChatFormat.SUCCESS))
				.append(Text.literal((selling ? "Selling " : "Looking for ") + m.quantity + "x " + m.itemName
						+ " by " + m.seller + (m.sellerVerified ? " ✓" : "") + " — " + priceText + bidText).formatted(ChatFormat.RESULT));

		client.player.sendMessage(msg, false);
	}

	private static void deliverNotifications(MinecraftClient client, List<MarketplaceNotification> notifications) {
		for (MarketplaceNotification n : notifications) {
			MutableText msg = Text.literal("[ShopLogger] ").formatted(ChatFormat.PREFIX)
					.append(Text.literal("Marketplace: ").formatted(ChatFormat.SUCCESS))
					.append(Text.literal(n.message == null ? "" : n.message).formatted(ChatFormat.RESULT));
			client.player.sendMessage(msg, false);
		}
	}

	private static void report(MinecraftClient client, String world, List<String> watched, List<Listing> listings) {
		for (String watchedName : watched) {
			Listing best = null;
			int sellerCount = 0;

			for (Listing l : listings) {
				if (!world.equalsIgnoreCase(l.world)) continue;
				if ("display".equalsIgnoreCase(l.currency)) continue;
				if (!MatchUtil.alphaOnly(l.itemName).equals(MatchUtil.alphaOnly(watchedName))) continue;

				sellerCount++;
				if (best == null || l.pricePerItemInDiamonds() < best.pricePerItemInDiamonds()) best = l;
			}

			if (best != null) reportMatch(client, watchedName, best, sellerCount);
		}
	}

	private static void reportMatch(MinecraftClient client, String watchedName, Listing best, int sellerCount) {
		String extra = sellerCount > 1 ? " (+" + (sellerCount - 1) + " more seller" + (sellerCount > 2 ? "s" : "") + ")" : "";

		MutableText msg = Text.literal("[ShopLogger] ").formatted(ChatFormat.PREFIX)
				.append(Text.literal("Watching: ").formatted(ChatFormat.SUCCESS))
				.append(Text.literal(best.itemName + " (" + best.priceLabel + ") at " + best.seller + "'s shop" + extra + "  ").formatted(ChatFormat.RESULT));

		BlockPos pos = parsePosition(best.position);
		if (pos != null) {
			msg.append(WatchlistAlert.buildTpButton(best.world, pos, best.seller)).append(Text.literal("  "));
		}
		msg.append(WatchlistAlert.buildRemoveButton(watchedName));

		client.player.sendMessage(msg, false);
	}

	/** listing.position is BlockPos#toShortString() ("x, y, z") for scanned listings, but manual admin entries can be arbitrary text. */
	private static BlockPos parsePosition(String position) {
		if (position == null) return null;
		Matcher m = POSITION_PATTERN.matcher(position.trim());
		if (!m.matches()) return null;
		try {
			return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
