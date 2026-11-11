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
 * Two short lines on the containers in My storage. What they say is up to
 * Settings › My storage › Storage holograms: the main item and how many (or
 * "Mixed" and the total, see StorageIndex.summary), the container's name, or,
 * on every storage container around you, whether it's in My storage yet. Printed flat on the
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
	private static final int IN = 0xFF8EE07A, OUT = 0xFFFFB86B;
	/** Where the lines end (their bottom), from the block's middle: near the bottom of the side, so longer text grows upward. */
	private static final double BOTTOM = -0.40;
	/** Widest a line gets (in font pixels) before the name wraps: about the width of a chest's side. */
	private static final int MAX_LINE = 86;

	/** "In My storage?": the storage containers around you (refreshed twice a second), whether they're in or not. */
	private static java.util.List<BlockPos> around = java.util.List.of();
	private static int ticks;

	/** Every client tick: only the "In My storage?" mode needs to look around (the others read the index each frame). */
	public static void tick(Minecraft mc) {
		if (StorageSettings.hologramMode() != StorageSettings.HologramMode.INDEXED || !StorageSettings.isEnabled() || mc.level == null || mc.player == null) {
			around = java.util.List.of();
			return;
		}
		if (++ticks % 10 != 0) return;
		java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
		Vec3 eye = mc.player.getEyePosition();
		net.minecraft.world.level.ChunkPos c = mc.player.chunkPosition();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (!mc.level.hasChunk(c.x() + dx, c.z() + dz)) continue;
			for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> e : mc.level.getChunk(c.x() + dx, c.z() + dz).getBlockEntities().entrySet()) {
				if (!StorageContainers.isContainer(e.getValue()) || Vec3.atCenterOf(e.getKey()).distanceTo(eye) > RANGE) continue;
				if (!StorageContainers.isStorage(mc.level, e.getKey())) continue;
				found.add(StorageContainers.canonical(mc.level, e.getKey()));
			}
		}
		around = new java.util.ArrayList<>(found);
	}

	/** While block entities are submitted, camera-relative (LevelRendererMixin). */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
		if (!StorageSettings.isEnabled() || !StorageSettings.hologramsOn()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !StorageIndex.ready()) return;
		String dim = StorageIndex.dimension();
		StorageSettings.HologramMode mode = StorageSettings.hologramMode();
		if (mode == StorageSettings.HologramMode.INDEXED) {
			String add = com.snailtools.shoplogger.ModKeys.text("storage");
			for (BlockPos pos : around) {
				if (Vec3.atCenterOf(pos).distanceTo(camera) > RANGE || !mc.level.hasChunkAt(pos)) continue;
				if (!StorageContainers.isContainer(mc.level.getBlockEntity(pos))) continue;
				boolean in = StorageIndex.find(dim, pos) != null;
				draw(pose, collector, camera, mc.level, mc.font, pos, in ? new String[] { "In My storage", "" } : new String[] { "Not in My storage", add + " adds it" }, in ? IN : OUT);
			}
			return;
		}
		for (StorageIndex.Box box : StorageIndex.boxes()) {
			if (!box.dim.equals(dim)) continue;
			BlockPos pos = box.pos();
			if (Vec3.atCenterOf(pos).distanceTo(camera) > RANGE || !mc.level.hasChunkAt(pos)) continue;
			net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(pos);
			if (!StorageContainers.isContainer(be)) continue; // broken since
			String[] lines = StorageIndex.summary(box);
			if (mode == StorageSettings.HologramMode.NAME) {
				String name = nameOf(box, be);
				if (name != null) lines = new String[] { name, lines[0].equals("Indexed") || lines[0].equals("Locked") ? "" : lines[0] };
			}
			draw(pose, collector, camera, mc.level, mc.font, pos, lines, MAIN);
		}
	}

	/** Your name for it, else its anvil name (from the world, or its window's title when you opened it), else null. */
	private static String nameOf(StorageIndex.Box box, net.minecraft.world.level.block.entity.BlockEntity be) {
		if (box.named()) return box.name;
		if (be instanceof net.minecraft.world.Nameable n && n.hasCustomName() && n.getCustomName() != null) return n.getCustomName().getString();
		return box.title;
	}

	private static boolean open(ClientLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || s.getCollisionShape(level, p).isEmpty();
	}

	private static void draw(PoseStack pose, SubmitNodeCollector collector, Vec3 camera, ClientLevel level, Font font, BlockPos pos, String[] lines, int color) {
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
			text(pose, collector, font, lines, true, color);
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
		text(pose, collector, font, lines, false, color);
		pose.popPose();
	}

	/**
	 * The name (wrapped onto a second line when it's too wide, then cut off) and
	 * the amount under it. fromBottom: the last line ends at the pose's origin;
	 * otherwise the block of lines is centred on it.
	 */
	private static void text(PoseStack pose, SubmitNodeCollector collector, Font font, String[] lines, boolean fromBottom, int color) {
		pose.scale(SCALE, -SCALE, SCALE);
		java.util.List<String> name = wrap(font, lines[0]);
		boolean amount = !lines[1].isEmpty();
		int count = name.size() + (amount ? 1 : 0);
		int height = count * 10 - 2;
		int y = fromBottom ? -height : -height / 2;
		for (String s : name) {
			FormattedCharSequence l = Component.literal(s).getVisualOrderText();
			collector.submitText(pose, -font.width(l) / 2f, y, l, false, Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, color, 0, 0);
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
