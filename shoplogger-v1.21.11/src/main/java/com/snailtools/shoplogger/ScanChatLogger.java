package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * When enabled via its hotkey, echoes every item found during a chest scan to
 * chat. Two formats, chosen by the "chatLog/singleLine" setting:
 *
 * Multi-line (default) — grouped by price and then by stack size:
 * PRICE: 2 db
 * STACKED: 64
 * cobblestone, stone, dirt, gravel
 * STACKED: 25
 * seeds, paper, sugarcane
 *
 * Single-line — one consolidated message for the whole scan:
 * Scanned 4 items: cobblestone (2 db/64), stone (2 db/64), dirt (2 db/64), gravel (2 db/64)
 */
public final class ScanChatLogger {

	private static final String DIAMOND = "minecraft:diamond";
	private static final String DIAMOND_BLOCK = "minecraft:diamond_block";
	private static final String CONFIG_SINGLE_LINE = "chatLog/singleLine";

	private static boolean enabled = false;

	private ScanChatLogger() {}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isSingleLine() {
		return Config.getOrCreate(CONFIG_SINGLE_LINE, Boolean.class, false);
	}

	public static void setSingleLine(boolean value) {
		Config.update(CONFIG_SINGLE_LINE, value);
	}

	/** Call right after a container scan with whatever entries were just found (may be empty). */
	public static void maybePrint(MinecraftClient client, List<ShopEntry> entries) {
		if (!enabled || client.player == null || entries.isEmpty()) return;

		List<ShopEntry> merchandise = new ArrayList<>();
		for (ShopEntry e : entries) {
			// Diamonds/diamond blocks are the currency, not merchandise — never echo them here.
			if (DIAMOND.equals(e.baseItem()) || DIAMOND_BLOCK.equals(e.baseItem())) continue;
			merchandise.add(e);
		}
		if (merchandise.isEmpty()) return;

		if (isSingleLine()) {
			printSingleLine(client, merchandise);
		} else {
			printMultiLine(client, merchandise);
		}
	}

	/** "shulk" for a bulk (shulker) listing — its batchSize is a fixed 27-slots'-worth, not a plain stack count — the raw number otherwise. */
	private static String batchLabel(ShopEntry e) {
		return e.bulk() ? "shulk" : String.valueOf(e.stackSize());
	}

	private static void printSingleLine(MinecraftClient client, List<ShopEntry> entries) {
		List<String> parts = new ArrayList<>();
		for (ShopEntry e : entries) {
			parts.add(e.itemName() + " (" + e.priceLabel() + "/" + batchLabel(e) + ")");
		}
		ChatFormat.sendPlain(client, ChatFormat.ITEMS,
				"Scanned " + entries.size() + " item" + (entries.size() == 1 ? "" : "s") + ": " + String.join(", ", parts));
	}

	private static void printMultiLine(MinecraftClient client, List<ShopEntry> entries) {
		// priceLabel -> batch label ("shulk" or a raw stack size) -> item names, in first-seen order.
		Map<String, Map<String, List<String>>> byPrice = new LinkedHashMap<>();
		for (ShopEntry e : entries) {
			byPrice.computeIfAbsent(e.priceLabel(), k -> new LinkedHashMap<>())
					.computeIfAbsent(batchLabel(e), k -> new ArrayList<>())
					.add(e.itemName());
		}

		for (Map.Entry<String, Map<String, List<String>>> priceGroup : byPrice.entrySet()) {
			ChatFormat.sendPlain(client, ChatFormat.PRICE, "PRICE: " + priceGroup.getKey());
			for (Map.Entry<String, List<String>> stackGroup : priceGroup.getValue().entrySet()) {
				ChatFormat.sendPlain(client, ChatFormat.STACK, "STACKED: " + stackGroup.getKey());
				ChatFormat.sendPlain(client, ChatFormat.ITEMS, String.join(", ", stackGroup.getValue()));
			}
		}
	}
}
