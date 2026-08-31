package com.snailtools.shoplogger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights one specific shop container after the player taps a "TP" button
 * in a listing row (see ListingListWidget), so they can actually spot the
 * right chest once /shop teleports them into the general area. A line of
 * particles is drawn fresh every tick from the player's current eye position
 * straight to the container — not a static marker, so it visibly moves and
 * re-aims as the player walks, always pointing the way there.
 */
public final class TeleportHighlight {

	private static final TeleportHighlight INSTANCE = new TeleportHighlight();
	public static TeleportHighlight getInstance() { return INSTANCE; }

	private static final int COLOR = 0x39FF14; // bright green, unmistakably different from the muted scan markers
	private static final float SCALE = 1.6f;
	private static final double POINT_SPACING = 1.5; // blocks between particles along the beam
	private static final int MAX_POINTS = 24; // caps the per-tick particle cost for very long beams
	private static final double ARRIVAL_DISTANCE = 2.0;
	private static final long TIMEOUT_MS = 5 * 60 * 1000L;

	private BlockPos targetPos;
	private String targetWorldLabel;
	private long armedAtMillis;

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

	public void tick(MinecraftClient client) {
		if (targetPos == null || client.world == null || client.player == null) return;

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

		Vec3d eye = client.player.getEyePos();
		Vec3d target = Vec3d.ofCenter(targetPos);
		double dist = eye.distanceTo(target);
		if (dist <= ARRIVAL_DISTANCE) {
			clear();
			return;
		}

		client.player.sendMessage(Text.literal("Press X to cancel beam"), true);

		int points = Math.max(2, Math.min(MAX_POINTS, (int) Math.round(dist / POINT_SPACING)));
		for (int i = 0; i <= points; i++) {
			double t = i / (double) points;
			DustParticleEffect effect = new DustParticleEffect(COLOR, SCALE);
			double x = eye.x + (target.x - eye.x) * t;
			double y = eye.y + (target.y - eye.y) * t;
			double z = eye.z + (target.z - eye.z) * t;
			client.world.addParticleClient(effect, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}
