package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Handles containers the player opens themselves (right-click, real GUI). */
public class ShopScanner {

	/**
	 * If a container screen doesn't open within this long after the matching
	 * right-click, we no longer trust that the next screen that DOES open is
	 * actually the one we clicked — e.g. the click was denied/delayed and a
	 * completely different container opened shortly after instead. Without
	 * this, that later screen's contents would get attributed to the
	 * originally-clicked position, creating a "ghost" listing for items that
	 * were never actually in that chest.
	 */
	private static final long PENDING_TIMEOUT_MS = 3000L;

	private BlockPos pendingContainerPos = null;
	private ShopSign pendingSign = null;
	private long pendingSetAtMillis = 0L;

	/**
	 * The handler of the currently-open screen, IF ShopEntryFactory just built
	 * real shop entries for it (a sign was found nearby, not stale) — used by
	 * RareRentalHighlighter to tell a real shop chest apart from any other
	 * container the player might open (their own storage, an ender chest).
	 */
	private static AbstractContainerMenu currentShopHandler = null;
	private static ShopSign currentShopSign = null;
	// The open shop still waiting for its contents (see onContainerContent).
	private static BlockPos awaitingContentsPos = null;

	// The open shop as last scanned (its listings by key, and the payment in it), so a
	// purchase shows up as stock going down while the payment goes up. See rescanForBuys.
	private static java.util.Map<String, ShopEntry> openStock = null;
	private static Integer openPayment = null;
	private static BlockPos openPos = null;
	private static int rescanInTicks = -1;
	/** Ticks to wait after the server changes a slot: a buy changes several, one rescan covers them. */
	private static final int RESCAN_DELAY_TICKS = 4;

	/** The sign of the shop screen that's open right now (for BuyConfirm), or null if this isn't one. */
	public static ShopSign currentShopSign(AbstractContainerMenu handler) {
		return handler != null && handler == currentShopHandler ? currentShopSign : null;
	}

	public static boolean isCurrentShopScreen(AbstractContainerMenu handler) {
		return handler != null && handler == currentShopHandler;
	}

	/** Call this from a UseBlockCallback when the player right-clicks a chest/barrel. */
	public void onContainerInteract(BlockPos containerPos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		BlockState state = client.level.getBlockState(containerPos);
		ShopSign found = SignFinder.find(client.level, containerPos, state);
		// either half of a double chest opens the same shop — log it under its left half
		BlockPos shopPos = SignFinder.shopPos(client.level, containerPos, state).immutable();

		this.pendingContainerPos = found != null ? shopPos : null;
		this.pendingSign = found;
		this.pendingSetAtMillis = System.currentTimeMillis();
	}

	/** Call this from ScreenEvents.AFTER_INIT when a container screen opens. */
	public void onContainerScreenOpened(AbstractContainerMenu handler) {
		if (handler == currentShopHandler) return; // the same screen re-initialised (window resize), not a new open
		openStock = null; // a different screen: forget the last shop
		openPayment = null;
		openPos = null;
		rescanInTicks = -1;
		if (pendingSign == null || pendingContainerPos == null) {
			currentShopHandler = null; // opened a container with no shop sign on its front; ignore
			return;
		}
		boolean stale = System.currentTimeMillis() - pendingSetAtMillis > PENDING_TIMEOUT_MS;
		BlockPos containerPos = pendingContainerPos;
		ShopSign sign = pendingSign;
		pendingSign = null;
		pendingContainerPos = null;
		if (stale) {
			currentShopHandler = null; // this screen almost certainly isn't the one we clicked — see PENDING_TIMEOUT_MS
			return;
		}
		currentShopHandler = handler;
		currentShopSign = sign;
		// The screen opens before the server sends what's in the chest, so the
		// slots are still empty here; the scan runs once they arrive.
		awaitingContentsPos = containerPos;
	}

	/** Called after the server fills a container's slots (ClientPlayNetworkHandlerMixin). */
	public static void onContainerContent(int containerId) {
		AbstractContainerMenu handler = currentShopHandler;
		if (handler == null || handler.containerId != containerId) return;
		BlockPos containerPos = awaitingContentsPos;
		if (containerPos == null) { onSlotChanged(containerId); return; } // a refresh after a click
		awaitingContentsPos = null;
		scan(handler, currentShopSign, containerPos);
	}

	/** The server changed a slot of an open container (ClientPlayNetworkHandlerMixin): maybe someone bought something. */
	public static void onSlotChanged(int containerId) {
		AbstractContainerMenu handler = currentShopHandler;
		if (handler == null || handler.containerId != containerId || awaitingContentsPos != null || openStock == null) return;
		rescanInTicks = RESCAN_DELAY_TICKS;
	}

