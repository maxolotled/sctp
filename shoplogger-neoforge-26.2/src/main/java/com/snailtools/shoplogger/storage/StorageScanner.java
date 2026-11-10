package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A newly added storage container's one-time first scan: the next time you're
 * within reach, it's opened silently (no screen) and read, like the shop
 * scanner does with shops. After that it's never opened silently again; your
 * own opens keep it up to date (StorageTracker).
 *
 * Any click of yours wins: the shared SilentScreenCoordinator lets go of a
 * silent open the moment you interact with something yourself, and this waits
 * a moment before trying again, so the chest you clicked always opens for you.
 */
public final class StorageScanner implements SilentScreenCoordinator.Listener {

	private static final StorageScanner INSTANCE = new StorageScanner();
	public static StorageScanner getInstance() { return INSTANCE; }

	private static final double REACH = 4.5;
	private static final long GAP_MS = 800L;      // between two silent opens
	private static final long HOLD_OFF_MS = 2500L; // after you click something yourself
	/** Silent opens without an answer before a container waits for you to open it yourself. */
	public static final int MAX_FAILURES = 3;
	private static final long RETRY_MS = 30_000L; // x the failures so far
	/** How soon after our click a window still counts as its answer. */
	private static final long ANSWER_MS = 2000L;

	private boolean armed, selfInteracting, resolving;
	private StorageIndex.Box armedBox;
	private int armedRows, armedSyncId = -1;
	private boolean armedShulker;
	private boolean yielding; // letting go for you, not a failed open
	private long lastAttempt, holdOffUntil;
	private int ticks;

	/** Containers nearer than this are checked for still being there (their chunk is surely loaded). */
	private static final double GONE_CHECK_RANGE = 24.0;
	/** How long a spot must stay without its container before it counts as broken (not a hiccup while blocks load). */
	private static final long GONE_AFTER_MS = 3000L;

	/**
	 * A container in My storage that was broken, or swapped for another kind,
	 * is taken out of it: whatever's there now isn't what was read. The chat
	 * says so, so a container that's put back can be added again with X+M.
	 */
	private void forgetBroken(Minecraft mc, long now) {
		String dim = StorageIndex.dimension();
		Vec3 eye = mc.player.getEyePosition();
		for (StorageIndex.Box b : new java.util.ArrayList<>(StorageIndex.boxes())) {
			if (!b.dim.equals(dim) || Vec3.atCenterOf(b.pos()).distanceTo(eye) > GONE_CHECK_RANGE || !mc.level.hasChunkAt(b.pos())) { b.missingSince = 0; continue; }
			net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(b.pos());
			boolean there = StorageContainers.isContainer(be) && StorageContainers.kind(be).equals(b.kind);
			if (there) { b.missingSince = 0; continue; }
			if (b.missingSince == 0) { b.missingSince = now; continue; }
			if (now - b.missingSince < GONE_AFTER_MS) continue;
			StorageIndex.forget(b);
			com.snailtools.shoplogger.ChatFormat.send(mc, com.snailtools.shoplogger.ChatFormat.INFO,
					b.label() + " is gone, so it's out of My storage. Put one back? Add it with X+M.");
		}
	}

	/** True during our own silent click, so the click handlers don't take it for yours. */
	public static boolean isSelfInteracting() { return INSTANCE.selfInteracting || INSTANCE.resolving; }

	/** You did something that may open a screen: let go of a silent open and wait a moment. */
	public void onPlayerMayOpenScreen() {
		if (selfInteracting || resolving) return;
		if (armed) {
			yielding = true;
			try { SilentScreenCoordinator.yieldToManualOpen(); } finally { yielding = false; }
		}
		holdOffUntil = System.currentTimeMillis() + HOLD_OFF_MS;
	}

	public void tick(Minecraft mc) {
		if (armed || mc.level == null || mc.player == null || mc.gameMode == null) return;
		if (!StorageSettings.isEnabled() || !StorageIndex.ready()) return;
		long now = System.currentTimeMillis();
		if (mc.player.containerMenu != mc.player.inventoryMenu) {
			// a container of yours is open: wait a bit after it closes too, in case you open the next one right away
			holdOffUntil = Math.max(holdOffUntil, now + HOLD_OFF_MS);
			return;
		}
		if (++ticks % 20 == 0) forgetBroken(mc, now);
		if (mc.player.isShiftKeyDown()) return;
		if (now < holdOffUntil || now - lastAttempt < GAP_MS) return;
		if (SilentScreenCoordinator.isArmed()) return;

		String dim = StorageIndex.dimension();
		Vec3 eye = mc.player.getEyePosition();
		StorageIndex.Box best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageIndex.Box b : StorageIndex.boxes()) {
			if (!b.waiting() || b.locked || !b.dim.equals(dim) || b.failures >= MAX_FAILURES || now < b.retryAt) continue;
			double d = Vec3.atCenterOf(b.pos()).distanceTo(eye);
			if (d <= REACH && d < bestDist) { best = b; bestDist = d; }
		}
		if (best == null) return;

