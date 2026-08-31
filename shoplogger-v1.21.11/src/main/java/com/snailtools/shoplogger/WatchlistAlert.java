package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

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
	public static void maybeAlert(MinecraftClient client, List<ShopEntry> entries) {
		if (client.player == null || entries.isEmpty()) return;
		List<String> watched = WatchlistStore.getAll();
		if (watched.isEmpty()) return;

		for (ShopEntry entry : entries) {
			if (ShopSign.DISPLAY_CURRENCY.equalsIgnoreCase(entry.currency())) continue; // never alert on display/free listings
			if (!isWatched(entry.itemName(), watched)) continue;

			String cooldownKey = "watchlist/lastAlert/" + entry.world().toLowerCase(Locale.ROOT)
					+ "|" + entry.seller().toLowerCase(Locale.ROOT)
					+ "|" + MatchUtil.alphaOnly(entry.itemName());
			long now = System.currentTimeMillis();
			Long last = Config.get(cooldownKey, Long.class);
			if (last != null && now - last < COOLDOWN_MS) continue;
			Config.update(cooldownKey, now);

			report(client, entry);
		}
	}

	private static boolean isWatched(String itemName, List<String> watched) {
		String normalized = MatchUtil.alphaOnly(itemName);
		for (String w : watched) {
			if (MatchUtil.alphaOnly(w).equals(normalized)) return true;
		}
		return false;
	}

	private static void report(MinecraftClient client, ShopEntry entry) {
		MutableText msg = Text.literal("[ShopLogger] ").formatted(ChatFormat.PREFIX)
				.append(Text.literal("Watching: ").formatted(ChatFormat.SUCCESS))
				.append(Text.literal(entry.itemName() + " (" + entry.priceLabel() + ") at " + entry.seller() + "'s shop  ").formatted(ChatFormat.RESULT))
				.append(buildTpButton(entry.world(), entry.containerPos(), entry.seller()))
				.append(Text.literal("  "))
				.append(buildRemoveButton(entry.itemName()));

		client.player.sendMessage(msg, false);
	}

	/** Shared with WatchlistJoinCheck, which sources the same [TP] action from a text-parsed position instead of a live scan's real BlockPos. */
	static Text buildTpButton(String world, BlockPos pos, String seller) {
		String tpCommand = "/watchtp " + world + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + seller;
		return Text.literal("[TP]").setStyle(Style.EMPTY
				.withColor(Formatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(tpCommand))
				.withHoverEvent(new HoverEvent.ShowText(Text.literal("Teleport to this shop"))));
	}

	/** Shared with WatchlistJoinCheck — see buildTpButton. */
	static Text buildRemoveButton(String itemName) {
		String removeCommand = "/watchremove " + itemName;
		return Text.literal("[Stop watching]").setStyle(Style.EMPTY
				.withColor(Formatting.RED)
				.withClickEvent(new ClickEvent.RunCommand(removeCommand))
				.withHoverEvent(new HoverEvent.ShowText(Text.literal("Remove from your watchlist"))));
	}
}
