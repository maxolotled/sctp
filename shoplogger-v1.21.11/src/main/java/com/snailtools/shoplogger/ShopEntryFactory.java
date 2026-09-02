package com.snailtools.shoplogger;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Tallies item stacks in a container into one ShopEntry per distinct item type. */
public final class ShopEntryFactory {

	private ShopEntryFactory() {}

	/** A shulker box always has 27 inventory slots, regardless of what's in it. */
	private static final int SHULKER_SLOTS = 27;

	// batchSize = how many of this item the sign's price actually buys.
	// Normal/bundled listings: the largest single-slot quantity seen (the shop
	// stocks per slot, not necessarily a full stack — see ShopSign). Bulk
	// (shulker) listings are priced per WHOLE shulker, not per slot inside
	// it — see addTally's SHULKER_SLOTS use — so batchSize there is a fixed
	// 27 * (this item's own max stack size), e.g. 1728 for a 64-stackable
	// item, regardless of how full the shulker actually is right now.
	private record Tally(ItemStack representative, int count, int batchSize, boolean bulk, boolean bundled) {}

	public static List<ShopEntry> build(ScreenHandler handler, ShopSign sign, BlockPos containerPos) {
		if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
			return List.of();
		}
		if (!sign.display() && sign.signPrice() == 0) {
			return List.of(); // price-0 signs aren't real sales — don't log their contents
		}
		if (!ShopDimension.isActive(MinecraftClient.getInstance())) {
			return List.of(); // not the shop dimension — e.g. a lookalike sign+chest in the overworld
		}

		ShopWorld world = WorldSelection.get();
		if (world == null) {
			return List.of(); // callers should already gate on WorldSelection.ensureSet(...) before reaching here
		}

		int invSize = containerHandler.getInventory().size();

		// key = baseId + "|" + displayName
		Map<String, Tally> tallies = new LinkedHashMap<>();

		for (int i = 0; i < invSize && i < handler.slots.size(); i++) {
			ItemStack stack = handler.getSlot(i).getStack();
			if (stack == null || stack.isEmpty()) continue;

			List<ItemStack> shulkerContents = readShulkerContents(stack);
			List<ItemStack> bundleContents = shulkerContents == null ? readBundleContents(stack) : null;

			if (shulkerContents != null && !shulkerContents.isEmpty() && isSingleItemType(shulkerContents)) {
				// Bulk item: the whole shulker is one item type — register its
				// contents instead of the shulker itself.
				for (ItemStack inner : shulkerContents) {
					if (inner == null || inner.isEmpty()) continue;
					addTally(tallies, inner, inner.getCount(), true, false, sign.currency());
				}
			} else if (bundleContents != null && !bundleContents.isEmpty() && isSingleItemType(bundleContents)) {
				// Same idea as a shulker, but marked "bundled" instead of "bulk" so
				// the site can tell the two apart.
				for (ItemStack inner : bundleContents) {
					if (inner == null || inner.isEmpty()) continue;
					addTally(tallies, inner, inner.getCount(), false, true, sign.currency());
				}
			} else {
				// Empty/mixed-contents shulker or bundle (e.g. a curated bundle like
				// a "Lunar New Year Box") — list the container itself rather than
				// decomposing it. "Bulk"/"bundled" specifically mean "entirely one
				// item type."
				addTally(tallies, stack, stack.getCount(), false, false, sign.currency());
			}
		}

