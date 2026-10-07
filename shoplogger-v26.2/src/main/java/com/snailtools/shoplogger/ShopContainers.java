package com.snailtools.shoplogger;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Single source of truth for "is this block entity a shop container." Shop
 * containers are chests and barrels ONLY — shulker boxes (placed as blocks)
 * and ender chests must never be treated as one, even if scanning logic
 * changes later. This does not affect reading a shulker box's contents when
 * it's sitting as an ItemStack inside a valid shop chest/barrel's slots —
 * see ShopEntryFactory, which is unrelated to this check.
 */
public final class ShopContainers {

	private ShopContainers() {}

	public static boolean isShopContainer(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
	}

	/**
	 * Double chests are real shops too (since 2.4): one shop for both halves,
	 * known by its left half (see SignFinder.shopPos) and priced by the left
	 * half's sign. They open as a 6-row screen instead of 3 — see
	 * ShopAutoScanner.accepts().
	 */
	public static boolean isDoubleChest(BlockState state) {
		return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
	}
}
