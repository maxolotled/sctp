package com.snailtools.shoplogger;

import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

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
}