	/** Every client tick (QolHookManager). */
	public static void tick(Minecraft client) {
		if (rescanInTicks > 0 && --rescanInTicks == 0) {
			rescanInTicks = -1;
			rescanForBuys(client);
		}
		ShopUploader.tickSoon(client);
	}

	/**
	 * The open shop changed after a click: scan it again. Stock that went down while
	 * the payment in the chest went up is a purchase (yours, usually): it's logged as a
	 * sale and uploaded within seconds. The new scan becomes the shop's latest state.
	 */
	private static void rescanForBuys(Minecraft client) {
		AbstractContainerMenu handler = currentShopHandler;
		ShopSign sign = currentShopSign;
		BlockPos pos = openPos;
		if (handler == null || sign == null || pos == null || client.player == null || client.player.containerMenu != handler) return;
		ShopWorld world = WorldSelection.get();
		if (world == null) return;
		List<ShopEntry> entries = ShopEntryFactory.build(handler, sign, pos);
		Integer payment = OwnShopSaleTracker.paymentCount(sign, handler);

		boolean logged = false;
		if (openStock != null && openPayment != null && payment != null && payment > openPayment) {
			java.util.Map<String, ShopEntry> now = new java.util.HashMap<>();
			for (ShopEntry e : entries) now.put(e.key(), e);
			// what was paid covers this much of what went (2% slack for rounding), like the Worker's check
			double paid = payment - openPayment, goneValue = 0;
			java.util.List<ShopEntry> goneList = new java.util.ArrayList<>();
			java.util.List<Integer> gone = new java.util.ArrayList<>();
			for (ShopEntry b : openStock.values()) {
				ShopEntry n = now.get(b.key());
				int g = b.amountAvailable() - (n == null ? 0 : n.amountAvailable());
				if (g <= 0) continue;
				goneList.add(b);
				gone.add(g);
				goneValue += g * (b.price() / (double) Math.max(1, b.stackSize()));
			}
			double share = goneValue > 0 ? Math.min(1.0, paid * 1.02 / goneValue) : 1.0;
			for (int i = 0; i < goneList.size(); i++) {
				int units = (int) Math.round(gone.get(i) * share);
				if (units <= 0) continue;
				ShopLog.addSale(goneList.get(i), units);
				logged = true;
			}
		}

		ShopLog.replaceForPosition(world.label(), pos, entries);
		ShopLog.setPayment(world.label(), pos, payment, sign.currency());
		StockHolograms.record(world.label(), pos, entries);
		remember(entries, payment, pos);
		if (logged) ShopUploader.requestSoon();
	}

	private static void remember(List<ShopEntry> entries, Integer payment, BlockPos pos) {
		java.util.Map<String, ShopEntry> m = new java.util.HashMap<>();
		for (ShopEntry e : entries) m.put(e.key(), e);
		openStock = m;
		openPayment = payment;
		openPos = pos;
	}

	private static void scan(AbstractContainerMenu handler, ShopSign sign, BlockPos containerPos) {
		Minecraft client = Minecraft.getInstance();
		if (WorldSelection.ensureSet(client)) {
			List<ShopEntry> entries = ShopEntryFactory.build(handler, sign, containerPos);
			ShopWorld world = WorldSelection.get();
			if (world != null) {
				ShopLog.replaceForPosition(world.label(), containerPos, entries);
				Integer payment = OwnShopSaleTracker.paymentCount(sign, handler);
				ShopLog.setPayment(world.label(), containerPos, payment, sign.currency());
				remember(entries, payment, containerPos); // what a purchase will be compared to
				StockHolograms.record(world.label(), containerPos, entries);
			}
			// Deliberately NOT markScanned() here — manual opens don't participate
			// in the "recently scanned" particle marker (or the auto-scanner's own
			// rescan cooldown); that tracking is for the silent auto-scan path only.
			ScanChatLogger.maybePrint(client, entries);
			OwnShopSaleTracker.check(client, sign, containerPos, handler, world != null ? world.label() : null);
			OwnShopSaleTracker.acknowledge(containerPos); // you opened it yourself, so you've seen the payment
			if (world != null) {
				ShopVisitAlert.maybeAlert(client, world.label(), sign.seller());
				ShopVisitAlert.maybeSendShopInfo(world.label(), sign.seller());
			}
			WatchlistAlert.maybeAlert(client, entries);
		}
	}
}
