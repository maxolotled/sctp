package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.ChatFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Where is it?": every container holding an item lights up (Settings › My
 * storage: a white glow through walls, a beacon beam, or both), and the line
 * above your hotbar names the nearest one's coordinates. Opening one of them
 * turns off just that one (or all of them, per Settings), and inside it the
 * slots holding the item are outlined. X turns everything off.
 */
public final class StorageHighlight {

	private StorageHighlight() {}

	public static final int GLOW_COLOR = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0x50FFFFFF;
	private static final int SLOT_BORDER = 0xFFFFFFFF;

	private static final List<StorageIndex.Box> targets = new ArrayList<>();
	private static String label;
	/** The item being looked for (base item + exact name), for outlining its slots; null when it's containers, not an item. */
	private static String itemBase, itemName;
	/** Set once every lit container has been opened: the outlines stay until that container is closed. */
	private static boolean allFound;
	private static int tickCount;

	public static boolean isActive() { return !targets.isEmpty(); }

	/** Highlights the containers holding an item (baseItem null for a rare, by its name). */
	public static void showItem(String baseItem, String name) {
		show(name, StorageIndex.holding(baseItem, name));
	}

	/** Highlights the containers holding exactly this item (decorations in the name count), and outlines its slots once you open one. */
	public static void showExactItem(String baseItem, String name) {
		show(name, StorageIndex.holdingExactly(baseItem, name));
		if (!targets.isEmpty()) { itemBase = baseItem; itemName = name; }
	}

	/** The containers still waiting for their first read that are nearest to you. */
	public static void showNearestWaiting(int howMany) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String dim = StorageIndex.dimension();
		Vec3 eye = mc.player.getEyePosition();
		List<StorageIndex.Box> waiting = new ArrayList<>();
		for (StorageIndex.Box b : StorageIndex.boxes()) if (b.waiting() && b.dim.equals(dim)) waiting.add(b);
		if (waiting.isEmpty()) { ChatFormat.send(mc, ChatFormat.INFO, "No containers here are waiting for their first read."); return; }
		waiting.sort(Comparator.comparingDouble((StorageIndex.Box b) -> Vec3.atCenterOf(b.pos()).distanceTo(eye)));
		show("Waiting to be read", waiting.subList(0, Math.min(howMany, waiting.size())));
	}

	public static void show(String what, List<StorageIndex.Box> boxes) {
		Minecraft mc = Minecraft.getInstance();
		clear();
		if (boxes.isEmpty()) { ChatFormat.send(mc, ChatFormat.INFO, what + " isn't in your storage."); return; }
		String dim = StorageIndex.dimension();
		List<StorageIndex.Box> elsewhere = new ArrayList<>();
		boolean ender = false;
		for (StorageIndex.Box b : boxes) {
			if (b.isEnder()) ender = true;
			else if (b.dim.equals(dim)) targets.add(b);
			else elsewhere.add(b);
		}
		label = what;
		mc.setScreenAndShow(null); // back to the world, where the highlight is
		if (ender) ChatFormat.send(mc, ChatFormat.INFO, what + ": also in your ender chest.");
		if (!elsewhere.isEmpty()) {
			StorageIndex.Box b = elsewhere.get(0);
			ChatFormat.send(mc, ChatFormat.INFO, what + ": " + elsewhere.size() + " container" + (elsewhere.size() == 1 ? "" : "s") + " in another dimension ("
					+ niceDim(b.dim) + ", first at " + b.where() + ").");
		}
		if (targets.isEmpty() && !ender && elsewhere.isEmpty()) ChatFormat.send(mc, ChatFormat.INFO, what + " isn't in your storage.");
		tickCount = 0;
	}

	/** X: everything off, the slot outlines too. */
	public static void clear() {
		targets.clear();
		label = null;
		itemBase = itemName = null;
		allFound = false;
	}

	/** You opened a container: if it's one of the lit-up ones, it (or all of them) stops. */
	public static void onOpened(StorageIndex.Box box) {
		if (!targets.contains(box) && !(box.isEnder() && label != null)) return;
		if (StorageSettings.openStops() == StorageSettings.OpenStops.ALL || box.isEnder()) targets.clear();
		else targets.remove(box);
		if (targets.isEmpty()) {
			label = null;
			allFound = true; // the slot outlines stay while this container is open
		}
	}

	/** Every client tick: keeps the line above the hotbar up to date. */
	public static void tick(Minecraft mc) {
		if (allFound && !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) clear();
		if (targets.isEmpty() || mc.player == null || mc.level == null) return;
		if (!StorageSettings.isEnabled() || !targets.get(0).dim.equals(StorageIndex.dimension())) { clear(); return; }
		if (tickCount++ % 10 != 0) return;
		Vec3 eye = mc.player.getEyePosition();
		StorageIndex.Box near = targets.get(0);
		for (StorageIndex.Box b : targets) if (Vec3.atCenterOf(b.pos()).distanceTo(eye) < Vec3.atCenterOf(near.pos()).distanceTo(eye)) near = b;
		int dist = (int) Math.round(Vec3.atCenterOf(near.pos()).distanceTo(eye));
		String more = targets.size() > 1 ? " (+" + (targets.size() - 1) + " more)" : "";
		mc.player.sendOverlayMessage(Component.literal(label + ": " + near.where() + " · " + dist + " blocks" + more + " · X to stop"));
	}

	/** Inside a container screen: outlines the slots holding the item you're looking for (ScreenMixin). */
	public static void onScreenRender(Screen screen, GuiGraphicsExtractor g) {
		if (itemName == null || !(screen instanceof AbstractContainerScreen<?> cs)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int left = com.snailtools.shoplogger.RareRentalHighlighter.screenField(cs, "leftPos");
		int top = com.snailtools.shoplogger.RareRentalHighlighter.screenField(cs, "topPos");
		for (Slot slot : cs.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) continue;
			if (!StorageIndex.holdsExactly(slot.getItem(), itemBase, itemName)) continue;
			int x = left + slot.x, y = top + slot.y;
			g.fill(x, y, x + 16, y + 16, SLOT_FILL);
			g.fill(x - 1, y - 1, x + 17, y, SLOT_BORDER);
			g.fill(x - 1, y + 16, x + 17, y + 17, SLOT_BORDER);
			g.fill(x - 1, y, x, y + 16, SLOT_BORDER);
			g.fill(x + 16, y, x + 17, y + 16, SLOT_BORDER);
		}
	}

	public static List<BlockPos> positions() {
		List<BlockPos> out = new ArrayList<>();
		for (StorageIndex.Box b : targets) out.add(b.pos());
		return out;
	}

	/** True while something is lit up or outlined, so X turns it off before doing anything else. */
	public static boolean isShowing() { return isActive() || itemName != null; }

	public static boolean glow() { return isActive() && StorageSettings.highlightStyle() != StorageSettings.HighlightStyle.BEAM; }
	public static boolean beam() { return isActive() && StorageSettings.highlightStyle() != StorageSettings.HighlightStyle.GLOW; }

	private static String niceDim(String dim) {
		String s = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		if (s.equals("overworld")) return "the overworld";
		if (s.equals("the_nether")) return "the Nether";
		if (s.equals("the_end")) return "the End";
		return s.replace('_', ' ').toLowerCase(Locale.ROOT);
	}
}
