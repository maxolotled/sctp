package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Empty hand" items: when you right-click a block that does something on its
 * own (door, chest, button, lever, crafting table...) while holding one of
 * these, the click is made with an empty hotbar slot instead, so the item's
 * own right-click (a rare's ability, a plugin tool) doesn't fire. Works
 * everywhere, not just in shop worlds.
 *
 * Items are matched exactly: same item type AND same custom name, so
 * registering your named sword leaves plain swords alone. Registered from
 * Settings > Empty hand: press Add, hold the item, press X.
 */
public final class EmptyHandItems {

	private EmptyHandItems() {}

	/** One registered item. Stored as-is in the config, so keep the fields simple. */
	public static final class Entry {
		public String item;  // e.g. "minecraft:diamond_sword"
		public String name;  // custom name, "" when it has none
		public String label; // what the settings list shows

		public Entry() {}

		Entry(String item, String name, String label) {
			this.item = item;
			this.name = name;
			this.label = label;
		}
	}

	private static final String CONFIG_ENABLED = "emptyHand/enabled";
	private static final String CONFIG_ITEMS = "emptyHand/items";

	private static boolean capturing = false;
	private static final Map<Class<?>, Boolean> CLICKABLE = new ConcurrentHashMap<>();

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	public static List<Entry> items() {
		Entry[] saved = Config.get(CONFIG_ITEMS, Entry[].class);
		List<Entry> out = new ArrayList<>();
		if (saved != null) for (Entry e : saved) if (e != null && e.item != null) out.add(e);
		return out;
	}

	public static void remove(Entry entry) {
		List<Entry> list = items();
		list.removeIf(e -> e.item.equals(entry.item) && nameOf(e).equals(nameOf(entry)));
		Config.update(CONFIG_ITEMS, list.toArray(new Entry[0]));
	}

	private static String nameOf(Entry e) {
		return e.name == null ? "" : e.name;
	}

	private static String customName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	public static boolean isRegistered(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String name = customName(stack);
		for (Entry e : items()) {
			if (e.item.equals(id) && nameOf(e).equals(name)) return true;
		}
		return false;
	}

	// ---- registering: Settings "Add" -> hold the item -> press X ----

	public static boolean isCapturing() {
		return capturing;
	}

	public static void startCapture() {
		capturing = true;
	}

	public static void cancelCapture() {
		capturing = false;
	}

	/** While capturing, keep reminding the player what to do (action bar). */
	public static void tick(Minecraft client) {
		if (capturing && client.player != null && client.gui.screen() == null) {
			client.player.sendOverlayMessage(Component.literal("Hold the item to register, then press X"));
		}
	}

	/** Called when X is pressed while capturing: registers the held item. Returns false if the hand was empty. */
	public static boolean captureHeld(Minecraft client) {
		capturing = false;
		if (client.player == null) return false;
		ItemStack held = client.player.getMainHandItem();
		if (held.isEmpty()) {
			ChatFormat.send(client, ChatFormat.NEUTRAL, "Your hand was empty, so nothing was added.");
			return false;
		}
		if (isRegistered(held)) {
			ChatFormat.send(client, ChatFormat.NEUTRAL, held.getHoverName().getString() + " is already on your empty-hand list.");
			return true;
		}
		List<Entry> list = items();
		list.add(new Entry(BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), customName(held), held.getHoverName().getString()));
		Config.update(CONFIG_ITEMS, list.toArray(new Entry[0]));
		ChatFormat.send(client, ChatFormat.SUCCESS, "Added " + held.getHoverName().getString() + ": clicking doors, chests and other blocks with it now uses an empty hand.");
		return true;
	}

	// ---- the swap itself (see EmptyHandMixin) ----

	/**
	 * The hotbar slot to click with instead of the selected one, or -1 to
	 * leave the click alone: only for a registered item in the main hand,
	 * not sneaking (a sneak-click means "use the item on this block"), on a
	 * block that does something when clicked.
	 */
	public static int slotFor(Minecraft client, Level level, BlockPos pos) {
		if (!isEnabled() || client.player == null || client.player.isShiftKeyDown()) return -1;
		if (ShopAutoScanner.getInstance().isSelfInteracting()) return -1; // the scanner picks its own slot
		Inventory inv = client.player.getInventory();
		if (!isRegistered(inv.getItem(inv.getSelectedSlot()))) return -1;
		if (!isClickable(level, pos, level.getBlockState(pos))) return -1;

		int size = Inventory.getSelectionSize();
		for (int i = 0; i < size; i++) if (inv.getItem(i).isEmpty()) return i;
		for (int i = 0; i < size; i++) {
			ItemStack s = inv.getItem(i);
			if (ShopAutoScanner.isPlainVanilla(s) && !isRegistered(s)) return i;
		}
		return -1; // nothing safe to click with: leave it to the item
	}

	/**
	 * Does this block do something when clicked with an empty hand? True for
	 * any block whose class overrides vanilla's useWithoutItem (doors,
	 * trapdoors, gates, chests, buttons, levers, beds, note blocks...) or
	 * that opens a menu (crafting tables, anvils...).
	 */
	public static boolean isClickable(Level level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		boolean overrides = CLICKABLE.computeIfAbsent(block.getClass(), EmptyHandItems::overridesUseWithoutItem);
		return overrides || state.getMenuProvider(level, pos) != null;
	}

	private static boolean overridesUseWithoutItem(Class<?> c) {
		for (Class<?> k = c; k != null && k != BlockBehaviour.class && k != Object.class; k = k.getSuperclass()) {
			for (var m : k.getDeclaredMethods()) {
				if (m.getName().equals("useWithoutItem")) return true;
			}
		}
		return false;
	}
}
