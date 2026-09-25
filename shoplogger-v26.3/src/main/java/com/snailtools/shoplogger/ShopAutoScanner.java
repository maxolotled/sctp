package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Silently opens known shop containers as the player walks near them, reads
 * their contents via the mixin-intercepted packet flow (no GUI ever shown),
 * and closes them again. Only runs with the mixin installed — see
 * ClientPlayNetworkHandlerMixin.
 *
 * Rate-limited on purpose: hammering every container every tick is both
 * unnecessary (shop stock doesn't change that fast) and needlessly heavy on
 * the server. The wait between silent opens adapts to how long recent ones
 * actually took to resolve (see getAdaptiveCooldownMs()) instead of one fixed
 * guess — snappy on a good connection, but it stretches out during lag so a
 * slow-to-resolve manual open elsewhere can't get raced by a fresh silent
 * open before its own response arrives.
 */
public class ShopAutoScanner implements SilentScreenCoordinator.Listener {

	private static final ShopAutoScanner INSTANCE = new ShopAutoScanner();
	public static ShopAutoScanner getInstance() { return INSTANCE; }

	/** How close the player must be before we'll silently open a shop. */
	private static final double INTERACT_RANGE = 4.5;
	/** Minimum time between re-scanning the *same* container. Configurable — see getPerShopCooldownMs(). */
	private static final int DEFAULT_PER_SHOP_COOLDOWN_MINUTES = 5;
	private static final String CONFIG_COOLDOWN_MINUTES = "scanning/cooldownMinutes";
	/** How often (in ticks) we rescan nearby chunks for new shop signs. */
	private static final int DISCOVERY_INTERVAL_TICKS = 100;
	/** Chunk radius around the player to scan for shop signs. */
	private static final int DISCOVERY_CHUNK_RADIUS = 4;

	// ---- adaptive global cooldown (minimum time between any two silent
	// opens, regardless of container) — replaces one fixed guess with a
	// number sized from how long recent silent opens actually took to go
	// from "sent" to "resolved", see recordRoundTrip()/getAdaptiveCooldownMs() ----
	private static final long DEFAULT_COOLDOWN_MS = 750L; // used until enough real samples exist
	private static final long MIN_COOLDOWN_MS = 400L;
	private static final long MAX_COOLDOWN_MS = 3000L;
	private static final int COOLDOWN_SAFETY_MULTIPLIER = 3; // headroom over the worst recent round trip
	private static final int ROUND_TRIP_SAMPLES = 8;
	private final long[] roundTripSamplesMs = new long[ROUND_TRIP_SAMPLES];
	private int roundTripSampleCount = 0;
	private int roundTripSampleIndex = 0;

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
	 * True while we're inside our own call to useItemOn() for a silent open.
	 * UseBlockCallback fires for that call exactly like it would for a real
	 * player right-click, so callers must check this and ignore it —
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
	 * our own silent useItemOn() calls — see isSelfInteracting().
	 */
	public void onManualContainerInteract() {
		SilentScreenCoordinator.yieldToManualOpen();
		lastOpenAttempt = System.currentTimeMillis();
	}

	/** Records that a container was just scanned — called by both the silent and manual scan paths. */
	public void markScanned(BlockPos pos) {
		lastScanned.put(pos.immutable(), System.currentTimeMillis());
	}

	/** How long a container counts as "recently scanned" — configurable via the settings screen, defaults to 5 minutes. */
	public static long getPerShopCooldownMs() {
		int minutes = com.snailtools.shoplogger.config.Config.getOrCreate(CONFIG_COOLDOWN_MINUTES, Integer.class, DEFAULT_PER_SHOP_COOLDOWN_MINUTES);
		return minutes * 60 * 1000L;
	}

	public static void setPerShopCooldownMinutes(int minutes) {
		com.snailtools.shoplogger.config.Config.update(CONFIG_COOLDOWN_MINUTES, minutes);
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

	/** The known ShopSign for a container position, if any — used to anchor the scan-marker particle to the sign instead of the container. */
	public ShopSign getKnownSign(BlockPos containerPos) {
		return knownShops.get(containerPos);
	}

	/** Records how long a silent open actually took to resolve — feeds getAdaptiveCooldownMs(). */
	private void recordRoundTrip(long ms) {
		roundTripSamplesMs[roundTripSampleIndex] = ms;
		roundTripSampleIndex = (roundTripSampleIndex + 1) % ROUND_TRIP_SAMPLES;
		if (roundTripSampleCount < ROUND_TRIP_SAMPLES) roundTripSampleCount++;
	}

	/**
	 * How long to wait between silent opens right now — DEFAULT_COOLDOWN_MS
	 * until there's real data (just joined, or nothing nearby to scan in a
	 * while), after that COOLDOWN_SAFETY_MULTIPLIER times the slowest of the
	 * last ROUND_TRIP_SAMPLES silent opens, clamped between MIN_COOLDOWN_MS
	 * and MAX_COOLDOWN_MS so one freak spike can't stall scanning for too
	 * long, and a great connection can't shrink it away to nothing either.
	 */
	private long getAdaptiveCooldownMs() {
		if (roundTripSampleCount == 0) return DEFAULT_COOLDOWN_MS;
		long worst = 0;
		for (int i = 0; i < roundTripSampleCount; i++) worst = Math.max(worst, roundTripSamplesMs[i]);
		return Math.max(MIN_COOLDOWN_MS, Math.min(MAX_COOLDOWN_MS, worst * COOLDOWN_SAFETY_MULTIPLIER));
	}

	/** TEMPORARY (see CHANGELOG 2.2) — exposes the adaptive cooldown for TempScanWaitOverlay. */
	public long getCurrentWaitMs() {
		return getAdaptiveCooldownMs();
	}

	// ---- called every client tick ----

	public void tick(Minecraft client) {
		if (!enabled || client.level == null || client.player == null) return;
		if (!ShopDimension.isActive(client)) return;
		if (!WorldSelection.ensureSet(client)) return;

		tickCounter++;
		if (tickCounter % DISCOVERY_INTERVAL_TICKS == 0) {
			discoverNearbyShops(client);
		}

		if (armed) return; // waiting on a silent open to finish
		if (client.player.containerMenu != client.player.inventoryMenu) return; // player has a container open themselves — don't compete for it
		if (isHoldingPausingItem(client)) return; // see isHoldingPausingItem() — don't silently right-click while holding one of these
		long now = System.currentTimeMillis();
		if (now - lastOpenAttempt < getAdaptiveCooldownMs()) return;

		findNextTarget(client).ifPresent(target -> {
			openSilently(client, target.pos, target.sign);
		});
	}

	/**
	 * Silent scanning right-clicks a container using whatever's in the
	 * player's main hand (see openSilently()'s useItemOn call) — for a
	 * held item with its own special block-interaction behavior, that could
	 * silently fire on every shop container the player walks past, not just
	 * open it. Name tags and anything built on the vanilla feather item
	 * (Snailcraft's convention for several custom tools/wands) are the known
	 * cases; pausing while either is in hand avoids triggering them by
	 * accident. Scanning resumes on its own the moment the player switches
	 * away from holding one.
	 */
	private static boolean isHoldingPausingItem(Minecraft client) {
		ItemStack main = client.player.getMainHandItem();
		return main.getItem() == Items.NAME_TAG || main.getItem() == Items.FEATHER;
	}

	private record Target(BlockPos pos, ShopSign sign) {}

	private Optional<Target> findNextTarget(Minecraft client) {
		Vec3 eye = client.player.getEyePosition();
		long now = System.currentTimeMillis();

		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for (Map.Entry<BlockPos, ShopSign> e : knownShops.entrySet()) {
			BlockPos pos = e.getKey();
			Long last = lastScanned.get(pos);
			if (last != null && now - last < getPerShopCooldownMs()) continue;

			double dist = Math.sqrt(new Vec3(pos.getX(), pos.getY(), pos.getZ()).distanceToSqr(eye));
			if (dist > INTERACT_RANGE) continue;

			if (dist < bestDist) {
				bestDist = dist;
				best = pos;
			}
		}

		if (best == null) return Optional.empty();
		return Optional.of(new Target(best, knownShops.get(best)));
	}

	private void openSilently(Minecraft client, BlockPos containerPos, ShopSign sign) {
		if (client.gameMode == null || client.player == null) return;
		if (!SilentScreenCoordinator.arm(this)) return; // something else is mid-silent-open; try again later

		Vec3 center = Vec3.atCenterOf(containerPos);
		BlockHitResult hit = new BlockHitResult(center, Direction.UP, containerPos, false);

		armed = true;
		armedContainerPos = containerPos;
		armedSign = sign;
		armedSyncId = -1;
		lastOpenAttempt = System.currentTimeMillis();

		selfInteracting = true;
		try {
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
		} finally {
			selfInteracting = false;
		}
	}

	// ---- SilentScreenCoordinator.Listener ----

	@Override
	public void onScreenSuppressed(AbstractContainerMenu handler) {
		if (!armed) return;
		armedSyncId = handler.containerId;
	}

	@Override
	public void onInventorySynced(int syncId, ClientPacketListener netHandler) {
		if (!armed || syncId != armedSyncId || armedSyncId == -1) return;
		recordRoundTrip(System.currentTimeMillis() - lastOpenAttempt);

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			disarm();
			return;
		}

		AbstractContainerMenu handler = client.player.containerMenu;
		if (handler != null && handler.containerId == syncId) {
			List<ShopEntry> entries = ShopEntryFactory.build(handler, armedSign, armedContainerPos);
			ShopWorld world = WorldSelection.get();
			if (world != null) {
				ShopLog.replaceForPosition(world.label(), armedContainerPos, entries);
			}
			ScanChatLogger.maybePrint(client, entries);
			OwnShopSaleTracker.check(client, armedSign, armedContainerPos, handler, world != null ? world.label() : null);
			if (world != null) {
				ShopVisitAlert.maybeAlert(client, world.label(), armedSign.seller());
				ShopVisitAlert.maybeSendShopInfo(world.label(), armedSign.seller());
			}
			WatchlistAlert.maybeAlert(client, entries);
		}

		markScanned(armedContainerPos);

		// Tell the server we're done, and restore the player's own inventory
		// screen handler, matching what a normal close does.
		netHandler.send(new ServerboundContainerClosePacket(syncId));
		client.player.containerMenu = client.player.inventoryMenu;

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

	private void discoverNearbyShops(Minecraft client) {
		ClientLevel world = client.level;
		if (world == null || client.player == null) return;

		ChunkPos center = client.player.chunkPosition();
		Set<ChunkPos> scannedChunks = new HashSet<>();

		for (int dx = -DISCOVERY_CHUNK_RADIUS; dx <= DISCOVERY_CHUNK_RADIUS; dx++) {
			for (int dz = -DISCOVERY_CHUNK_RADIUS; dz <= DISCOVERY_CHUNK_RADIUS; dz++) {
				ChunkPos chunkPos = new ChunkPos(center.x() + dx, center.z() + dz);
				// world.getChunk(x, z) never actually returns null on the client — an
				// unloaded chunk silently gets a shared placeholder "empty chunk" with
				// no block entities instead. Without this check, that placeholder gets
				// treated as "confirmed empty," and forgetGoneShops below wipes any
				// known shop inside it even though the chest is still there and just
				// hasn't finished (re)loading yet.
				if (!world.hasChunk(chunkPos.x(), chunkPos.z())) continue;
				LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());
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
						knownShops.put(pos.immutable(), sign);
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
	private void forgetGoneShops(ClientLevel world, Set<ChunkPos> scannedChunks) {
		if (scannedChunks.isEmpty()) return;
		ShopWorld shopWorld = WorldSelection.get();

		Iterator<Map.Entry<BlockPos, ShopSign>> it = knownShops.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, ShopSign> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!scannedChunks.contains(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4))) continue;

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
