package com.snailtools.shoplogger.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Your own opens of an indexed container (or your ender chest): it's read the
 * moment its contents arrive, again a few ticks after anything in it changes
 * (you put something in or took something out), and once more as you close it.
 * Each read logs the difference (StorageIndex.record).
 */
public final class StorageTracker {

	private StorageTracker() {}

	private static final long PENDING_MS = 3000L;
	private static final int RESCAN_DELAY_TICKS = 3;

	private static StorageIndex.Box pending;
	private static long pendingAt;
	private static StorageIndex.Box openBox;
	private static AbstractContainerMenu openMenu;
	private static int rescanIn = -1;

	/** A right-click on a block by you (InteractionMixin), never the scanners' own. */
	public static void onUse(BlockPos clicked) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !StorageSettings.isEnabled() || !StorageIndex.ready()) return;
		BlockEntity be = mc.level.getBlockEntity(clicked);
		StorageIndex.Box box = null;
		if (StorageContainers.isEnderChest(be)) box = StorageIndex.ender();
		else if (StorageContainers.isContainer(be)) box = StorageIndex.find(StorageIndex.dimension(), StorageContainers.canonical(mc.level, clicked));
		if (box != null) {
			pending = box;
			pendingAt = System.currentTimeMillis();
			StorageHighlight.onOpened(box);
		}
	}

	/** A container's full contents arrived (ClientPlayNetworkHandlerMixin). */
	public static void onContent(int containerId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null || menu.containerId != containerId) return;
		if (openMenu == menu) { rescanIn = RESCAN_DELAY_TICKS; return; } // a refresh of the one that's open
		if (pending == null || System.currentTimeMillis() - pendingAt > PENDING_MS) return;
		if (!(menu instanceof ChestMenu) && !(menu instanceof ShulkerBoxMenu)) return;
		openBox = pending;
		openMenu = menu;
		pending = null;
		StorageIndex.record(openBox, StorageIndex.read(menu));
	}

	/** One slot of an open container changed (ClientPlayNetworkHandlerMixin). */
	public static void onSlotChanged(int containerId) {
		if (openMenu != null && openMenu.containerId == containerId) rescanIn = RESCAN_DELAY_TICKS;
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		StorageIndex.tick();
		if (openMenu == null) return;
		boolean stillOpen = mc.player != null && mc.player.containerMenu == openMenu;
		if (!stillOpen) {
			// closed: one last read of what the screen showed
			StorageIndex.record(openBox, StorageIndex.read(openMenu));
			openMenu = null;
			openBox = null;
			rescanIn = -1;
			return;
		}
		if (rescanIn > 0 && --rescanIn == 0) {
			rescanIn = -1;
			StorageIndex.record(openBox, StorageIndex.read(openMenu));
		}
	}
}
