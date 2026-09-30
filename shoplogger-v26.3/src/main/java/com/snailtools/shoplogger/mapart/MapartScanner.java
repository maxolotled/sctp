package com.snailtools.shoplogger.mapart;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldDetector;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Scans item frames holding filled maps near the player, merges frames that
 * touch each other on one wall into full rectangles (2x1, 3x2, ...), stitches
 * each rectangle into one picture and uploads it to the SCTP mapart catalog.
 *
 * Built so it can never hitch the game:
 *  - the game thread only READS frames, hashes their pixel arrays (microseconds)
 *    and copies the pixels of the few pieces that actually changed;
 *  - stitching, PNG encoding, Base64 and the HTTP upload all run on one
 *    low-priority background thread, one piece at a time, with a pause between
 *    uploads;
 *  - at most a handful of pieces are queued per scan and the queue is bounded;
 *  - nothing is ever printed to chat (see MapartPreviewScreen for what's going on).
 *
 * The world comes from the mod's own world detection — only scans once it has
 * been confirmed on this connection, so a piece can't be filed under the wrong world.
 */
public final class MapartScanner {

	private static final MapartScanner INSTANCE = new MapartScanner();
	public static MapartScanner getInstance() { return INSTANCE; }

	private static final String API_URL = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev/mapart/upload";
	// Same shared key every other mod upload uses (see ShopUploader).
	private static final String API_KEY = "JjabYIfRtghvBJNoy6857TFVHbjknlMOi6754E5dcfvhgBHNI6b564";
	// New key in 2.3 so the scanner starts OFF for everyone (uploads are paused
	// server-side for now), including players who had switched it on before.
	private static final String CONFIG_ENABLED = "mapart/enabledV2";

	private static final int SCAN_INTERVAL_TICKS = 100;   // every 5 s
	private static final double SCAN_RADIUS = 48.0;
	private static final int MAX_GRID = 20;               // matches the Worker's MAPART_MAX_GRID
	private static final int MAX_FRAMES_PER_SCAN = 600;
	private static final int MAX_QUEUED_PER_SCAN = 4;
	private static final int QUEUE_LIMIT = 24;
	private static final long FAIL_RETRY_MS = 2 * 60 * 1000L;
	private static final long PAUSE_BETWEEN_UPLOADS_MS = 400L;
	private static final int MAX_PREVIEW_PIECES = 80;
	static final int THUMB = 64;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_LIMIT), r -> {
				Thread t = new Thread(r, "sctp-mapart-upload");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				return t;
			});

	public enum Status { QUEUED, UPLOADED, UNCHANGED, FAILED }

	/** What the preview screen shows for one piece. Fields are written by the upload thread. */
	public static final class Piece {
		public final String key;
		public final String world;
		public volatile String title;
		public volatile int width, height;
		public volatile Status status = Status.QUEUED;
		public volatile byte[] thumbPng;
		public volatile long updatedAt = System.currentTimeMillis();
		Piece(String key, String world) { this.key = key; this.world = world; }
	}

	private int tickCounter = 0;
	/** world|leadMapId -> signature of what was last uploaded, so unchanged pieces aren't re-sent. */
	private final Map<String, String> uploaded = new ConcurrentHashMap<>();
	private final Set<String> pending = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();
	private final Map<String, Piece> pieces = new ConcurrentHashMap<>();
	private volatile long lastScanAt = 0L;
	private volatile int lastFrameCount = 0;

	private record FrameInfo(int gx, int gy, int mapId, int rotation, String name, byte[] colors, int colorsHash) {}

	private record Group(int minX, int minY, int width, int height, List<FrameInfo> frames) {}

	private record JobFrame(int gx, int gy, int mapId, int rotation, String name, byte[] colors) {}

	private record Job(String world, String key, String sig, Group shape, int leadMapId, String rawName,
					   List<String> allNames, List<JobFrame> frames) {}

	private MapartScanner() {}

	// ---------------- public state (for the preview screen) ----------------

	public boolean isEnabled() {
		return Config.getOrDefault(CONFIG_ENABLED, Boolean.class, Boolean.FALSE);
	}

	public void setEnabled(boolean on) {
		Config.update(CONFIG_ENABLED, on);
	}

	/** Newest first. */
	public List<Piece> snapshot() {
		List<Piece> list = new ArrayList<>(pieces.values());
		list.sort(Comparator.comparingLong((Piece p) -> p.updatedAt).reversed());
		return list;
	}

	public long lastScanAt() { return lastScanAt; }
	public int lastFrameCount() { return lastFrameCount; }
	public int queuedCount() { return pending.size(); }

	/** Forgets what was uploaded so every nearby piece is sent again on the next scan. */
	public void resendAll() {
		uploaded.clear();
		failedUntil.clear();
		tickCounter = SCAN_INTERVAL_TICKS;
	}

	// ---------------- game-thread part ----------------

	public void tick(Minecraft client) {
		if (client.level == null || client.player == null) return;
		if (++tickCounter < SCAN_INTERVAL_TICKS) return;
		tickCounter = 0;
		if (!isEnabled() || client.hasSingleplayerServer()) return;
		ShopWorld world = WorldSelection.get();
		if (world == null || !WorldDetector.getInstance().isConfirmedThisSession()) return;
		try {
			scan(client, world.label());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void scan(Minecraft client, String world) {
		AABB box = client.player.getBoundingBox().inflate(SCAN_RADIUS);
		List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, box);
		lastScanAt = System.currentTimeMillis();

		Map<String, Map<Long, FrameInfo>> planes = new HashMap<>();
		List<Group> groups = new ArrayList<>();
		int seen = 0;

		for (ItemFrame frame : frames) {
			ItemStack stack = frame.getItem();
			if (!stack.is(Items.FILLED_MAP)) continue;
			MapId id = stack.get(DataComponents.MAP_ID);
			if (id == null) continue;
			MapItemSavedData data = client.level.getMapData(id);
			if (data == null) continue; // pixel data not received yet — picked up on a later scan
			if (++seen > MAX_FRAMES_PER_SCAN) break;

			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			String name = custom == null ? null : custom.getString();
			int rot = Math.floorMod(frame.getRotation(), 4);
			Direction dir = frame.getDirection();
			BlockPos pos = frame.blockPosition();
			int hash = Arrays.hashCode(data.colors);

			if (dir.getAxis().isHorizontal()) {
				Direction right = dir.getOpposite().getClockWise(); // viewer's right when looking at the frame
				int gx = pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
				int gy = -pos.getY();
				int plane = dir.getAxis() == Direction.Axis.Z ? pos.getZ() : pos.getX();
				String key = dir.getName() + "@" + plane;
				planes.computeIfAbsent(key, k -> new HashMap<>()).put(cellKey(gx, gy), new FrameInfo(gx, gy, id.id(), rot, name, data.colors, hash));
			} else {
				// Floor/ceiling frames have no wall grid to merge on — standalone.
				groups.add(new Group(0, 0, 1, 1, List.of(new FrameInfo(0, 0, id.id(), rot, name, data.colors, hash))));
			}
		}
		lastFrameCount = seen;
		for (Map<Long, FrameInfo> cells : planes.values()) groups.addAll(decompose(cells));
		if (groups.isEmpty()) return;

		long now = System.currentTimeMillis();
		int queued = 0;
		for (Group g : groups) {
			if (queued >= MAX_QUEUED_PER_SCAN) break;
			FrameInfo lead = pickLead(g);
			String key = world + "|" + lead.mapId();
			String sig = signature(g, lead);
			if (sig.equals(uploaded.get(key)) || pending.contains(key)) continue;
			Long retryAt = failedUntil.get(key);
			if (retryAt != null && retryAt > now) continue;
			if (WORKER.getQueue().remainingCapacity() == 0) break;

			// Only now copy the pixels: the arrays are mutated in place by map updates.
			List<JobFrame> jf = new ArrayList<>(g.frames().size());
			for (FrameInfo f : g.frames()) jf.add(new JobFrame(f.gx(), f.gy(), f.mapId(), f.rotation(), f.name(), f.colors().clone()));
			Set<String> names = new LinkedHashSet<>();
			for (FrameInfo f : g.frames()) if (f.name() != null && !f.name().isBlank()) names.add(f.name());
			Job job = new Job(world, key, sig, g, lead.mapId(), lead.name() == null ? "" : lead.name(), new ArrayList<>(names), jf);

			Piece piece = pieces.computeIfAbsent(key, k -> new Piece(k, world));
			piece.title = displayTitle(job.rawName());
			piece.width = g.width();
			piece.height = g.height();
			piece.status = Status.QUEUED;
			piece.updatedAt = now;
			trimPieces();

			pending.add(key);
			try {
				WORKER.execute(() -> run(job));
				queued++;
			} catch (Exception e) {
				pending.remove(key);
				break;
			}
		}
	}

	private void trimPieces() {
		if (pieces.size() <= MAX_PREVIEW_PIECES) return;
		List<Piece> all = snapshot();
		for (int i = MAX_PREVIEW_PIECES; i < all.size(); i++) pieces.remove(all.get(i).key);
	}

	private static String displayTitle(String raw) {
		String t = raw == null ? "" : raw.replaceAll("§.", "").trim();
		return t.isEmpty() ? "Unnamed mapart" : t;
	}

	// ---------------- grouping ----------------

	private static long cellKey(int gx, int gy) {
		return ((long) gx << 32) ^ (gy & 0xFFFFFFFFL);
	}

	/**
	 * Splits one wall's frames into full rectangles: repeatedly takes the
	 * largest solid WxH block still left, so a perfect 3x2 wall merges into one
	 * piece while an L shape becomes a rectangle plus standalone leftovers.
	 */
	private static List<Group> decompose(Map<Long, FrameInfo> cells) {
		List<Group> result = new ArrayList<>();
		Map<Long, FrameInfo> remaining = new HashMap<>(cells);
		while (!remaining.isEmpty()) {
			Group best = bestRectangle(remaining);
			result.add(best);
			for (FrameInfo f : best.frames()) remaining.remove(cellKey(f.gx(), f.gy()));
		}
		return result;
	}

	private static Group bestRectangle(Map<Long, FrameInfo> cells) {
		List<FrameInfo> ordered = new ArrayList<>(cells.values());
		ordered.sort(Comparator.comparingInt(FrameInfo::gy).thenComparingInt(FrameInfo::gx));

		Map<Long, Integer> downRun = new HashMap<>();
		int bestArea = 0, bestX = 0, bestY = 0, bestW = 0, bestH = 0;
		for (FrameInfo top : ordered) {
			int minH = Integer.MAX_VALUE;
			for (int w = 1; w <= MAX_GRID; w++) {
				int x = top.gx() + (w - 1);
				if (!cells.containsKey(cellKey(x, top.gy()))) break;
				minH = Math.min(minH, Math.min(MAX_GRID, run(cells, downRun, x, top.gy())));
				int area = w * minH;
				if (area > bestArea) {
					bestArea = area; bestX = top.gx(); bestY = top.gy(); bestW = w; bestH = minH;
				}
			}
		}
		List<FrameInfo> frames = new ArrayList<>();
		for (int y = bestY; y < bestY + bestH; y++) {
			for (int x = bestX; x < bestX + bestW; x++) frames.add(cells.get(cellKey(x, y)));
		}
		return new Group(bestX, bestY, bestW, bestH, frames);
	}

	/** Number of consecutive present cells going down from (x, y). */
	private static int run(Map<Long, FrameInfo> cells, Map<Long, Integer> memo, int x, int y) {
		long key = cellKey(x, y);
		Integer cached = memo.get(key);
		if (cached != null) return cached;
		int value = cells.containsKey(key) ? 1 + run(cells, memo, x, y + 1) : 0;
		memo.put(key, value);
		return value;
	}

	/** The named map (title + seller live in its custom name); the longest name wins, else the top-left frame. */
	private static FrameInfo pickLead(Group g) {
		FrameInfo lead = null;
		for (FrameInfo f : g.frames()) {
			if (f.name() == null || f.name().isBlank()) continue;
			if (lead == null || f.name().length() > lead.name().length()) lead = f;
		}
		return lead != null ? lead : g.frames().get(0);
	}

	private static String signature(Group g, FrameInfo lead) {
		StringBuilder sb = new StringBuilder();
		sb.append(g.width()).append('x').append(g.height()).append('|').append(lead.mapId()).append('|').append(lead.name());
		for (FrameInfo f : g.frames()) sb.append("|n:").append(f.name());
		for (FrameInfo f : g.frames()) sb.append('|').append(f.mapId()).append(':').append(f.rotation()).append(':').append(f.colorsHash());
		return Integer.toHexString(sb.toString().hashCode()) + "-" + sb.length();
	}

	// ---------------- upload thread ----------------

	private void run(Job job) {
		Piece piece = pieces.get(job.key());
		try {
			int width = job.shape().width() * 128, height = job.shape().height() * 128;
			int[] argb = stitch(job, width, height);
			byte[] png = PngWriter.encode(width, height, argb);
			if (piece != null) piece.thumbPng = thumbnail(argb, width, height);

			JsonObject o = new JsonObject();
			o.addProperty("leadMapId", job.leadMapId());
			o.addProperty("rawName", job.rawName());
			JsonArray allNames = new JsonArray();
			for (String n : job.allNames()) allNames.add(n);
			o.add("allNames", allNames);
			o.addProperty("width", job.shape().width());
			o.addProperty("height", job.shape().height());
			JsonArray partIds = new JsonArray();
			for (JobFrame f : job.frames()) partIds.add(f.mapId());
			o.add("partMapIds", partIds);
			o.addProperty("png", Base64.getEncoder().encodeToString(png));
			JsonArray maps = new JsonArray();
			maps.add(o);
			JsonObject body = new JsonObject();
			body.addProperty("world", job.world());
			body.add("maps", maps);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_URL))
					.timeout(Duration.ofSeconds(60))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + API_KEY)
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());

			Status status = Status.UPLOADED;
			JsonArray results = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("results");
			for (JsonElement el : results) {
				String s = el.getAsJsonObject().get("status").getAsString();
				if (s.equals("error")) throw new IllegalStateException("rejected");
				if (s.equals("unchanged")) status = Status.UNCHANGED;
			}
			uploaded.put(job.key(), job.sig());
			failedUntil.remove(job.key());
			if (piece != null) { piece.status = status; piece.updatedAt = System.currentTimeMillis(); }
		} catch (Exception e) {
			failedUntil.put(job.key(), System.currentTimeMillis() + FAIL_RETRY_MS);
			if (piece != null) { piece.status = Status.FAILED; piece.updatedAt = System.currentTimeMillis(); }
			System.err.println("[ShopLogger] mapart upload failed for " + job.key() + ": " + e);
		} finally {
			pending.remove(job.key());
			try { Thread.sleep(PAUSE_BETWEEN_UPLOADS_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		}
	}

	private static int[] stitch(Job job, int width, int height) {
		int[] argb = new int[width * height];
		for (JobFrame f : job.frames()) {
			int ox = (f.gx() - job.shape().minX()) * 128, oy = (f.gy() - job.shape().minY()) * 128;
			for (int sy = 0; sy < 128; sy++) {
				for (int sx = 0; sx < 128; sx++) {
					int px = colorOf(f.colors()[sx + sy * 128]);
					int dx, dy;
					switch (f.rotation()) { // clockwise quarter turns, as the frame shows the map
						case 1 -> { dx = 127 - sy; dy = sx; }
						case 2 -> { dx = 127 - sx; dy = 127 - sy; }
						case 3 -> { dx = sy; dy = 127 - sx; }
						default -> { dx = sx; dy = sy; }
					}
					argb[(oy + dy) * width + ox + dx] = px;
				}
			}
		}
		return argb;
	}

	/** Nearest-neighbour fit into a THUMB x THUMB transparent square, as PNG bytes (for the preview screen). */
	private static byte[] thumbnail(int[] argb, int w, int h) throws java.io.IOException {
		double scale = Math.min((double) THUMB / w, (double) THUMB / h);
		int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
		int[] out = new int[THUMB * THUMB];
		int offX = (THUMB - tw) / 2, offY = (THUMB - th) / 2;
		for (int y = 0; y < th; y++) {
			int sy = Math.min(h - 1, (int) (y / scale));
			for (int x = 0; x < tw; x++) {
				int sx = Math.min(w - 1, (int) (x / scale));
				out[(offY + y) * THUMB + offX + x] = argb[sy * w + sx];
			}
		}
		return PngWriter.encode(THUMB, THUMB, out);
	}

	/** Packed map colour byte -> ARGB (base colour id in the high 6 bits, brightness in the low 2). */
	private static int colorOf(byte packed) {
		int id = packed & 0xFF;
		int base = id >> 2;
		if (base == 0) return 0; // unexplored/transparent
		int mult = switch (id & 3) {
			case 0 -> 180;
			case 1 -> 220;
			case 2 -> 255;
			default -> 135;
		};
		int rgb = MapColor.byId(base).col;
		int r = ((rgb >> 16) & 0xFF) * mult / 255;
		int gr = ((rgb >> 8) & 0xFF) * mult / 255;
		int b = (rgb & 0xFF) * mult / 255;
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}
}
