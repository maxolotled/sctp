package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For the player's OWN shops only: watches whether the sign's currency item
 * is sitting in the chest — i.e. a buyer paid and it hasn't been collected
 * yet. Turns the recently-scanned particle green on the transition to
 * "has payment" (with a one-time chat heads-up), and back to normal once the
 * seller collects it.
 *
 * Reuses whatever ShopEntryFactory already tallied for this scan rather than
 * re-reading container slots itself — every item in the container gets
 * tallied into an entry, currency included, so a sitting payment is already
 * right there as its own ShopEntry.
 */
public final class OwnShopSaleTracker {

	// Only the two currencies shop signs actually use — see ShopSign's currency
	// line. An unrecognized currency string (a malformed/non-standard sign)
	// just means we can't identify the payment item, so it's skipped.
	private static final Map<String, String> CURRENCY_BASE_ITEMS = Map.of(
			"diamond", "minecraft:diamond",
			"diamondblock", "minecraft:diamond_block"
	);

	private static final Map<BlockPos, Boolean> HAS_PENDING_PAYMENT = new HashMap<>();
	private static final String CONFIG_MESSAGES_ENABLED = "ownShopSale/messagesEnabled";

	private OwnShopSaleTracker() {}

	public static boolean hasPendingPayment(BlockPos containerPos) {
		return HAS_PENDING_PAYMENT.getOrDefault(containerPos, false);
	}

	public static boolean isMessagesEnabled() {
		return Config.getOrCreate(CONFIG_MESSAGES_ENABLED, Boolean.class, true);
	}

	public static void setMessagesEnabled(boolean value) {
		Config.update(CONFIG_MESSAGES_ENABLED, value);
	}

	/** Call after every real (sign-having) scan of a container, manual or silent. */
	public static void check(Minecraft client, ShopSign sign, BlockPos containerPos, List<ShopEntry> entries) {
		String self = client.getUser().getName();
		if (self == null || !self.equalsIgnoreCase(sign.seller())) {
			HAS_PENDING_PAYMENT.remove(containerPos); // not our shop — don't track it
			return;
		}

		String baseItemId = CURRENCY_BASE_ITEMS.get(sign.currency());
		if (baseItemId == null) return;

		boolean nowHasPayment = entries.stream()
				.anyMatch(e -> baseItemId.equalsIgnoreCase(e.baseItem()) && e.amountAvailable() > 0);
		Boolean before = HAS_PENDING_PAYMENT.put(containerPos, nowHasPayment);

		// Only notify on a genuine LIVE transition (confirmed empty -> now has
		// payment) observed within this session. If `before` is null, this is
		// the first time we've seen this container since the game/mod started
		// — we don't actually know whether the payment sitting there is a
		// brand-new sale or one that's been waiting uncollected for a while,
		// so just record the baseline silently instead of assuming it's new.
		// Without this, a shop wall with several already-pending payments
		// floods "Something sold!" for every chest the moment the auto-scanner
		// discovers them each session, even though nothing new just happened.
		if (nowHasPayment && Boolean.FALSE.equals(before) && isMessagesEnabled()) {
			ChatFormat.send(client, ChatFormat.SUCCESS, "Something sold from your shop at " + containerPos.toShortString() + "!");
		}
	}
}