		BlockPos pos = best.pos();
		if (!mc.level.hasChunkAt(pos)) return;
		if (!StorageContainers.isStorage(mc.level, pos)) {
			// broken or turned into a shop since you added it: nothing to read
			best.scannedAt = System.currentTimeMillis();
			StorageIndex.record(best, java.util.List.of());
			return;
		}
		if (!SilentScreenCoordinator.arm(this)) return;
		armed = true;
		armedBox = best;
		armedShulker = "shulker".equals(best.kind);
		armedRows = StorageContainers.rows(mc.level, pos);
		armedSyncId = -1;
		lastAttempt = now;

		Inventory inv = mc.player.getInventory();
		int original = inv.getSelectedSlot();
		int slot = pickSlot(inv);
		selfInteracting = true;
		try {
			if (slot != original) inv.setSelectedSlot(slot);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		} finally {
			if (slot != original) inv.setSelectedSlot(original);
			selfInteracting = false;
		}
	}

	/** An empty hotbar slot if there is one, else a plain item, so no server item fires on the click. */
	private static int pickSlot(Inventory inv) {
		int current = inv.getSelectedSlot(), size = Inventory.getSelectionSize();
		if (inv.getItem(current).isEmpty()) return current;
		for (int i = 0; i < size; i++) if (inv.getItem(i).isEmpty()) return i;
		if (ShopAutoScanner.isPlainVanilla(inv.getItem(current))) return current;
		for (int i = 0; i < size; i++) if (ShopAutoScanner.isPlainVanilla(inv.getItem(i))) return i;
		return current;
	}

	@Override
	public boolean accepts(AbstractContainerMenu handler) {
		if (!armed) return true;
		if (armedShulker) return handler instanceof ShulkerBoxMenu;
		return handler instanceof ChestMenu chest && chest.getRowCount() == armedRows;
	}

	/**
	 * Our click got a window that isn't the container's own (a locked shulker or
	 * chest the server shows read-only in a menu of its own). Close it before
	 * it's ever drawn and don't open that container silently again; it's read
	 * when you open it yourself. Only right after our click, so a window you
	 * opened is never taken.
	 */
	@Override
	public boolean swallowUnexpected(AbstractContainerMenu handler) {
		if (!armed || armedBox == null || System.currentTimeMillis() - lastAttempt > ANSWER_MS) return false;
		Minecraft mc = Minecraft.getInstance();
		StorageIndex.markLocked(armedBox);
		resolving = true;
		try {
			if (mc.getConnection() != null) mc.getConnection().send(new ServerboundContainerClosePacket(handler.containerId));
			if (mc.player != null && mc.player.containerMenu == handler) mc.player.containerMenu = mc.player.inventoryMenu;
		} finally {
			resolving = false;
		}
		disarm();
		return true;
	}

	@Override
	public void onScreenSuppressed(AbstractContainerMenu handler) {
		if (armed) armedSyncId = handler.containerId;
	}

	@Override
	public void onInventorySynced(int syncId, ClientPacketListener net) {
		if (!armed || syncId != armedSyncId) return;
		Minecraft mc = Minecraft.getInstance();
		resolving = true;
		try {
			AbstractContainerMenu menu = mc.player == null ? null : mc.player.containerMenu;
			if (menu != null && menu.containerId == syncId && accepts(menu)) StorageIndex.record(armedBox, StorageIndex.read(menu));
			net.send(new ServerboundContainerClosePacket(syncId));
			if (mc.player != null) mc.player.containerMenu = mc.player.inventoryMenu;
		} finally {
			resolving = false;
		}
		disarm();
	}

	@Override
	public void onWatchdogTimeout() {
		// no answer (blocked lid, someone else's claim...): try it a few more times, slower, then leave it to you
		if (armedBox != null && !yielding) {
			armedBox.failures++;
			armedBox.retryAt = System.currentTimeMillis() + RETRY_MS * armedBox.failures;
			if (armedBox.failures >= MAX_FAILURES) StorageIndex.touch();
		}
		disarm();
		holdOffUntil = System.currentTimeMillis() + HOLD_OFF_MS;
	}

	private void disarm() {
		armed = false;
		armedBox = null;
		armedSyncId = -1;
		SilentScreenCoordinator.disarm(this);
	}
}
