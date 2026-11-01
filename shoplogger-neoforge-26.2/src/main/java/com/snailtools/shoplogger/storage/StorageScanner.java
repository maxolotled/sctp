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

	private boolean armed, selfInteracting, resolving;
	private StorageIndex.Box armedBox;
	private int armedRows, armedSyncId = -1;
	private boolean armedShulker;
	private long lastAttempt, holdOffUntil;

	/** True during our own silent click, so the click handlers don't take it for yours. */
	public static boolean isSelfInteracting() { return INSTANCE.selfInteracting || INSTANCE.resolving; }

	/** You did something that may open a screen: let go of a silent open and wait a moment. */
	public void onPlayerMayOpenScreen() {
		if (selfInteracting || resolving) return;
		if (armed) SilentScreenCoordinator.yieldToManualOpen();
		holdOffUntil = System.currentTimeMillis() + HOLD_OFF_MS;
	}

	public void tick(Minecraft mc) {
		if (armed || mc.level == null || mc.player == null || mc.gameMode == null) return;
		if (!StorageSettings.isEnabled() || !StorageIndex.ready()) return;
		if (mc.player.containerMenu != mc.player.inventoryMenu || mc.player.isShiftKeyDown()) return;
		long now = System.currentTimeMillis();
		if (now < holdOffUntil || now - lastAttempt < GAP_MS) return;
		if (SilentScreenCoordinator.isArmed()) return;

		String dim = StorageIndex.dimension();
		Vec3 eye = mc.player.getEyePosition();
		StorageIndex.Box best = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageIndex.Box b : StorageIndex.boxes()) {
			if (!b.waiting() || !b.dim.equals(dim)) continue;
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
