package com.snailtools.shoplogger;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

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
	private static ScreenHandler currentShopHandler = null;

	public static boolean isCurrentShopScreen(ScreenHandler handler) {
		return handler != null && handler == currentShopHandler;
	}

	/** Call this from a UseBlockCallback when the player right-clicks a chest/barrel. */
	public void onContainerInteract(BlockPos containerPos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return;

		BlockState state = client.world.getBlockState(containerPos);
		ShopSign found = SignFinder.find(client.world, containerPos, state);

		this.pendingContainerPos = found != null ? containerPos : null;
		this.pendingSign = found;
		this.pendingSetAtMillis = System.currentTimeMillis();
	}

	/** Call this from ScreenEvents.AFTER_INIT when a container screen opens. */
	public void onContainerScreenOpened(ScreenHandler handler) {
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

		MinecraftClient client = MinecraftClient.getInstance();
		if (WorldSelection.ensureSet(client)) {
			List<ShopEntry> entries = ShopEntryFactory.build(handler, sign, containerPos);
			ShopWorld world = WorldSelection.get();
			if (world != null) {
				ShopLog.replaceForPosition(world.label(), containerPos, entries);
			}
			ShopAutoScanner.getInstance().markScanned(containerPos);
			ScanChatLogger.maybePrint(client, entries);
			OwnShopSaleTracker.check(client, sign, containerPos, entries);
			if (world != null) ShopVisitAlert.maybeAlert(client, world.label(), sign.seller());
			WatchlistAlert.maybeAlert(client, entries);
		}
	}
}
