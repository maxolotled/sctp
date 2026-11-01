package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.ChatFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Where is it?": every container holding an item lights up (Settings › My
 * storage: a white glow through walls, a beacon beam, or both), and the line
 * above your hotbar names the nearest one's coordinates. It stays until you
 * open one of them or press X.
 */
public final class StorageHighlight {

	private StorageHighlight() {}

	public static final int GLOW_COLOR = 0xFFFFFFFF;

	private static final List<StorageIndex.Box> targets = new ArrayList<>();
	private static String label;
	private static int tickCount;

	public static boolean isActive() { return !targets.isEmpty(); }

	/** Highlights the containers holding an item (baseItem null for a rare) and says where the nearest is. */
	public static void showItem(String baseItem, String name) {
		show(name, StorageIndex.holding(baseItem, name));
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

	public static void clear() {
		targets.clear();
		label = null;
	}

	/** You opened a container: if it's one of the highlighted ones, you found it. */
	public static void onOpened(StorageIndex.Box box) {
		if (targets.contains(box) || (box.isEnder() && label != null)) clear();
	}

	/** Every client tick: keeps the line above the hotbar up to date. */
	public static void tick(Minecraft mc) {
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

	public static List<BlockPos> positions() {
		List<BlockPos> out = new ArrayList<>();
		for (StorageIndex.Box b : targets) out.add(b.pos());
		return out;
	}

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
