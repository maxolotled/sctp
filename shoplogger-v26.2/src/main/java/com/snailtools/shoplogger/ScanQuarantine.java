package com.snailtools.shoplogger;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Defensive safety net against "faulty scans": SilentScreenCoordinator only
 * ever allows one silently-opened screen in flight at a time, so if the
 * player manually opens a chest, barrel, or ender chest at nearly the same
 * moment an unrelated auto-scan resolves, there's a narrow race where synced
 * inventory data could get cross-attributed to the wrong container.
 *
 * Rather than trying to close every possible timing edge case, this
 * quarantines a 3-second window around every manual chest/barrel/ender-chest
 * open: any OTHER scan that finishes while that window is active gets
 * discarded rather than logged, on the theory that it might be contaminated.
 * The container the player is actually opening is exempted from its own
 * window — that scan is exactly what the player asked for and is trusted
 * normally; only scans for a DIFFERENT position are at risk. An ender chest
 * open has no legitimate ShopLog scan of its own, so it exempts nothing.
 *
 * This costs at most a couple of legitimate auto-scans right around a manual
 * open; the auto-scanner simply picks the same shop back up on its next pass.
 */
public final class ScanQuarantine {

	private static final long WINDOW_MS = 3000L;

	private record Window(long start, long end, BlockPos exemptPos) {}

	private static final List<Window> windows = new ArrayList<>();

	private ScanQuarantine() {}

	/**
	 * Call the moment a chest/barrel/ender chest is manually opened.
	 * exemptPos is the container being opened (null for an ender chest,
	 * which has no ShopLog scan of its own to protect).
	 */
	public static synchronized void markManualOpen(BlockPos exemptPos) {
		long now = System.currentTimeMillis();
		windows.add(new Window(now - WINDOW_MS, now + WINDOW_MS, exemptPos));
		ShopLog.purgeLoggedBetween(now - WINDOW_MS, now, exemptPos);
		prune(now);
	}

	/** True if a scan resolving right now for `pos` should be distrusted and discarded. */
	public static synchronized boolean shouldDiscard(BlockPos pos) {
		long now = System.currentTimeMillis();
		prune(now);
		for (Window w : windows) {
			if (now < w.start() || now > w.end()) continue;
			if (pos != null && pos.equals(w.exemptPos())) continue; // the container that triggered this window — trust it
			return true;
		}
		return false;
	}

	private static void prune(long now) {
		windows.removeIf(w -> w.end() < now);
	}
}
