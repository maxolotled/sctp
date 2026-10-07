package com.snailtools.shoplogger;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock holograms (Settings › Look & tools, off by default): one small line of
 * text on the FRONT of a scanned shop chest, under its sign — "64 in stock · 3m
 * ago" or "Empty · 3m ago". It's printed flat on the chest face like sign text
 * (it never turns to follow you), only for shops within a few blocks, and only
 * while you're standing in front of the chest.
 */
public final class StockHolograms {

	private StockHolograms() {}

	private static final String CONFIG_ENABLED = "stockHolograms/enabled";
	private static final double MAX_DISTANCE = 8.0;
	private static final float SCALE = 0.0085f;
	private static final int COLOR = 0xFFD9DED6;
	private static final int EMPTY_COLOR = 0xFF9AA19A;

	private record Summary(String world, int stock, int items, long scannedAt) {}
	private static final Map<BlockPos, Summary> LATEST = new HashMap<>();

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, false);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	/** Called after every real scan (silent or manual) with what was found. */
	public static void record(String world, BlockPos containerPos, List<ShopEntry> entries) {
		if (world == null || containerPos == null) return;
		int stock = 0;
		for (ShopEntry e : entries) stock += e.amountAvailable();
		LATEST.put(containerPos.immutable(), new Summary(world, stock, entries.size(), System.currentTimeMillis()));
	}

	/** Called while block entities are submitted, with the camera-relative pose (see LevelRendererMixin). */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
		if (!isEnabled() || LATEST.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !ShopDimension.isActive(mc)) return;
		ShopWorld world = WorldSelection.get();
		if (world == null) return;
		Font font = mc.font;
		long now = System.currentTimeMillis();

		for (Map.Entry<BlockPos, Summary> e : LATEST.entrySet()) {
			Summary s = e.getValue();
			if (!world.label().equalsIgnoreCase(s.world())) continue;
			BlockPos pos = e.getKey();
			if (Vec3.atCenterOf(pos).distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;

			BlockState state = mc.level.getBlockState(pos);
			if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && !(state.getBlock() instanceof BarrelBlock)) continue;
			Direction front = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
					? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
					: state.getValue(BlockStateProperties.FACING);
			if (front.getAxis() == Direction.Axis.Y) continue; // a barrel facing up/down has no front to print on

			// only from in front of the chest: behind or beside it there's nothing to read
			Vec3 faceCenter = Vec3.atCenterOf(pos).add(front.getStepX() * 0.5, 0, front.getStepZ() * 0.5);
			Vec3 toCam = camera.subtract(faceCenter);
			if (toCam.x * front.getStepX() + toCam.z * front.getStepZ() <= 0.05) continue;

			String age = ago(now - s.scannedAt());
			String text = s.items() == 0 || s.stock() == 0 ? "Empty · " + age
					: (s.items() > 1 ? s.items() + " items · " : "") + s.stock() + " in stock · " + age;
			FormattedCharSequence line = Component.literal(text).getVisualOrderText();
			boolean empty = s.items() == 0 || s.stock() == 0;
			// chests are inset 1/16 from the block edge; barrels fill the block
			double faceDepth = state.getBlock() instanceof BarrelBlock ? 0.5 : 0.4375;

			poseStack.pushPose();
			poseStack.translate(pos.getX() + 0.5 - camera.x, pos.getY() + 0.5 - camera.y, pos.getZ() + 0.5 - camera.z);
			// same as Axis.YP.rotationDegrees(-yRot); a matrix so it compiles on 26.2 and 26.3 alike
			poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-front.toYRot()))); // local +z now points out of the front
			// low on the face, below where a wall sign sits (signs start 4.5/16 up)
			poseStack.translate(0.0, -0.36, faceDepth + 0.004);
			poseStack.scale(SCALE, -SCALE, SCALE);
			collector.submitText(poseStack, -font.width(line) / 2f, 0, line, false, Font.DisplayMode.POLYGON_OFFSET,
					0xF000F0, empty ? EMPTY_COLOR : COLOR, 0, 0);
			poseStack.popPose();
		}
	}

	private static String ago(long ms) {
		long m = ms / 60000;
		if (m < 1) return "just now";
		if (m < 60) return m + "m ago";
		long h = m / 60;
		return h < 24 ? h + "h ago" : (h / 24) + "d ago";
	}
}
