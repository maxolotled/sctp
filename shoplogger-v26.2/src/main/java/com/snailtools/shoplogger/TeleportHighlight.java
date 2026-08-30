package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights one specific shop container after the player taps a "TP" button
 * in a listing row (see ListingListWidget), so they can actually spot the
 * right chest once /shop teleports them into the general area. Deliberately
 * far more obvious than ShopMarkerRenderer's subtle "recently scanned" dots —
 * this is a single active target, rendered every tick, not throttled.
 */
public final class TeleportHighlight {

	private static final TeleportHighlight INSTANCE = new TeleportHighlight();
	public static TeleportHighlight getInstance() { return INSTANCE; }

	private static final int COLOR = 0x39FF14; // bright green, unmistakably different from the muted scan markers
	private static final float SCALE = 1.6f;
	private static final int BEAM_HEIGHT_BLOCKS = 6;
	private static final double ARRIVAL_DISTANCE = 2.0;
	private static final long TIMEOUT_MS = 5 * 60 * 1000L;

	private BlockPos targetPos;
	private String targetWorldLabel;
	private long armedAtMillis;
	private final java.util.Random random = new java.util.Random();

	private TeleportHighlight() {}

	public void arm(String worldLabel, BlockPos pos) {
		this.targetWorldLabel = worldLabel;
		this.targetPos = pos;
		this.armedAtMillis = System.currentTimeMillis();
	}

	public void clear() {
		targetPos = null;
		targetWorldLabel = null;
	}

	public boolean isArmed() {
		return targetPos != null;
	}

	public void tick(Minecraft client) {
		if (targetPos == null || client.level == null || client.player == null) return;

		if (System.currentTimeMillis() - armedAtMillis > TIMEOUT_MS) {
			clear();
			return;
		}

		// Both Firefly and Honeybee route through the same shop dimension, so
		// the world label (the player's own /setworld choice) is what
		// actually disambiguates which one the target belongs to.
		if (!ShopDimension.isActive(client)) return;
		ShopWorld current = WorldSelection.get();
		if (current == null || targetWorldLabel == null || !targetWorldLabel.equalsIgnoreCase(current.label())) return;

		Vec3 eye = client.player.getEyePosition();
		double dist = Math.sqrt(new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ()).distanceToSqr(eye));
		if (dist <= ARRIVAL_DISTANCE) {
			clear();
			return;
		}

		client.player.sendOverlayMessage(Component.literal(String.format("Target chest: %.0f blocks away", dist)));

		for (int i = 0; i <= BEAM_HEIGHT_BLOCKS; i++) {
			DustParticleOptions effect = new DustParticleOptions(COLOR, SCALE);
			double x = targetPos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
			double y = targetPos.getY() + 0.5 + i;
			double z = targetPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
			client.level.addParticle(effect, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}
