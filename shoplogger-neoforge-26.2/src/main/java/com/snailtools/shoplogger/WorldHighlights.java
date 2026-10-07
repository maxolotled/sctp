package com.snailtools.shoplogger;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Everything the mod draws into the world itself (not particles), called from
 * LevelRendererMixin once per frame:
 *
 *  - "highlights": the vanilla look-at-a-block outline, but coloured, thicker
 *    and drawn on top of everything so it shows through walls. Used for your
 *    own shops that have an uncollected payment (green) and the teleport
 *    beam's "Chest highlight" style.
 *  - beacon beams: a real vanilla beacon beam rising out of a block, for the
 *    teleport beam's "Beacon beam" style.
 */
public final class WorldHighlights {

	private WorldHighlights() {}

	public static final int SALE_COLOR = 0xFF3DFF6E;   // own shop with a payment waiting
	public static final int TARGET_COLOR = 0xFF39FF14; // teleport target, same green as the particle beam
	private static final float STROKE_WIDTH = 4.0f;
	private static final int FILL_ALPHA = 0x26;
	/** Highlights further away than this are skipped: they'd only be a few pixels anyway. */
	private static final double MAX_SALE_DISTANCE = 64.0;

	/** Called with the per-frame gizmo collector active (see LevelRendererMixin). */
	public static void emitGizmos() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		BlockPos target = TeleportHighlight.getInstance().highlightTarget();
		if (target != null) highlight(mc.level, target, TARGET_COLOR);

		if (ShopDimension.isActive(mc)) {
			Vec3 eye = mc.player.getEyePosition();
			for (BlockPos pos : OwnShopSaleTracker.pendingPaymentPositions()) {
				if (pos.equals(target)) continue;
				if (Vec3.atCenterOf(pos).distanceToSqr(eye) > MAX_SALE_DISTANCE * MAX_SALE_DISTANCE) continue;
				highlight(mc.level, pos, SALE_COLOR);
			}
		}
	}

	/** One block's outline (both halves for a double chest), visible through other blocks. */
	public static void highlight(Level level, BlockPos pos, int argb) {
		AABB box = outlineBox(level, pos);
		int fill = (FILL_ALPHA << 24) | (argb & 0x00FFFFFF);
		Gizmos.cuboid(box.inflate(0.002), GizmoStyle.strokeAndFill(argb, STROKE_WIDTH, fill)).setAlwaysOnTop();
	}

	private static AABB outlineBox(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		AABB box = shapeBox(level, pos, state);
		if (state.getBlock() instanceof ChestBlock && ShopContainers.isDoubleChest(state)) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			box = box.minmax(shapeBox(level, other, level.getBlockState(other)));
		}
		return box;
	}

	private static AABB shapeBox(Level level, BlockPos pos, BlockState state) {
		VoxelShape shape = state.getShape(level, pos);
		return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
	}

	/** Called while the level's block entities are submitted, with the camera-relative pose (see LevelRendererMixin). */
	public static void submitBeams(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		BlockPos pos = TeleportHighlight.getInstance().beaconTarget();
		if (pos == null) return;

		// same animation clock vanilla beacons use, so it scrolls at the familiar speed
		float time = Math.floorMod(mc.level.getGameTime(), 40) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() + 1 - camera.y, pos.getZ() - camera.z);
		BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0f, time,
				0, BeaconRenderer.MAX_RENDER_Y, TARGET_COLOR, 0.2f, 0.25f);
		poseStack.popPose();
	}
}
