package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time check, run right after the world becomes known following a join
 * or world switch: is anyone currently listing a watched item on the live
 * website data — regardless of whether this player has ever scanned that
 * shop themselves? Complements WatchlistAlert, which only reacts to fresh
 * local scans; this instead reacts to whatever's already on the server.
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
		pending = false;

		List<String> watched = WatchlistStore.getAll();
		if (watched.isEmpty()) return;

		WebDataClient.fetchListings()
				.thenAccept(listings -> client.execute(() -> report(client, world.label(), watched, listings)))
				.exceptionally(ex -> null);
	}

	private static void report(Minecraft client, String world, List<String> watched, List<Listing> listings) {
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

	private static void reportMatch(Minecraft client, String watchedName, Listing best, int sellerCount) {
		String extra = sellerCount > 1 ? " (+" + (sellerCount - 1) + " more seller" + (sellerCount > 2 ? "s" : "") + ")" : "";

		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal("Watching: ").withStyle(ChatFormat.SUCCESS))
				.append(Component.literal(best.itemName + " (" + best.priceLabel + ") at " + best.seller + "'s shop" + extra + "  ").withStyle(ChatFormat.RESULT));

		BlockPos pos = parsePosition(best.position);
		if (pos != null) {
			msg.append(WatchlistAlert.buildTpButton(best.world, pos, best.seller)).append(Component.literal("  "));
		}
		msg.append(WatchlistAlert.buildRemoveButton(watchedName));

		client.player.sendSystemMessage(msg);
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
