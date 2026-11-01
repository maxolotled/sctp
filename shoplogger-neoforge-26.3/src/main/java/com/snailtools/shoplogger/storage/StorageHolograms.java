package com.snailtools.shoplogger.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.snailtools.shoplogger.SignFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two short lines on your storage containers: the main item and how many (or
 * "Mixed" and the total), see StorageIndex.summary; containers near you that
 * aren't indexed yet say "Unindexed". Printed flat on whichever side of the
 * container faces open air (its front first), or on its lid when every side is
 * blocked. Shop chests keep their own holograms (StockHolograms).
 */
public final class StorageHolograms {

	private StorageHolograms() {}

	private static final double INDEXED_RANGE = 12.0;
	private static final double UNINDEXED_RANGE = 6.0;
	private static final float SCALE = 0.0095f;
	private static final int MAIN = 0xFFEDEFEA;
	private static final int SUB = 0xFFAEB5AC;
	private static final int UNINDEXED = 0xFF8C948A;

	private static final Set<BlockPos> nearby = new LinkedHashSet<>();
	private static int tick;

	/** Every client tick: once a second, which storage containers are around you. */
	public static void tick(Minecraft mc) {
		if (tick++ % 20 != 0) return;
		nearby.clear();
		if (mc.level == null || mc.player == null || !StorageSettings.isEnabled() || !StorageSettings.hologramsOn()) return;
		ClientLevel level = mc.level;
		ChunkPos c = mc.player.chunkPosition();
		Vec3 eye = mc.player.getEyePosition();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (!level.hasChunk(c.x() + dx, c.z() + dz)) continue;
			LevelChunk chunk = level.getChunk(c.x() + dx, c.z() + dz);
			for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
				if (!StorageContainers.isContainer(e.getValue())) continue;
				BlockPos pos = e.getKey();
				if (Vec3.atCenterOf(pos).distanceTo(eye) > INDEXED_RANGE + 2) continue;
				if (!StorageContainers.isStorage(level, pos)) continue;
				nearby.add(StorageContainers.canonical(level, pos));
			}
		}
	}

	/** While block entities are submitted, camera-relative (LevelRendererMixin). */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
		if (nearby.isEmpty() || !StorageSettings.isEnabled() || !StorageSettings.hologramsOn()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !StorageIndex.ready()) return;
		String dim = StorageIndex.dimension();
		boolean showUnindexed = StorageSettings.unindexedLabelsOn();
		for (BlockPos pos : nearby) {
			double dist = Vec3.atCenterOf(pos).distanceTo(camera);
			StorageIndex.Box box = StorageIndex.find(dim, pos);
			String[] lines;
			if (box != null) {
				if (dist > INDEXED_RANGE) continue;
				lines = StorageIndex.summary(box);
			} else {
				if (!showUnindexed || dist > UNINDEXED_RANGE) continue;
				lines = new String[] { "Unindexed", "" };
			}
			draw(pose, collector, camera, mc.level, mc.font, pos, lines, box == null);
		}
	}

	private static boolean open(ClientLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || s.getCollisionShape(level, p).isEmpty();
	}

	private static void draw(PoseStack pose, SubmitNodeCollector collector, Vec3 camera, ClientLevel level, Font font, BlockPos pos, String[] lines, boolean unindexed) {
		BlockState state = level.getBlockState(pos);
		BlockPos partner = SignFinder.findDoubleChestPartner(level, pos, state);
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(pos);
		if (partner != null) blocks.add(partner);
		Vec3 center = Vec3.atCenterOf(pos);
		if (partner != null) center = center.add(Vec3.atCenterOf(partner)).scale(0.5);

		// the front first, then the other sides; a double chest only its long sides
		List<Direction> order = new ArrayList<>();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) order.add(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		else if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis() != Direction.Axis.Y) order.add(state.getValue(BlockStateProperties.FACING));
		for (Direction d : Direction.Plane.HORIZONTAL) if (!order.contains(d)) order.add(d);
		Direction face = null;
		for (Direction d : order) {
			if (partner != null && d.getAxis() == Direction.getApproximateNearest(partner.getX() - pos.getX(), 0, partner.getZ() - pos.getZ()).getAxis()) continue;
			boolean free = true;
			for (BlockPos b : blocks) if (!open(level, b.relative(d))) { free = false; break; }
			if (free) { face = d; break; }
		}
		boolean top = face == null;
		if (top) {
			boolean free = true;
			for (BlockPos b : blocks) if (!open(level, b.above())) { free = false; break; }
			if (!free) return; // boxed in on every side: nowhere to print
		}

		boolean fullBlock = state.getBlock() instanceof BarrelBlock || "shulker".equals(StorageContainers.kind(level.getBlockEntity(pos)));
		pose.pushPose();
		pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
		if (!top) {
			// only from the side it's printed on
			Vec3 toCam = camera.subtract(center);
			if (toCam.x * face.getStepX() + toCam.z * face.getStepZ() <= 0.05) { pose.popPose(); return; }
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-face.toYRot())));
			pose.translate(0.0, 0.08, (fullBlock ? 0.5 : 0.4375) + 0.004);
		} else {
			double lid = fullBlock ? 0.5 : 0.375;
			if (camera.y < center.y + lid) { pose.popPose(); return; }
			Direction toCam = Direction.getApproximateNearest(camera.x - center.x, 0, camera.z - center.z);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-toCam.toYRot())));
			pose.translate(0.0, lid + 0.004, 0.0);
			pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-90)));
		}
		pose.scale(SCALE, -SCALE, SCALE);
		FormattedCharSequence l1 = Component.literal(lines[0]).getVisualOrderText();
		int y = lines[1].isEmpty() ? -4 : -9;
		collector.submitText(pose, -font.width(l1) / 2f, y, l1, false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, unindexed ? UNINDEXED : MAIN, 0, 0);
		if (!lines[1].isEmpty()) {
			FormattedCharSequence l2 = Component.literal(lines[1]).getVisualOrderText();
			collector.submitText(pose, -font.width(l2) / 2f, y + 10, l2, false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, SUB, 0, 0);
		}
		pose.popPose();
	}
}
