package com.snailtools.shoplogger;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Silently opens known shop containers as the player walks near them, reads
 * their contents via the mixin-intercepted packet flow (no GUI ever shown),
 * and closes them again. Only runs with the mixin installed — see
 * ClientPlayNetworkHandlerMixin.
 *
 * Rate-limited on purpose: hammering every container every tick is both
 * unnecessary (shop stock doesn't change that fast) and needlessly heavy on
 * the server. Tune the constants below to taste.
 */
public class ShopAutoScanner implements SilentScreenCoordinator.Listener {

	private static final ShopAutoScanner INSTANCE = new ShopAutoScanner();
	public static ShopAutoScanner getInstance() { return INSTANCE; }

	/** How close the player must be before we'll silently open a shop. */
	private static final double INTERACT_RANGE = 4.5;
	/** Minimum time between re-scanning the *same* container. Configurable — see getPerShopCooldownMs(). */
	private static final int DEFAULT_PER_SHOP_COOLDOWN_MINUTES = 5;
	private static final String CONFIG_COOLDOWN_MINUTES = "scanning/cooldownMinutes";
	/** Minimum time between any two silent opens, regardless of container. */
	private static final long GLOBAL_COOLDOWN_MS = 750L;
	/** How often (in ticks) we rescan nearby chunks for new shop signs. */
	private static final int DISCOVERY_INTERVAL_TICKS = 100;
	/** Chunk radius around the player to scan for shop signs. */
	private static final int DISCOVERY_CHUNK_RADIUS = 4;

	private final Map<BlockPos, ShopSign> knownShops = new HashMap<>();
	private final Map<BlockPos, Long> lastScanned = new HashMap<>();
	/**
	 * Consecutive failed-validation count per position, used by
	 * forgetGoneShops() below. A shop's chest chunk can be loaded while the
	 * sign sitting on its front face — a different block, possibly in a
	 * neighboring chunk — hasn't finished loading yet, making SignFinder.find()
	 * transiently return null for a shop that's completely fine. Requiring two
	 * consecutive failures (5+ seconds apart) before actually removing it gives
	 * that race far more time than it needs to resolve, without meaningfully
	 * delaying detection of shops that are genuinely gone.
	 */
	private final Map<BlockPos, Integer> invalidStreak = new HashMap<>();
	private static final int REMOVE_AFTER_CONSECUTIVE_FAILURES = 2;

	private boolean enabled = true;
	private boolean armed = false;
	private BlockPos armedContainerPos = null;
	private ShopSign armedSign = null;
	private int armedSyncId = -1;
	private long lastOpenAttempt = 0L;
	private int tickCounter = 0;

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isArmed() {
		return armed;
	}

	/**
	 * True while we're inside our own call to interactBlock() for a silent
	 * open. UseBlockCallback fires for that call exactly like it would for a
	 * real player right-click, so callers must check this and ignore it —
	 * otherwise a silent open would immediately be treated as "the player
	 * manually clicked this container" and yield to itself.
	 */
	private boolean selfInteracting = false;

	public boolean isSelfInteracting() {
		return selfInteracting;
	}

	/**
	 * Call the moment a genuine manual container interaction is detected.
	 * Manual clicking always takes priority over a silent background scan:
	 * this releases whatever silent open is currently in flight (ours or
	 * anyone else's, via the shared coordinator) so the player's own screen
	 * isn't suppressed, and briefly holds off starting a new silent open so
	 * we don't immediately race back in ahead of it. Must NOT be called for
	 * our own silent interactBlock() calls — see isSelfInteracting().
	 */
	public void onManualContainerInteract() {
		SilentScreenCoordinator.yieldToManualOpen();
		lastOpenAttempt = System.currentTimeMillis();
	}

	/** Records that a container was just scanned — called by both the silent and manual scan paths. */
	public void markScanned(BlockPos pos) {
		lastScanned.put(pos.toImmutable(), System.currentTimeMillis());
	}

	/** How long a container counts as "recently scanned" — configurable via the settings screen, defaults to 5 minutes. */
	public static long getPerShopCooldownMs() {
		int minutes = com.snailtools.shoplogger.config.Config.getOrCreate(CONFIG_COOLDOWN_MINUTES, Integer.class, DEFAULT_PER_SHOP_COOLDOWN_MINUTES);
		return minutes * 60 * 1000L;
	}

	public static void setPerShopCooldownMinutes(int minutes) {
		com.snailtools.shoplogger.config.Config.update(CONFIG_COOLDOWN_MINUTES, minutes);
	}

	/** The known ShopSign for a container position, if any — used to anchor the scan-marker particle to the sign instead of the container. */
	public ShopSign getKnownSign(BlockPos containerPos) {
		return knownShops.get(containerPos);
	}

	/** Positions still within the per-shop cooldown window, i.e. "freshly done, no need to recheck yet." */
	public Set<BlockPos> getRecentlyScannedPositions() {
		long now = System.currentTimeMillis();
		long cooldownMs = getPerShopCooldownMs();
		Set<BlockPos> recent = new HashSet<>();
		for (Map.Entry<BlockPos, Long> e : lastScanned.entrySet()) {
			if (now - e.getValue() < cooldownMs) {
				recent.add(e.getKey());
			}
		}
		return recent;
	}

	// ---- called every client tick ----

	public void tick(MinecraftClient client) {
		if (!enabled || client.world == null || client.player == null) return;
		if (!ShopDimension.isActive(client)) return;
		if (!WorldSelection.ensureSet(client)) return;

		tickCounter++;
		if (tickCounter % DISCOVERY_INTERVAL_TICKS == 0) {
			discoverNearbyShops(client);
		}

		if (armed) return; // waiting on a silent open to finish
		if (client.player.currentScreenHandler != client.player.playerScreenHandler) return; // player has a container open themselves — don't compete for it
		long now = System.currentTimeMillis();
		if (now - lastOpenAttempt < GLOBAL_COOLDOWN_MS) return;

		findNextTarget(client).ifPresent(target -> {
			openSilently(client, target.pos, target.sign);
		});
	}

	private record Target(BlockPos pos, ShopSign sign) {}

	private Optional<Target> findNextTarget(MinecraftClient client) {
		Vec3d eye = client.player.getEyePos();
		long now = System.currentTimeMillis();

		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for (Map.Entry<BlockPos, ShopSign> e : knownShops.entrySet()) {
			BlockPos pos = e.getKey();
			Long last = lastScanned.get(pos);
			if (last != null && now - last < getPerShopCooldownMs()) continue;

			double dist = Math.sqrt(pos.getSquaredDistance(eye));
			if (dist > INTERACT_RANGE) continue;

			if (dist < bestDist) {
				bestDist = dist;
				best = pos;
			}
		}

		if (best == null) return Optional.empty();
		return Optional.of(new Target(best, knownShops.get(best)));
	}

	private void openSilently(MinecraftClient client, BlockPos containerPos, ShopSign sign) {
		if (client.interactionManager == null || client.player == null) return;
		if (!SilentScreenCoordinator.arm(this)) return; // something else is mid-silent-open; try again later

		Vec3d center = Vec3d.ofCenter(containerPos);
		BlockHitResult hit = new BlockHitResult(center, Direction.UP, containerPos, false);

		armed = true;
		armedContainerPos = containerPos;
		armedSign = sign;
		armedSyncId = -1;
		lastOpenAttempt = System.currentTimeMillis();

		selfInteracting = true;
		try {
			client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND, hit);
		} finally {
			selfInteracting = false;
		}
	}

	// ---- SilentScreenCoordinator.Listener ----

	@Override
	public void onScreenSuppressed(ScreenHandler handler) {
		if (!armed) return;
		armedSyncId = handler.syncId;
	}

	@Override
	public void onInventorySynced(int syncId, ClientPlayNetworkHandler netHandler) {
		if (!armed || syncId != armedSyncId || armedSyncId == -1) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			disarm();
			return;
		}

		ScreenHandler handler = client.player.currentScreenHandler;
		if (handler != null && handler.syncId == syncId) {
			List<ShopEntry> entries = ShopEntryFactory.build(handler, armedSign, armedContainerPos);
			ShopWorld world = WorldSelection.get();
			if (world != null) {
				ShopLog.replaceForPosition(world.label(), armedContainerPos, entries);
			}
			ScanChatLogger.maybePrint(client, entries);
			OwnShopSaleTracker.check(client, armedSign, armedContainerPos, entries);
			if (world != null) ShopVisitAlert.maybeAlert(client, world.label(), armedSign.seller());
			WatchlistAlert.maybeAlert(client, entries);
		}

		markScanned(armedContainerPos);

		// Tell the server we're done, and restore the player's own inventory
		// screen handler, matching what a normal close does.
		netHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
		client.player.currentScreenHandler = client.player.playerScreenHandler;

		disarm();
	}

	@Override
	public void onWatchdogTimeout() {
		disarm();
	}

	private void disarm() {
		armed = false;
		armedContainerPos = null;
		armedSign = null;
		armedSyncId = -1;
		SilentScreenCoordinator.disarm(this);
	}

	// ---- shop discovery ----

	private void discoverNearbyShops(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;

		ChunkPos center = client.player.getChunkPos();
		Set<ChunkPos> scannedChunks = new HashSet<>();

		for (int dx = -DISCOVERY_CHUNK_RADIUS; dx <= DISCOVERY_CHUNK_RADIUS; dx++) {
			for (int dz = -DISCOVERY_CHUNK_RADIUS; dz <= DISCOVERY_CHUNK_RADIUS; dz++) {
				ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
				// world.getChunk(x, z) never actually returns null on the client — an
				// unloaded chunk silently gets a shared placeholder "empty chunk" with
				// no block entities instead. Without this check, that placeholder gets
				// treated as "confirmed empty," and forgetGoneShops below wipes any
				// known shop inside it even though the chest is still there and just
				// hasn't finished (re)loading yet.
				if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) continue;
				WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
				if (chunk == null) continue;
				scannedChunks.add(chunkPos);

				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = e.getValue();
					if (!ShopContainers.isShopContainer(be)) continue;

					BlockPos pos = e.getKey();
					if (knownShops.containsKey(pos)) continue;

					BlockState state = world.getBlockState(pos);
					ShopSign sign = SignFinder.find(world, pos, state);
					if (sign != null) {
						knownShops.put(pos.toImmutable(), sign);
					}
				}
			}
		}

		forgetGoneShops(world, scannedChunks);
	}

	/**
	 * Forgets any known shop within the just-scanned chunks that no longer has
	 * a valid chest/barrel + matching sign (block broken, sign removed/edited),
	 * and records an empty scan for its position so the server prunes whatever
	 * was previously logged there.
	 */
	private void forgetGoneShops(ClientWorld world, Set<ChunkPos> scannedChunks) {
		if (scannedChunks.isEmpty()) return;
		ShopWorld shopWorld = WorldSelection.get();

		Iterator<Map.Entry<BlockPos, ShopSign>> it = knownShops.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, ShopSign> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!scannedChunks.contains(new ChunkPos(pos))) continue;

			BlockEntity be = world.getBlockEntity(pos);
			BlockState state = world.getBlockState(pos);
			boolean stillValid = ShopContainers.isShopContainer(be)
					&& SignFinder.find(world, pos, state) != null;

			if (stillValid) {
				invalidStreak.remove(pos);
				continue;
			}

			int streak = invalidStreak.merge(pos, 1, Integer::sum);
			if (streak < REMOVE_AFTER_CONSECUTIVE_FAILURES) continue;

			it.remove();
			lastScanned.remove(pos);
			invalidStreak.remove(pos);
			if (shopWorld != null) {
				ShopLog.replaceForPosition(shopWorld.label(), pos, List.of());
			}
		}
	}

	public int knownShopCount() {
		return knownShops.size();
	}
}
