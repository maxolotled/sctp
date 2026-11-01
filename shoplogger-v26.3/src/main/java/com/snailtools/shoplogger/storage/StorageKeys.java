package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WorldSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * X+M. Looking at a storage container: adds it to My storage (or, if it's
 * already in, takes it out). Looking at anything else: adds every storage
 * container within 16 blocks. Each new one is scanned once the first time
 * you're within reach.
 */
public final class StorageKeys {

	private StorageKeys() {}

	private static final double AREA = 16.0;

	private static net.minecraft.client.KeyMapping storageKey;
	private static boolean chordUsed;

	/** Called by each loader once its key mapping exists. */
	public static void setKey(net.minecraft.client.KeyMapping key) { storageKey = key; }

	/**
	 * Every key press (KeyboardHandlerMixin). X held + M (or whatever it's bound to) in
	 * the world: runs X+M and swallows the M, so another mod using M too (Xaero's world
	 * map, for one) neither steals it nor opens on top. M on its own is left alone.
	 */
	public static boolean onKey(int action, net.minecraft.client.input.KeyEvent event) {
		if (storageKey == null || action != 1 || !storageKey.matches(event)) return false;
		if (!com.snailtools.shoplogger.gui.HeldItemPage.libraryHeld()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null) return false;
		chordUsed = true;
		onAddKey(mc);
		return true;
	}

	/** True once after X+M was used, so letting go of X doesn't also open the menu. */
	public static boolean consumeChord() {
		boolean c = chordUsed;
		chordUsed = false;
		return c;
	}

	public static void onAddKey(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		if (!StorageSettings.isEnabled()) {
			ChatFormat.send(mc, ChatFormat.INFO, "My storage is turned off (Settings › My storage).");
			return;
		}
		WorldSelection.ensureSet(mc);
		if (!StorageIndex.ready()) {
			ChatFormat.send(mc, ChatFormat.ERROR, "Couldn't tell which world you're on yet. Try again in a moment, or use /setworld.");
			return;
		}
		ClientLevel level = mc.level;
		String dim = StorageIndex.dimension();

		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			BlockEntity be = level.getBlockEntity(pos);
			if (StorageContainers.isEnderChest(be)) {
				ChatFormat.send(mc, ChatFormat.INFO, "Your ender chest is part of My storage already: it's updated every time you open it.");
				return;
			}
			if (StorageContainers.isContainer(be)) {
				if (!StorageContainers.isStorage(level, pos)) {
					ChatFormat.send(mc, ChatFormat.INFO, "That's a shop chest. Shops aren't part of your storage.");
					return;
				}
				BlockPos at = StorageContainers.canonical(level, pos);
				StorageIndex.Box existing = StorageIndex.find(dim, at);
				if (existing != null) {
					StorageIndex.remove(existing);
					ChatFormat.send(mc, ChatFormat.SUCCESS, "Took " + existing.label() + " out of My storage.");
					return;
				}
				StorageIndex.Box b = StorageIndex.add(dim, at, StorageContainers.kind(be));
				ChatFormat.send(mc, ChatFormat.SUCCESS, "Added " + b.label() + " to My storage. It's read the first time you're within reach.");
				return;
			}
		}

		// anything else: every storage container around you
		Vec3 eye = mc.player.getEyePosition();
		Set<BlockPos> found = new LinkedHashSet<>();
		ChunkPos c = mc.player.chunkPosition();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (!level.hasChunk(c.x() + dx, c.z() + dz)) continue;
			LevelChunk chunk = level.getChunk(c.x() + dx, c.z() + dz);
			for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
				if (!StorageContainers.isContainer(e.getValue())) continue;
				if (Vec3.atCenterOf(e.getKey()).distanceTo(eye) > AREA) continue;
				if (!StorageContainers.isStorage(level, e.getKey())) continue;
				found.add(StorageContainers.canonical(level, e.getKey()));
			}
		}
		int added = 0, already = 0;
		for (BlockPos p : found) {
			if (StorageIndex.find(dim, p) != null) { already++; continue; }
			StorageIndex.add(dim, p, StorageContainers.kind(level.getBlockEntity(p)));
			added++;
		}
		if (found.isEmpty()) ChatFormat.send(mc, ChatFormat.INFO, "No storage containers within 16 blocks. Look at one and press X+M to add just that one.");
		else if (added == 0) ChatFormat.send(mc, ChatFormat.INFO, "All " + already + " containers within 16 blocks are in My storage already.");
		else ChatFormat.send(mc, ChatFormat.SUCCESS, "Added " + added + " container" + (added == 1 ? "" : "s") + " within 16 blocks to My storage"
				+ (already > 0 ? " (" + already + " already in)" : "") + ". Walk past them and they're read one by one.");
	}
}
