package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.ShopContainers;
import com.snailtools.shoplogger.SignFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What counts as storage: chests (trapped and double too), barrels and placed
 * shulker boxes, never a shop chest (one with a shop sign). The ender chest is
 * its own thing: tracked whenever it's opened, wherever.
 */
public final class StorageContainers {

	private StorageContainers() {}

	public static boolean isContainer(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
	}

	public static boolean isEnderChest(BlockEntity be) {
		return be instanceof EnderChestBlockEntity;
	}

	/** A storage container that isn't a shop. */
	public static boolean isStorage(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!isContainer(be)) return false;
		BlockState state = level.getBlockState(pos);
		return !(ShopContainers.isShopContainer(be) && SignFinder.find(level, pos, state) != null);
	}

	/** The position a container is known by: a double chest by its left half, like shops. */
	public static BlockPos canonical(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ChestBlockEntity) return SignFinder.shopPos(level, pos, level.getBlockState(pos)).immutable();
		return pos.immutable();
	}

	public static String kind(BlockEntity be) {
		if (be instanceof BarrelBlockEntity) return "barrel";
		if (be instanceof ShulkerBoxBlockEntity) return "shulker";
		return "chest";
	}

	/**
	 * The kind of container the block itself is ("chest", "barrel", "shulker"), or
	 * null for any other block. Unlike kind(BlockEntity) this needs no block entity,
	 * which can show up a moment after the block when a chunk loads.
	 */
	public static String kindOfBlock(BlockState state) {
		net.minecraft.world.level.block.Block b = state.getBlock();
		if (b instanceof net.minecraft.world.level.block.ChestBlock) return "chest";
		if (b instanceof net.minecraft.world.level.block.BarrelBlock) return "barrel";
		if (b instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return "shulker";
		return null;
	}

	/** Rows its screen has: 6 for a double chest, else 3 (a shulker box opens its own screen). */
	public static int rows(Level level, BlockPos pos) {
		return ShopContainers.isDoubleChest(level.getBlockState(pos)) ? 6 : 3;
	}
}
