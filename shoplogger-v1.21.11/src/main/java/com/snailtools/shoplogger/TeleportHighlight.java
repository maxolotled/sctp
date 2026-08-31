package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights one specific shop container after the player taps a "TP" button
 * in a listing row (see ListingListWidget), so they can actually spot the
 * right chest once /shop teleports them into the general area.
 */
public final class TeleportHighlight {

	private static final TeleportHighlight INSTANCE = new TeleportHighlight();
	public static TeleportHighlight getInstance() { return INSTANCE; }

	public enum BeamStyle {
		LINE("Full line"),
		SPARSE("Sparse line"),
		DESTINATION_ONLY("Destination marker only");

		public final String label;
		BeamStyle(String label) { this.label = label; }
	}

	private static final String CONFIG_STYLE = "teleportBeam/style";

	public static BeamStyle getStyle() {
		return Config.getOrCreate(CONFIG_STYLE, BeamStyle.class, BeamStyle.LINE);
	}

	public static void setStyle(BeamStyle style) {
		Config.update(CONFIG_STYLE, style);
	}

	private static final int COLOR = 0x39FF14; // bright green, unmistakably different from the muted scan markers
	private static final float SCALE = 1.6f;
	// Never spawn a particle closer than this to the player's own eyes — a
	// point drawn right in front of the camera reads as "covering the whole
	// screen" due to perspective, regardless of how sparse the rest of the
	// line is. This is why the original every-tick full line felt obtrusive.
	private static final double MIN_START_DISTANCE = 1.5;
	private static final double LINE_SPACING = 1.5;
	private static final int LINE_MAX_POINTS = 24;
	private static final double SPARSE_SPACING = 3.0;
	private static final int SPARSE_MAX_POINTS = 12;
	private static final int SPARSE_INTERVAL_TICKS = 4; // ~5 updates/sec instead of 20
	private static final int MARKER_INTERVAL_TICKS = 5;
	private static final double ARRIVAL_DISTANCE = 2.0;
	private static final long TIMEOUT_MS = 5 * 60 * 1000L;

	private BlockPos targetPos;
	private String targetWorldLabel;
	private long armedAtMillis;
	private int tickCounter;

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

		tickCounter++;
		client.player.sendMessage(Text.literal("Press X to cancel beam"), true);

		switch (getStyle()) {
			case DESTINATION_ONLY -> renderMarker(client);
			case SPARSE -> {
				if (tickCounter % SPARSE_INTERVAL_TICKS == 0) renderLine(client, eye, target, dist, SPARSE_SPACING, SPARSE_MAX_POINTS);
			}
			default -> renderLine(client, eye, target, dist, LINE_SPACING, LINE_MAX_POINTS);
		}
	}

	private void renderLine(MinecraftClient client, Vec3d eye, Vec3d target, double dist, double spacing, int maxPoints) {
		int points = Math.max(2, Math.min(maxPoints, (int) Math.round(dist / spacing)));
		for (int i = 0; i <= points; i++) {
			double t = i / (double) points;
			if (dist * t < MIN_START_DISTANCE) continue; // skip anything right in front of the camera
			DustParticleEffect effect = new DustParticleEffect(COLOR, SCALE);
			double x = eye.x + (target.x - eye.x) * t;
			double y = eye.y + (target.y - eye.y) * t;
			double z = eye.z + (target.z - eye.z) * t;
			client.world.addParticleClient(effect, x, y, z, 0.0, 0.0, 0.0);
		}
	}

	/** A short, low-key pillar at the chest only — no line back to the player at all. */
	private void renderMarker(MinecraftClient client) {
		if (tickCounter % MARKER_INTERVAL_TICKS != 0) return;
		for (int i = 0; i <= 5; i++) {
			DustParticleEffect effect = new DustParticleEffect(COLOR, SCALE);
			double x = targetPos.getX() + 0.5;
			double y = targetPos.getY() + 0.3 + i * 0.4;
			double z = targetPos.getZ() + 0.5;
			client.world.addParticleClient(effect, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}
