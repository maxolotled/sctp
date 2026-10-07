package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Sprinkles a faint red particle near chests/barrels ShopAutoScanner has
 * scanned in the last few minutes — a subtle "already checked, no need to
 * open this one again yet" hint while walking a shop route. Deliberately
 * understated: dim color, small scale, sparse spawn rate, not a persistent
 * outline.
 */
public final class ShopMarkerRenderer {

	private static final ShopMarkerRenderer INSTANCE = new ShopMarkerRenderer();
	public static ShopMarkerRenderer getInstance() { return INSTANCE; }

	private static final int COLOR = 0xB33A3A; // muted brick red, not alarm-red
	// Chests whose scan found nothing for sale get this instead — same muted styling, grey.
	private static final int EMPTY_COLOR = 0x8A8A8A;
	// (Own shops with an uncollected payment get a green highlight from
	// WorldHighlights instead of a particle.)
	private static final float SCALE = 1.25f;
	/** How often (in ticks) each marked chest gets a fresh particle. 60 ticks = 3s. */
	private static final int SPAWN_INTERVAL_TICKS = 20;
	/** Only bother for chests actually close enough to notice. */
	private static final double MAX_DISTANCE = 24.0;

	private final Random random = new Random();
	private boolean enabled = true;
	private int tickCounter = 0;

	private ShopMarkerRenderer() {}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void tick(Minecraft client) {
		if (!enabled || client.level == null || client.player == null) return;

		tickCounter++;
		if (tickCounter % SPAWN_INTERVAL_TICKS != 0) return;

		Vec3 eye = client.player.getEyePosition();

		for (BlockPos containerPos : ShopAutoScanner.getInstance().getRecentlyScannedPositions()) {
			// Mark the sign, not the container — that's what the player is
			// actually reading while walking a shop route. Falls back to the
			// container position if this container has no known sign yet
			// (e.g. a manually-opened chest ShopAutoScanner never discovered).
			ShopSign sign = ShopAutoScanner.getInstance().getKnownSign(containerPos);
			BlockPos pos = sign != null ? sign.signPos() : containerPos;

			if (new Vec3(pos.getX(), pos.getY(), pos.getZ()).distanceToSqr(eye) > MAX_DISTANCE * MAX_DISTANCE) continue;

			if (OwnShopSaleTracker.hasPendingPayment(containerPos)) continue; // highlighted green instead
			int color = ShopAutoScanner.getInstance().wasEmpty(containerPos) ? EMPTY_COLOR : COLOR;
			DustParticleOptions effect = new DustParticleOptions(color, SCALE);

			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double y = pos.getY() + 0.85;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			client.level.addParticle(effect, x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
