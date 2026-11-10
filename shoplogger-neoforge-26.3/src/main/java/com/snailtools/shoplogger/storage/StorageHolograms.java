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
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Two short lines on the containers in My storage: the main item and how many
 * (or "Mixed" and the total), see StorageIndex.summary. Printed flat on the
 * lower half of EVERY side that's entirely open to air: a double chest's long
 * side only when both halves are clear, its short ends on their own. Boxed in
 * on every side, it goes on the lid instead. Containers that aren't in My
 * storage get nothing; shop chests keep their own holograms (StockHolograms).
 */
public final class StorageHolograms {

	private StorageHolograms() {}

	private static final double RANGE = 12.0;
	private static final float SCALE = 0.0095f;
	private static final int MAIN = 0xFFEDEFEA;
	private static final int SUB = 0xFFAEB5AC;
	/** Where the lines end (their bottom), from the block's middle: near the bottom of the side, so longer text grows upward. */
	private static final double BOTTOM = -0.40;
	/** Widest a line gets (in font pixels) before the name wraps: about the width of a chest's side. */
	private static final int MAX_LINE = 86;

	/** Every client tick (nothing to do: what's shown is read straight from the index each frame). */
	public static void tick(Minecraft mc) {}

	/** While block entities are submitted, camera-relative (LevelRendererMixin). */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
		if (!StorageSettings.isEnabled() || !StorageSettings.hologramsOn()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !StorageIndex.ready()) return;
		String dim = StorageIndex.dimension();
		for (StorageIndex.Box box : StorageIndex.boxes()) {
			if (!box.dim.equals(dim)) continue;
			BlockPos pos = box.pos();
			if (Vec3.atCenterOf(pos).distanceTo(camera) > RANGE || !mc.level.hasChunkAt(pos)) continue;
			if (!StorageContainers.isContainer(mc.level.getBlockEntity(pos))) continue; // broken since
			draw(pose, collector, camera, mc.level, mc.font, pos, StorageIndex.summary(box));
		}
	}

	private static boolean open(ClientLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || s.getCollisionShape(level, p).isEmpty();
	}

	private static void draw(PoseStack pose, SubmitNodeCollector collector, Vec3 camera, ClientLevel level, Font font, BlockPos pos, String[] lines) {
		BlockState state = level.getBlockState(pos);
		BlockPos partner = SignFinder.findDoubleChestPartner(level, pos, state);
		boolean fullBlock = state.getBlock() instanceof BarrelBlock || "shulker".equals(StorageContainers.kind(level.getBlockEntity(pos)));
		double depth = fullBlock ? 0.5 : 0.4375;
		Direction.Axis joined = partner == null ? null
				: Direction.getApproximateNearest(partner.getX() - pos.getX(), 0, partner.getZ() - pos.getZ()).getAxis();

		boolean anySide = false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Vec3 center;
			if (partner == null) {
				if (!open(level, pos.relative(d))) continue;
				center = Vec3.atCenterOf(pos);
			} else if (d.getAxis() == joined) {
				// a short end: just the half on that end
				BlockPos end = d.getStepX() * (partner.getX() - pos.getX()) + d.getStepZ() * (partner.getZ() - pos.getZ()) > 0 ? partner : pos;
				if (!open(level, end.relative(d))) continue;
				center = Vec3.atCenterOf(end);
			} else {
				// a long side: only when both halves face air
				if (!open(level, pos.relative(d)) || !open(level, partner.relative(d))) continue;
				center = Vec3.atCenterOf(pos).add(Vec3.atCenterOf(partner)).scale(0.5);
			}
			anySide = true;
			// only from the side it's printed on
			Vec3 toCam = camera.subtract(center);
			if (toCam.x * d.getStepX() + toCam.z * d.getStepZ() <= 0.05) continue;
			pose.pushPose();
			pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-d.toYRot())));
			pose.translate(0.0, BOTTOM, depth + 0.004);
			text(pose, collector, font, lines, true);
			pose.popPose();
		}
		if (anySide) return;

		// boxed in on every side: the lid, if that's open
		Vec3 center = partner == null ? Vec3.atCenterOf(pos) : Vec3.atCenterOf(pos).add(Vec3.atCenterOf(partner)).scale(0.5);
		if (!open(level, pos.above()) || (partner != null && !open(level, partner.above()))) return;
		double lid = fullBlock ? 0.5 : 0.375;
		if (camera.y < center.y + lid) return;
		Direction toCam = Direction.getApproximateNearest(camera.x - center.x, 0, camera.z - center.z);
		pose.pushPose();
		pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
		pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-toCam.toYRot())));
		pose.translate(0.0, lid + 0.004, 0.0);
		pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-90)));
		text(pose, collector, font, lines, false);
		pose.popPose();
	}

	/**
	 * The name (wrapped onto a second line when it's too wide, then cut off) and
	 * the amount under it. fromBottom: the last line ends at the pose's origin;
	 * otherwise the block of lines is centred on it.
	 */
	private static void text(PoseStack pose, SubmitNodeCollector collector, Font font, String[] lines, boolean fromBottom) {
		pose.scale(SCALE, -SCALE, SCALE);
		java.util.List<String> name = wrap(font, lines[0]);
		boolean amount = !lines[1].isEmpty();
		int count = name.size() + (amount ? 1 : 0);
		int height = count * 10 - 2;
		int y = fromBottom ? -height : -height / 2;
		for (String s : name) {
			FormattedCharSequence l = Component.literal(s).getVisualOrderText();
			collector.submitText(pose, -font.width(l) / 2f, y, l, false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, MAIN, 0, 0);
			y += 10;
		}
		if (amount) {
			FormattedCharSequence l2 = Component.literal(lines[1]).getVisualOrderText();
			collector.submitText(pose, -font.width(l2) / 2f, y, l2, false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, SUB, 0, 0);
		}
	}

	/** A name in at most two lines, split between words; what still doesn't fit ends in "…". */
	static java.util.List<String> wrap(Font font, String text) {
		if (font.width(text) <= MAX_LINE) return java.util.List.of(text);
		String[] words = text.split(" ");
		StringBuilder first = new StringBuilder();
		int i = 0;
		while (i < words.length && font.width(first + (first.length() > 0 ? " " : "") + words[i]) <= MAX_LINE) {
			if (first.length() > 0) first.append(' ');
			first.append(words[i++]);
		}
		if (first.length() == 0) { first.append(font.plainSubstrByWidth(words[0], MAX_LINE)); words[0] = words[0].substring(first.length()); }
		String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)).trim();
		if (rest.isEmpty()) return java.util.List.of(first.toString());
		if (font.width(rest) > MAX_LINE) rest = font.plainSubstrByWidth(rest, MAX_LINE - font.width("…")).trim() + "…";
		return java.util.List.of(first.toString(), rest);
	}
}
