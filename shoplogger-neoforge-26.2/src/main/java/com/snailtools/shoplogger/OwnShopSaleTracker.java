package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For the player's OWN shops only: watches whether the sign's currency item
 * is sitting in the chest — i.e. a buyer paid and it hasn't been collected
 * yet. Highlights the chest green (see WorldHighlights) on the transition to
 * "has payment" (with a one-time chat heads-up), and back to normal once the
 * seller collects it.
 *
 * Reads the container's raw slots itself rather than reusing ShopEntryFactory's
 * tallied entries — ShopEntryFactory deliberately drops the sign's own currency
 * item from its output (a shop should never list the item it's paid in as
 * something for sale), which would make a sitting payment invisible to this
 * check entirely if it relied on that list instead.
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

	/** Own shops with a payment waiting — WorldHighlights draws a green highlight around each. */
	public static List<BlockPos> pendingPaymentPositions() {
		List<BlockPos> out = new ArrayList<>();
		HAS_PENDING_PAYMENT.forEach((pos, pending) -> { if (pending) out.add(pos); });
		return out;
	}

	public static boolean isMessagesEnabled() {
		return Config.getOrCreate(CONFIG_MESSAGES_ENABLED, Boolean.class, true);
	}

	public static void setMessagesEnabled(boolean value) {
		Config.update(CONFIG_MESSAGES_ENABLED, value);
	}

	/** Call after every real (sign-having) scan of a container, manual or silent. worldLabel may be null (e.g. world not yet detected) — the alert still fires, just without a beam button. */
	public static void check(Minecraft client, ShopSign sign, BlockPos containerPos, AbstractContainerMenu handler, String worldLabel) {
		String self = client.getUser().getName();
		if (self == null || !self.equalsIgnoreCase(sign.seller())) {
			HAS_PENDING_PAYMENT.remove(containerPos); // not our shop — don't track it
			return;
		}

		String baseItemId = CURRENCY_BASE_ITEMS.get(sign.currency());
		if (baseItemId == null) return;

		boolean nowHasPayment = containerHasItem(handler, baseItemId);
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
			MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
					.append(Component.literal("Something sold from your shop at " + containerPos.toShortString() + "!").withStyle(ChatFormat.SUCCESS));
			if (worldLabel != null) {
				msg.append(Component.literal("  ")).append(WatchlistAlert.buildBeamButton(worldLabel, containerPos));
			}
			client.player.sendSystemMessage(msg);
		}
	}

	/**
	 * How many of the sign's currency items sit in the shop right now (paid but
	 * not collected), or null when the currency isn't one we can recognise.
	 * Uploaded with every scan of ANY shop: the Worker counts a sale only when
	 * stock went down AND this went up between two scans — see
	 * detectConfirmedSales in worker.js.
	 */
	public static Integer paymentCount(ShopSign sign, AbstractContainerMenu handler) {
		if (sign == null || sign.display()) return null;
		String baseItemId = CURRENCY_BASE_ITEMS.get(sign.currency());
		if (baseItemId == null || !(handler instanceof ChestMenu containerHandler)) return null;
		int invSize = containerHandler.getContainer().getContainerSize();
		int count = 0;
		for (int i = 0; i < invSize && i < handler.slots.size(); i++) {
			ItemStack stack = handler.getSlot(i).getItem();
			if (stack == null || stack.isEmpty()) continue;
			if (baseItemId.equalsIgnoreCase(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) count += stack.getCount();
		}
		return count;
	}

	/** Only the container's own slots — same bound ShopEntryFactory uses to exclude the player's own inventory slots later in the same handler. */
	private static boolean containerHasItem(AbstractContainerMenu handler, String baseItemId) {
		if (!(handler instanceof ChestMenu containerHandler)) return false;
		int invSize = containerHandler.getContainer().getContainerSize();
		for (int i = 0; i < invSize && i < handler.slots.size(); i++) {
			ItemStack stack = handler.getSlot(i).getItem();
			if (stack == null || stack.isEmpty()) continue;
			if (baseItemId.equalsIgnoreCase(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) return true;
		}
		return false;
	}
}
