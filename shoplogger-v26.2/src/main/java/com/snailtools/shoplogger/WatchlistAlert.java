package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;

/**
 * Alerts in chat the moment any scan (manual or silent) finds a watchlist
 * item in stock at a shop, with clickable [TP] and [Stop watching] actions —
 * see WatchlistStore (the list itself) and WatchlistScreen (add/remove UI).
 * Cooldown-gated per (world, seller, item), same 1-hour idea as
 * ShopVisitAlert, so re-scanning the same shop repeatedly doesn't spam.
 */
public final class WatchlistAlert {

	private static final long COOLDOWN_MS = 60 * 60 * 1000L;

	private WatchlistAlert() {}

	/** Call after any scan (manual or silent) that read a valid sign, with whatever entries it built. */
	public static void maybeAlert(Minecraft client, List<ShopEntry> entries) {
		if (client.player == null || entries.isEmpty()) return;
		List<WatchedItem> watched = WatchlistStore.getAll();
		if (watched.isEmpty()) return;

		for (ShopEntry entry : entries) {
			WatchedItem match = findWatched(entry.itemName(), watched);
			if (match == null) continue;
			if (WatchlistIgnore.isIgnored(WatchlistIgnore.shopKey(ShopReporter.fromEntry(entry)))) continue; // the player chose to ignore this exact listing
			// A display sign has no real price, so a max-price cap can't apply to
			// it either way — it only ever gets filtered by excludeNoPriceOrDisplay.
			boolean isDisplay = ShopSign.DISPLAY_CURRENCY.equalsIgnoreCase(entry.currency());
			if (match.excludeNoPriceOrDisplay && isDisplay) continue;
			if (!isDisplay && match.maxPrice != null && entry.pricePerItemInDiamonds() > match.maxPrice) continue;

			String cooldownKey = "watchlist/lastAlert/" + entry.world().toLowerCase(Locale.ROOT)
					+ "|" + entry.seller().toLowerCase(Locale.ROOT)
					+ "|" + MatchUtil.watchKey(entry.itemName());
			long now = System.currentTimeMillis();
			Long last = Config.get(cooldownKey, Long.class);
			if (last != null && now - last < COOLDOWN_MS) continue;
			Config.update(cooldownKey, now);

			report(client, entry);
		}
	}

	private static WatchedItem findWatched(String itemName, List<WatchedItem> watched) {
		String normalized = MatchUtil.watchKey(itemName);
		for (WatchedItem w : watched) {
			if (MatchUtil.watchKey(w.itemName).equals(normalized)) return w;
		}
		return null;
	}

	private static void report(Minecraft client, ShopEntry entry) {
		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal("Watching: ").withStyle(ChatFormat.SUCCESS))
				.append(Component.literal(entry.itemName() + " (" + entry.priceLabel() + ") at " + entry.seller() + "'s shop  ").withStyle(ChatFormat.RESULT))
				.append(buildTpButton(entry.world(), entry.containerPos(), entry.seller()))
				.append(Component.literal("  "))
				.append(ShopReporter.buildChatButton(ShopReporter.fromEntry(entry)))
				.append(Component.literal("  "))
				.append(WatchlistIgnore.buildChatButton(WatchlistIgnore.shopKey(ShopReporter.fromEntry(entry))))
				.append(Component.literal("  "))
				.append(buildOptionsButton(entry.itemName()))
				.append(Component.literal("  "))
				.append(buildRemoveButton(entry.itemName()));

		client.player.sendSystemMessage(msg);
	}

	/** Shared with WatchlistJoinCheck, which sources the same [TP] action from a text-parsed position instead of a live scan's real BlockPos. */
	static Component buildTpButton(String world, BlockPos pos, String seller) {
		String tpCommand = "/watchtp " + world + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + seller;
		return Component.literal("[TP]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(tpCommand))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Teleport to this shop"))));
	}

	/**
	 * For a watchlist match that turned out to be marketplace-sourced (see
	 * Listing.marketplace) — replaces the usual "at Seller's shop" text with a
	 * clickable "Seller's marketplace" link straight to that listing, since
	 * there's no real shop/position to name or teleport to.
	 */
	static Component buildMarketplaceLink(String seller, String listingId) {
		String url = "https://sctp.nl/marketplace/#listing=" + listingId;
		return Component.literal(seller + "'s marketplace").setStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open this listing on the website"))));
	}

	/** Same beam as buildTpButton, but doesn't run "/shop <seller>" — for OwnShopSaleTracker's sold-item alert, where the player is presumably already at (or near) their own shop and doesn't need re-teleporting there. */
	static Component buildBeamButton(String world, BlockPos pos) {
		String beamCommand = "/watchbeam " + world + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
		return Component.literal("[Beam]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(beamCommand))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show a beam to this shop"))));
	}

	/** Shared with WatchlistJoinCheck, which sources the same [Options] action for its marketplace matches too. */
	static Component buildOptionsButton(String itemName) {
		String optionsCommand = "/watchoptions " + itemName;
		return Component.literal("[Options]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand(optionsCommand))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Edit max price / settings for this item"))));
	}

	/** Shared with WatchlistJoinCheck — see buildTpButton. */
	static Component buildRemoveButton(String itemName) {
		String removeCommand = "/watchremove " + itemName;
		return Component.literal("[Stop watching]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand(removeCommand))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Remove from your watchlist"))));
	}
}
