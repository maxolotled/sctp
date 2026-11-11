package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.SilentScreenCoordinator;
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
 *
 * A window only counts for the container you clicked when it has that
 * container's shape (a double chest's 6 rows, a shulker box's own screen...),
 * and not right after a silent open let go for your click: that window may be
 * the silent one's, arriving late. Skipping a read is better than writing one
 * container's items into another.
 */
public final class StorageTracker {

	private StorageTracker() {}

	private static final long PENDING_MS = 3000L;
	private static final long AFTER_YIELD_MS = 2000L;
	private static final int RESCAN_DELAY_TICKS = 3;

	private static StorageIndex.Box pending;
	private static long pendingAt;
	private static int pendingRows; // 6 for a double chest, else 3; 0 = a shulker box's own screen
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
			pendingRows = box.isEnder() ? 3 : "shulker".equals(StorageContainers.kind(be)) ? 0 : StorageContainers.rows(mc.level, clicked);
			StorageHighlight.onOpened(box);
		}
	}

	/** Does this window look like the container that was clicked? */
	private static boolean fits(AbstractContainerMenu menu, int rows) {
		if (rows == 0) return menu instanceof ShulkerBoxMenu;
		return menu instanceof ChestMenu chest && chest.getRowCount() == rows;
	}

	/** A container's full contents arrived (ClientPlayNetworkHandlerMixin). */
	public static void onContent(int containerId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null || menu.containerId != containerId) return;
		if (openMenu == menu) { rescanIn = RESCAN_DELAY_TICKS; return; } // a refresh of the one that's open
		if (openMenu != null && pending == null && fits(menu, rowsOf(openBox))) {
			// the server opened the same container's window again (no click in between): keep following it
			openMenu = menu;
			rescanIn = RESCAN_DELAY_TICKS;
			return;
		}
		closeOpen();
		if (pending == null || System.currentTimeMillis() - pendingAt > PENDING_MS) return;
		StorageIndex.Box box = pending;
		pending = null;
		if (pendingAt - SilentScreenCoordinator.lastYieldAt() < AFTER_YIELD_MS) return; // may be the silent open's window
		if (!fits(menu, pendingRows)) return; // not the container you clicked (or a locked one's own window)
		openBox = box;
		openMenu = menu;
		rememberTitle(box, mc);
		StorageIndex.record(openBox, StorageIndex.read(menu));
	}

	/** An anvil name shows as the window's title: kept for the "Container name" holograms. A plain "Chest" or "Large Chest" isn't one. */
	private static void rememberTitle(StorageIndex.Box box, Minecraft mc) {
		if (box.isEnder() || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) return;
		net.minecraft.network.chat.Component t = s.getTitle();
		boolean plain = t.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().startsWith("container.");
		String title = plain ? null : t.getString().trim();
		if (title != null && title.isEmpty()) title = null;
		if (!java.util.Objects.equals(title, box.title)) { box.title = title; StorageIndex.touch(); }
	}

	private static int rowsOf(StorageIndex.Box box) {
		Minecraft mc = Minecraft.getInstance();
		if (box == null || mc.level == null) return -1;
		if (box.isEnder()) return 3;
		if ("shulker".equals(box.kind)) return 0;
		return StorageContainers.rows(mc.level, box.pos());
	}

	/** The open container was closed (or replaced): one last read of what its window showed. */
	private static void closeOpen() {
		if (openMenu != null) StorageIndex.record(openBox, StorageIndex.read(openMenu));
		openMenu = null;
		openBox = null;
		rescanIn = -1;
	}

	/** One slot of an open container changed (ClientPlayNetworkHandlerMixin). */
	public static void onSlotChanged(int containerId) {
		if (openMenu != null && openMenu.containerId == containerId) rescanIn = RESCAN_DELAY_TICKS;
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		StorageIndex.tick();
		if (openMenu == null) return;
		AbstractContainerMenu now = mc.player == null ? null : mc.player.containerMenu;
		if (now != openMenu) {
			// a new window of the same shape without a click: the server re-opened it; wait for its contents (onContent)
			if (now != null && now != mc.player.inventoryMenu && pending == null && fits(now, rowsOf(openBox))) return;
			closeOpen();
			return;
		}
		if (rescanIn > 0 && --rescanIn == 0) {
			rescanIn = -1;
			StorageIndex.record(openBox, StorageIndex.read(openMenu));
		}
	}
}