		long now = System.currentTimeMillis();
		List<ShopEntry> out = new ArrayList<>();
		for (Tally t : tallies.values()) {
			ItemStack rep = t.representative();

			// The sign's price is exactly what's on the sign, per t.batchSize()
			// items — however many the shop actually stocks per slot. We don't
			// scale it to a full stack; batchSize is just shown alongside it so
			// buyers know what the price buys (e.g. "1 diamond" / "per 32").
			int batchSize = t.batchSize();
			double stacksInStock = Math.round((t.count() / (double) batchSize) * 100.0) / 100.0;

			out.add(new ShopEntry(
					displayNameFor(rep),
					Registries.ITEM.getId(rep.getItem()).toString(),
					t.count(),
					sign.signPrice(),
					batchSize,
					stacksInStock,
					t.bulk(),
					t.bundled(),
					sign.currency(),
					sign.seller(),
					world.label(),
					containerPos,
					now
			));
		}
		return out;
	}

	// Currency string (from a shop sign's line 3) -> the item id it actually
	// pays with. Used to make sure a shop never lists its own payment item as
	// something for sale (e.g. an "ironingot" shop that also happens to have
	// loose iron ingots in the chest shouldn't show "1 Iron Ingot for 1 Iron
	// Ingot"). Extend as new real-world currencies show up.
	private static final Map<String, String> CURRENCY_ITEM_IDS = Map.ofEntries(
			Map.entry("diamond", "minecraft:diamond"),
			Map.entry("diamondblock", "minecraft:diamond_block"),
			Map.entry("iron", "minecraft:iron_ingot"),
			Map.entry("ironingot", "minecraft:iron_ingot"),
			Map.entry("ironblock", "minecraft:iron_block"),
			Map.entry("gold", "minecraft:gold_ingot"),
			Map.entry("goldingot", "minecraft:gold_ingot"),
			Map.entry("goldblock", "minecraft:gold_block"),
			Map.entry("emerald", "minecraft:emerald"),
			Map.entry("emeraldblock", "minecraft:emerald_block"),
			Map.entry("netherite", "minecraft:netherite_ingot"),
			Map.entry("netheriteingot", "minecraft:netherite_ingot"),
			Map.entry("netheriteblock", "minecraft:netherite_block"),
			Map.entry("coal", "minecraft:coal")
	);

	private static boolean isPaymentItem(String baseId, String currency) {
		String mapped = CURRENCY_ITEM_IDS.get(String.valueOf(currency).toLowerCase());
		return mapped != null && mapped.equals(baseId);
	}

	private static void addTally(Map<String, Tally> tallies, ItemStack stack, int amount, boolean bulk, boolean bundled, String currency) {
		String baseId = Registries.ITEM.getId(stack.getItem()).toString();
		if (isPaymentItem(baseId, currency)) return; // never list the item this shop is literally being paid in
		String displayName = displayNameFor(stack);
		// bulk/bundled are part of the key so a normal-priced stack and a
		// bulk/bundled stack of the SAME item in the same container don't
		// collapse into one tally, losing the fact both forms are sold.
		String key = baseId + "|" + displayName + "|" + bulk + "|" + bundled;

		// A bulk listing is priced per whole shulker (27 slots), not per slot
		// inside it — using the observed per-slot amount here (as bundled/normal
		// listings do) would understate a bulk batch by ~27x, which is exactly
		// the "1db/64" vs the real "1db/shulk (=1db/1728)" mismatch this fixes.
		int slotBatchSize = bulk ? SHULKER_SLOTS * stack.getMaxCount() : amount;

		Tally existing = tallies.get(key);
		if (existing == null) {
			tallies.put(key, new Tally(stack, amount, slotBatchSize, bulk, bundled));
		} else {
			// Once bulk/bundled, stays that way if any contributing stack came
			// from a shulker/bundle. batchSize takes the largest batch seen —
			// a smaller one (a partially-sold leftover slot) shouldn't shrink an
			// already-established real batch size.
			tallies.put(key, new Tally(
					existing.representative(),
					existing.count() + amount,
					Math.max(existing.batchSize(), slotBatchSize),
					existing.bulk() || bulk,
					existing.bundled() || bundled));
		}
	}

	/**
	 * An enchanted book's own display name is just "Enchanted Book" — the
	 * enchantment only shows up as a separate tooltip line, not in getName().
	 * Use the enchantment(s) it actually holds instead, e.g. "Sharpness V", so
	 * different enchanted books don't all collapse into one listing and
	 * buyers can tell what they're actually buying without opening the shop.
	 */
	private static String displayNameFor(ItemStack stack) {
		if (stack.getItem() == Items.ENCHANTED_BOOK) {
			ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
			if (enchantments != null && !enchantments.isEmpty()) {
				return enchantments.getEnchantmentEntries().stream()
						.map(e -> Enchantment.getName(e.getKey(), e.getIntValue()).getString())
						.sorted()
						.collect(Collectors.joining(", "));
			}
		}
		return stack.getName().getString();
	}

	/** True if every non-empty stack inside the shulker is the same item. */
	private static boolean isSingleItemType(List<ItemStack> contents) {
		var firstItem = contents.get(0).getItem();
		for (ItemStack s : contents) {
			if (s.getItem() != firstItem) return false;
		}
		return true;
	}

	/** Returns the item stacks inside a shulker box, or null if this isn't a (non-empty) shulker box. */
	private static List<ItemStack> readShulkerContents(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
		if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return null;

		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container == null) return null;

		return container.streamNonEmpty().toList();
	}

	/** Returns the item stacks inside a bundle, or null if this isn't a (non-empty) bundle. */
	private static List<ItemStack> readBundleContents(ItemStack stack) {
		if (!(stack.getItem() instanceof BundleItem)) return null;

		BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (contents == null || contents.isEmpty()) return null;

		List<ItemStack> list = new ArrayList<>();
		for (ItemStack s : contents.iterateCopy()) list.add(s);
		return list;
	}
}
