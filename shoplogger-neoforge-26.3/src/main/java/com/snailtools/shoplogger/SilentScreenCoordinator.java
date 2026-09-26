package com.snailtools.shoplogger;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Arbitrates the mixin-based "open a container screen without ever showing
 * it" mechanism (see MinecraftClientMixin / ClientPlayNetworkHandlerMixin)
 * between the different features that use it, so only one can be armed at a
 * time regardless of who triggered the open.
 */
public final class SilentScreenCoordinator {

	public interface Listener {
		/**
		 * Whether a container screen arriving right now could plausibly be the
		 * one this listener's own silent interaction asked for. Screens are
		 * matched purely by arrival order, so without this ANY container that
		 * happens to open while armed (the player's own chest, an NPC/command
		 * GUI, a late screen from an earlier timed-out open) would get handed
		 * over and its contents logged as the wrong thing. A rejected screen is
		 * shown to the player normally and this listener is released.
		 */
		default boolean accepts(AbstractContainerMenu handler) { return true; }

		void onScreenSuppressed(AbstractContainerMenu handler);
		void onInventorySynced(int syncId, ClientPacketListener netHandler);

		/**
		 * Called if this listener stayed armed too long without completing —
		 * the interaction it triggered was blocked, denied, or never got a
		 * server response (e.g. another mod cancelled it, the target chest
		 * turned out gone, lag). Must reset the listener's own local state.
		 */
		void onWatchdogTimeout();
	}

	/**
	 * If a listener is armed for longer than this without completing, it's
	 * force-disarmed. Without this, a single failed silent interaction leaves
	 * SilentScreenCoordinator permanently "armed," which — since the mixins
	 * suppress ANY screen while armed, not just the one we're waiting for —
	 * would silently break every container screen for the rest of the
	 * session, including the player's own inventory (E).
	 */
	private static final long WATCHDOG_TIMEOUT_MS = 5000L;

	private static Listener current;
	private static long armedAtMillis;

	private SilentScreenCoordinator() {}

	public static boolean isArmed() {
		return current != null;
	}

	/** Returns false (and does nothing) if another listener is already armed. */
	public static boolean arm(Listener listener) {
		if (current != null) return false;
		current = listener;
		armedAtMillis = System.currentTimeMillis();
		return true;
	}

	public static void disarm(Listener listener) {
		if (current == listener) {
			current = null;
		}
	}

	public static boolean accepts(AbstractContainerMenu handler) {
		return current == null || current.accepts(handler);
	}

	public static void onScreenSuppressed(AbstractContainerMenu handler) {
		if (current != null) current.onScreenSuppressed(handler);
	}

	public static void onInventorySynced(int syncId, ClientPacketListener netHandler) {
		if (current != null) current.onInventorySynced(syncId, netHandler);
	}

	/** Call once per client tick. */
	public static void tickWatchdog() {
		if (current == null) return;
		if (System.currentTimeMillis() - armedAtMillis < WATCHDOG_TIMEOUT_MS) return;

		Listener stuck = current;
		current = null; // clear first so the listener's own cleanup (which may re-call disarm(this)) is a harmless no-op
		stuck.onWatchdogTimeout();
	}

	/**
	 * Immediately releases whatever silent interaction is currently armed, as
	 * if it had watchdog-timed-out, so a container screen the player is about
	 * to open by hand is never suppressed by it. Call this right when a
	 * manual container interaction is detected — manual clicking always wins
	 * over a silent background scan. A no-op if nothing is armed.
	 */
	public static void yieldToManualOpen() {
		if (current == null) return;
		Listener stuck = current;
		current = null;
		stuck.onWatchdogTimeout();
	}
}
