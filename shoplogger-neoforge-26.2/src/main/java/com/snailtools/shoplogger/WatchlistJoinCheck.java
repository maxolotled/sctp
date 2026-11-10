package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MarketplaceListing;
import com.snailtools.shoplogger.gui.data.MarketplaceNotification;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
	public static void tick(Minecraft client) {
		if (!pending || client.player == null) return;
		ShopWorld world = WorldSelection.get();
		if (world == null) return; // wait for WorldDetector to finish — no point nagging, see WorldSelection.ensureSet()
		// A world value here could just be left over from an earlier session on
		// a different server (WorldSelection persists to disk) — only a real
		// "Current World:" read on THIS connection means we're actually on
		// Snailcraft right now. Otherwise stay completely silent (no chat
		// alerts, no marketplace checks) on singleplayer/unrelated servers.
		if (!WorldDetector.getInstance().isConfirmedThisSession()) return;
		pending = false;

		List<WatchedItem> watched = WatchlistStore.getAll();
		if (!watched.isEmpty()) {
			CompletableFuture<List<Component>> marketplaceFuture = WatchlistStore.isMarketplaceAlertsEnabled()
					? WebDataClient.fetchMarketplaceListings()
							.thenApply(listings -> buildMarketplaceMatches(world.label(), watched, listings))
							.exceptionally(ex -> List.of())
					: CompletableFuture.completedFuture(List.<Component>of());
			CompletableFuture<List<Component>> listingsFuture = WebDataClient.fetchListings()
					.thenApply(listings -> buildListingMatches(world.label(), watched, listings))
					.exceptionally(ex -> List.of());

			// Marketplace matches first — they're the ones a player can act on
			// immediately (bid/message), so they're worth surfacing above the
			// "someone's shop has it in stock" results.
			marketplaceFuture.thenCombine(listingsFuture, (marketplaceMsgs, listingMsgs) -> {
				List<Component> combined = new ArrayList<>(marketplaceMsgs);
				combined.addAll(listingMsgs);
				return combined;
			}).thenAccept(combined -> client.execute(() -> {
				for (Component msg : combined) client.player.sendSystemMessage(msg);
			})).exceptionally(ex -> null);
		}

		String self = client.getUser().getName();
		if (self != null && !self.isEmpty()) {
			WebDataClient.fetchMarketplaceNotifications(self)
					.thenAccept(notifications -> client.execute(() -> deliverNotifications(client, notifications)))
					.exceptionally(ex -> null);
		}
	}

	private static List<Component> buildMarketplaceMatches(String world, List<WatchedItem> watched, List<MarketplaceListing> listings) {
		List<Component> out = new ArrayList<>();
		for (MarketplaceListing m : listings) {
			if (!world.equalsIgnoreCase(m.world)) continue;
			if ("lookingFor".equals(m.type)) continue; // only alert on stuff you could actually buy
			WatchedItem match = findWatched(m.itemName, watched);
			if (match == null) continue;
			if (WatchlistIgnore.isIgnored(WatchlistIgnore.marketKey(m.id))) continue;
			boolean noPrice = m.priceInfo() == null;
			if (match.excludeNoPriceOrDisplay && noPrice) continue;
			if (!noPrice && match.maxPrice != null && m.diamondValue() > match.maxPrice) continue;
			out.add(buildMarketplaceMatch(m));
		}
		return out;
	}

	private static WatchedItem findWatched(String itemName, List<WatchedItem> watched) {
		String normalized = MatchUtil.watchKey(itemName);
		for (WatchedItem w : watched) {
			if (MatchUtil.watchKey(w.itemName).equals(normalized)) return w;
		}
		return null;
	}

	/** Distinct "Marketplace:" color (see ChatFormat.MARKETPLACE) and always sorted ahead of shop-listing matches — see tick(). */
	private static Component buildMarketplaceMatch(MarketplaceListing m) {
		MarketplaceListing.PriceInfo price = m.priceInfo();
		// "diamondstack" is a marketplace-only currency (real shop signs never
		// use it) — shown as "STX" everywhere else on the site, not the raw
		// currency string.
		String currencyText = price == null ? "?" : "diamondstack".equals(price.currency) ? "STX" : price.currency == null ? "?" : price.currency;
		String priceText = price != null ? (price.amount + " " + currencyText + " (" + price.label + ")") : "no price set";
		String bidText = m.bidCount > 0 ? ", " + m.bidCount + " bid" + (m.bidCount > 1 ? "s" : "") : "";

		// "lookingFor" posts are filtered out before this is ever called (see
		// buildMarketplaceMatches) — a watchlist alert should only ever point at
		// something the player could actually go buy.
		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal("Marketplace: ").withStyle(ChatFormat.MARKETPLACE))
				.append(Component.literal("Selling " + m.quantity + "x " + m.itemName + " at ").withStyle(ChatFormat.RESULT))
				.append(WatchlistAlert.buildMarketplaceLink(m.seller, m.id));
		msg.append(Component.literal((m.sellerVerified ? " ✓" : "") + " — " + priceText + bidText + "  ").withStyle(ChatFormat.RESULT));
		msg.append(WatchlistAlert.buildOptionsButton(m.itemName)).append(Component.literal("  "));
		msg.append(WatchlistIgnore.buildChatButton(WatchlistIgnore.marketKey(m.id)));
		return msg;
	}

	private static void deliverNotifications(Minecraft client, List<MarketplaceNotification> notifications) {
		for (MarketplaceNotification n : notifications) {
			MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
					.append(Component.literal("Marketplace: ").withStyle(ChatFormat.SUCCESS))
					.append(Component.literal(n.message == null ? "" : n.message).withStyle(ChatFormat.RESULT));
			client.player.sendSystemMessage(msg);
		}
	}

	private static List<Component> buildListingMatches(String world, List<WatchedItem> watched, List<Listing> listings) {
		List<Component> out = new ArrayList<>();
		for (WatchedItem watchedItem : watched) {
			// each seller's cheapest matching listing
			java.util.Map<String, Listing> bySeller = new java.util.LinkedHashMap<>();

			for (Listing l : listings) {
				if (!world.equalsIgnoreCase(l.world)) continue;
				// GET /listings merges in active marketplace posts alongside real
				// shop listings (see worker.js's marketplaceRowAsListing) — but
				// those are always reported separately by buildMarketplaceMatches
				// above, so skip them here entirely rather than risk the same
				// listing getting announced twice (once as "Marketplace: ...", once
				// as "Watching: ... at Seller's marketplace").
				if (l.marketplace) continue;
				boolean isDisplay = "display".equalsIgnoreCase(l.currency);
				if (watchedItem.excludeNoPriceOrDisplay && isDisplay) continue;
				if (!MatchUtil.watchKey(l.itemName).equals(MatchUtil.watchKey(watchedItem.itemName))) continue;
				if (WatchlistIgnore.isIgnored(WatchlistIgnore.shopKey(ShopReporter.fromListing(l)))) continue;
				if (!isDisplay && watchedItem.maxPrice != null && l.pricePerItemInDiamonds() > watchedItem.maxPrice) continue;

				String key = l.seller == null ? "" : l.seller.toLowerCase(java.util.Locale.ROOT);
				Listing had = bySeller.get(key);
				if (had == null || l.pricePerItemInDiamonds() < had.pricePerItemInDiamonds()) bySeller.put(key, l);
			}

			if (bySeller.isEmpty()) continue;
			// cheapest first: the alert is about the cheapest, the rest are listed on hover in that order
			List<Listing> sellers = new ArrayList<>(bySeller.values());
			sellers.sort(java.util.Comparator.comparingDouble(Listing::pricePerItemInDiamonds));
			out.add(buildListingMatch(watchedItem.itemName, sellers));
		}
		return out;
	}

	/** At most this many other sellers are named in the "+N more sellers" hover. */
	private static final int MORE_SELLERS_SHOWN = 15;

	private static Component buildListingMatch(String watchedName, List<Listing> sellers) {
		Listing best = sellers.get(0);
		int more = sellers.size() - 1;

		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal("Watching: ").withStyle(ChatFormat.SUCCESS))
				.append(Component.literal(best.itemName + " (" + best.priceLabel + ") at " + best.seller + "'s shop").withStyle(ChatFormat.RESULT));
		if (more > 0) {
			// hover: the other sellers, cheapest first
			MutableComponent list = Component.literal("Also selling it, cheapest first:").withStyle(ChatFormat.NEUTRAL);
			for (int i = 1; i < sellers.size() && i <= MORE_SELLERS_SHOWN; i++) {
				Listing l = sellers.get(i);
				list.append(Component.literal("\n" + l.seller + " — " + l.priceLabel).withStyle(ChatFormat.RESULT));
			}
			if (more > MORE_SELLERS_SHOWN) list.append(Component.literal("\n…and " + (more - MORE_SELLERS_SHOWN) + " more").withStyle(ChatFormat.NEUTRAL));
			msg.append(Component.literal(" (+" + more + " more seller" + (more > 1 ? "s" : "") + ")").setStyle(net.minecraft.network.chat.Style.EMPTY
					.withColor(net.minecraft.ChatFormatting.WHITE).withUnderlined(true)
					.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(list))));
		}
		msg.append(Component.literal("  "));

		BlockPos pos = parsePosition(best.position);
		if (pos != null) {
			msg.append(WatchlistAlert.buildTpButton(best.world, pos, best.seller)).append(Component.literal("  "));
		}
		msg.append(ShopReporter.buildChatButton(ShopReporter.fromListing(best))).append(Component.literal("  "));
		msg.append(WatchlistIgnore.buildChatButton(WatchlistIgnore.shopKey(ShopReporter.fromListing(best)))).append(Component.literal("  "));
		msg.append(WatchlistAlert.buildOptionsButton(watchedName)).append(Component.literal("  "));
		msg.append(WatchlistAlert.buildRemoveButton(watchedName));

		return msg;
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
