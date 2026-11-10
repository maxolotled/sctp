package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which group an item in My storage belongs to, for browsing by group: a rare
 * by its catalog category ("Decor") and type ("Snail", "Sword"), a vanilla
 * item by the creative menu tab it's in ("Building blocks", "Redstone"...).
 */
public final class StorageGroups {

	private StorageGroups() {}

	/** The creative tabs, in the creative menu's order, with plain English names. */
	private static final Map<ResourceKey<CreativeModeTab>, String> TABS = new LinkedHashMap<>();
	static {
		TABS.put(CreativeModeTabs.BUILDING_BLOCKS, "Building blocks");
		TABS.put(CreativeModeTabs.COLORED_BLOCKS, "Colored blocks");
		TABS.put(CreativeModeTabs.NATURAL_BLOCKS, "Natural blocks");
		TABS.put(CreativeModeTabs.FUNCTIONAL_BLOCKS, "Functional blocks");
		TABS.put(CreativeModeTabs.REDSTONE_BLOCKS, "Redstone");
		TABS.put(CreativeModeTabs.TOOLS_AND_UTILITIES, "Tools & utilities");
		TABS.put(CreativeModeTabs.COMBAT, "Combat");
		TABS.put(CreativeModeTabs.FOOD_AND_DRINKS, "Food & drinks");
		TABS.put(CreativeModeTabs.INGREDIENTS, "Ingredients");
		TABS.put(CreativeModeTabs.SPAWN_EGGS, "Spawn eggs");
	}
	public static final String OTHER = "Other";

	private static Map<Item, String> tabOf;
	private static Map<String, RareItem> rares; // by rareNormalize(name)
	private static boolean loading;

	/** Starts loading the rare catalog (once); until it's there, rares group by name only. */
	public static void load() {
		if (rares != null || loading) return;
		loading = true;
		WebDataClient.fetchRareCatalog().thenAccept(list -> {
			Map<String, RareItem> m = new HashMap<>();
			for (RareItem r : list) if (r.name != null) m.putIfAbsent(MatchUtil.rareNormalize(r.name), r);
			Minecraft.getInstance().execute(() -> { rares = m; loading = false; StorageIndex.touch(); });
		}).exceptionally(ex -> { loading = false; return null; });
	}

	public static boolean ready() { return rares != null; }

	/** The catalog rare an item in storage is, or null for a vanilla item. */
	public static RareItem rare(StorageIndex.Item item) {
		return rares == null ? null : rares.get(MatchUtil.rareNormalize(item.n));
	}

	/** "Decor", "Collectibles"... for a rare; the creative tab's name for a vanilla item. */
	public static String groupOf(StorageIndex.Item item) {
		RareItem r = rare(item);
		if (r != null) return r.category == null || r.category.isEmpty() ? "Rares" : r.category;
		return tabOf(item.b);
	}

	/** A rare's type ("Snail", "Sword"...), or null. */
	public static String typeOf(StorageIndex.Item item) {
		RareItem r = rare(item);
		return r == null || r.typeSlot == null || r.typeSlot.isEmpty() ? null : r.typeSlot;
	}

	public static List<String> tabNames() { return List.copyOf(TABS.values()); }

	private static String tabOf(String baseItem) {
		if (tabOf == null && !buildTabs()) return OTHER;
		Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.tryParse(baseItem));
		return tabOf.getOrDefault(item, OTHER);
	}

	/** Item -> its first creative tab, from the tabs' contents for this world's enabled features. */
	private static boolean buildTabs() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return false;
		CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
		Map<Item, String> m = new HashMap<>();
		for (Map.Entry<ResourceKey<CreativeModeTab>, String> e : TABS.entrySet()) {
			CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(e.getKey());
			if (tab == null) continue;
			for (ItemStack s : tab.getDisplayItems()) m.putIfAbsent(s.getItem(), e.getValue());
		}
		tabOf = m;
		return true;
	}
}
