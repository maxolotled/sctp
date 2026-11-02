package com.snailtools.shoplogger.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.snailtools.shoplogger.ShopEntryFactory;
import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * My storage: the containers you added with X+M, what's in each, and where.
 * Kept per Minecraft account and per world (Firefly / Honeybee) in a local file
 * (config/sctp/storage/), never uploaded. Items are named exactly as shop scans
 * name them (ShopEntryFactory.displayNameFor), so they match listings and pages.
 *
 * A container is added "waiting": StorageScanner opens it silently once you're
 * within reach. After that only your own opens update it (StorageTracker), and
 * every update keeps a short log of what went in and came out.
 */
public final class StorageIndex {

	private StorageIndex() {}

	public static final int LOG_LIMIT = 30;

	/** One line in a container's log: +n or -n of an item, and when. */
	public static final class Change {
		public long at;
		public String b, n;
		public int d;
		Change(long at, String b, String n, int d) { this.at = at; this.b = b; this.n = n; this.d = d; }
	}

	/** An item and how many: b = base item id, n = name (as listings name it), c = count. */
	public static final class Item {
		public String b, n;
		public int c;
		Item(String b, String n, int c) { this.b = b; this.n = n; this.c = c; }
		public String key() { return b + "|" + n; }
	}

	/** One indexed container (a double chest counts once, by its left half). */
	public static final class Box {
		public String dim;
		public int x, y, z;
		public String kind;        // "chest" | "barrel" | "shulker" | "ender"
		public long addedAt;
		public long scannedAt;     // 0 = still waiting for its first scan
		public List<Item> items = new ArrayList<>();
		public List<Change> log = new ArrayList<>();
		/** The server shows it in its own window (a locked container): never opened silently, read when you open it. */
		public boolean locked;
		/** Silent opens that got no answer, and when to try again (StorageScanner; not saved). */
		public transient int failures;
		public transient long retryAt;

		public BlockPos pos() { return new BlockPos(x, y, z); }
		public boolean isEnder() { return "ender".equals(kind); }
		public boolean waiting() { return scannedAt == 0; }
		public int total() { int t = 0; for (Item i : items) t += i.c; return t; }
		public String where() { return isEnder() ? "Ender chest" : x + ", " + y + ", " + z; }
		public String label() {
			if (isEnder()) return "Ender chest";
			String k = "barrel".equals(kind) ? "Barrel" : "shulker".equals(kind) ? "Shulker box" : "Chest";
			return k + " at " + x + ", " + y + ", " + z;
		}
	}

	private static final class Data {
		List<Box> boxes = new ArrayList<>();
		Box ender;
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static Data data = new Data();
	private static String loadedFor = null;
	/** Goes up on every change, so the lookups below know to rebuild. */
	private static int version;
	private static boolean dirty;
	private static long dirtySince;

	// ---- which account + world, and its file ----

	/** "max|Firefly" for the player and world right now, or null when the world isn't known yet. */
	private static String owner() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		ShopWorld w = WorldSelection.get();
		if (w == null) return null;
		return mc.getUser().getName() + "|" + w.label();
	}

	private static Path file(String owner) {
		String safe = owner.replaceAll("[^A-Za-z0-9_|]", "_").replace('|', '-');
		return Config.dir().resolve("storage").resolve(safe.toLowerCase(Locale.ROOT) + ".json");
	}

	/** True when the storage for this account and world is loaded (switching loads the right one). */
	public static boolean ready() {
		String o = owner();
		if (o == null) return false;
		if (!o.equals(loadedFor)) load(o);
		return true;
	}

	private static void load(String o) {
		flush();
		loadedFor = o;
		version++;
		data = new Data();
		Path f = file(o);
		if (!Files.exists(f)) return;
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			Data d = GSON.fromJson(r, Data.class);
			if (d != null) {
				if (d.boxes == null) d.boxes = new ArrayList<>();
				data = d;
			}
		} catch (Exception e) {
			System.err.println("[ShopLogger] Couldn't read " + f + ": " + e);
		}
	}

	/** Something changed: the lookups rebuild, and the file is written within a second (tick). */
	private static void save() {
		version++;
		if (!dirty) dirtySince = System.currentTimeMillis();
		dirty = true;
	}

	/** Every client tick (StorageTracker.tick): writes the file once changes have settled for a second. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince >= 1000L) flush();
	}

	/** The server answered a silent open with its own window: leave this one to the player. */
	public static void markLocked(Box b) {
		b.locked = true;
		save();
	}

	/** A box's state changed outside record/add/remove (StorageScanner gave up on it): redraw its hologram. */
	public static void touch() { version++; }

	private static void flush() {
		if (!dirty) return;
		dirty = false;
		if (loadedFor == null) return;
		Path f = file(loadedFor);
		try {
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(data, w); }
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			System.err.println("[ShopLogger] Couldn't save " + f + ": " + e);
		}
	}

	// ---- the index ----

	public static String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	public static List<Box> boxes() { return ready() ? data.boxes : List.of(); }

	/** Every container, the ender chest last (once it has been opened). */
	public static List<Box> all() {
		if (!ready()) return List.of();
		List<Box> out = new ArrayList<>(data.boxes);
		if (data.ender != null) out.add(data.ender);
		return out;
	}

	public static Box find(String dim, BlockPos pos) {
		if (!ready()) return null;
		for (Box b : data.boxes) if (b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ() && b.dim.equals(dim)) return b;
		return null;
	}

	public static Box add(String dim, BlockPos pos, String kind) {
		if (!ready()) return null;
		Box b = find(dim, pos);
		if (b != null) return b;
		b = new Box();
		b.dim = dim; b.x = pos.getX(); b.y = pos.getY(); b.z = pos.getZ(); b.kind = kind;
		b.addedAt = System.currentTimeMillis();
		data.boxes.add(b);
		save();
		return b;
	}

	public static void remove(Box b) {
		if (!ready()) return;
		if (b.isEnder()) data.ender = null; else data.boxes.remove(b);
		save();
	}

	/** The ender chest's record, made the first time it's opened. */
	public static Box ender() {
		if (!ready()) return null;
		if (data.ender == null) {
			data.ender = new Box();
			data.ender.dim = "";
			data.ender.kind = "ender";
			data.ender.addedAt = System.currentTimeMillis();
			version++;
		}
		return data.ender;
	}

	/**
	 * A fresh read of a container. The first scan just fills it in; later ones
	 * log what changed (+ put in, - taken out). Returns true when anything changed.
	 */
	public static boolean record(Box box, List<Item> now) {
		if (box == null) return false;
		Map<String, Item> before = new LinkedHashMap<>();
		for (Item i : box.items) before.put(i.key(), i);
		Map<String, Item> after = new LinkedHashMap<>();
		for (Item i : now) after.put(i.key(), i);
		boolean first = box.waiting();
		boolean changed = first;
		long t = System.currentTimeMillis();
		if (!first) {
			for (Item a : after.values()) {
				Item b = before.get(a.key());
				int d = a.c - (b == null ? 0 : b.c);
				if (d != 0) { box.log.add(0, new Change(t, a.b, a.n, d)); changed = true; }
			}
			for (Item b : before.values()) {
				if (!after.containsKey(b.key())) { box.log.add(0, new Change(t, b.b, b.n, -b.c)); changed = true; }
			}
			while (box.log.size() > LOG_LIMIT) box.log.remove(box.log.size() - 1);
		}
		box.items = new ArrayList<>(after.values());
		box.items.sort(Comparator.comparingInt((Item i) -> -i.c));
		box.scannedAt = t;
		save();
		return changed;
	}

	/** What's in an open container's own slots (not the player's), shulker boxes counted with their contents. */
	public static List<Item> read(AbstractContainerMenu menu) {
		Minecraft mc = Minecraft.getInstance();
		Map<String, Item> out = new LinkedHashMap<>();
		if (mc.player == null) return List.of();
		for (Slot slot : menu.slots) {
			if (slot.container == mc.player.getInventory()) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			count(out, stack);
			List<ItemStack> inside = ShopEntryFactory.readShulkerContents(stack);
			if (inside != null) for (ItemStack s : inside) count(out, s);
		}
		return new ArrayList<>(out.values());
	}

	private static void count(Map<String, Item> out, ItemStack stack) {
		String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String name = ShopEntryFactory.displayNameFor(stack);
		Item i = out.computeIfAbsent(base + "|" + name, k -> new Item(base, name, 0));
		i.c += stack.getCount();
	}

	// ---- lookups ----

	/** Does a stored item match this one? Rares by name (decorated names too), vanilla items by base item and name. */
	public static boolean matches(Item i, String baseItem, String name) {
		if (baseItem == null) return MatchUtil.isRareNameMatch(i.n, name);
		return i.b.equalsIgnoreCase(baseItem) && i.n.equalsIgnoreCase(name);
	}

	/** How much of an item, and in which containers, keyed like lookupKey; rebuilt after a change. */
	private static final class Held { int count; final List<Box> boxes = new ArrayList<>(); }
	private static Map<String, Held> lookup = new HashMap<>();
	private static List<Object[]> totalsCache = List.of();
	private static final Map<Box, String[]> summaries = new java.util.IdentityHashMap<>();
	private static int builtFor = -1;

	/** Rares by their normalized name (decorated names too), vanilla items by base item and name: the same rule as matches. */
	private static String lookupKey(String baseItem, String name) {
		if (baseItem == null) {
			String n = MatchUtil.rareNormalize(name);
			return n.isEmpty() ? null : "r|" + n;
		}
		return "v|" + baseItem.toLowerCase(Locale.ROOT) + "|" + (name == null ? "" : name.toLowerCase(Locale.ROOT));
	}

	private static void build() {
		if (builtFor == version && loadedFor != null) return;
		builtFor = version;
		summaries.clear();
		Map<String, Held> map = new HashMap<>();
		for (Box b : all()) for (Item i : b.items) {
			for (String k : new String[] { lookupKey(null, i.n), lookupKey(i.b, i.n) }) {
				if (k == null) continue;
				Held h = map.computeIfAbsent(k, x -> new Held());
				h.count += i.c;
				if (h.boxes.isEmpty() || h.boxes.get(h.boxes.size() - 1) != b) h.boxes.add(b);
			}
		}
		lookup = map;
		totalsCache = computeTotals();
	}

	/** How many of an item you have, all containers together (baseItem null for a rare). */
	public static int count(String baseItem, String name) {
		if (!ready()) return 0;
		build();
		String k = lookupKey(baseItem, name);
		Held h = k == null ? null : lookup.get(k);
		return h == null ? 0 : h.count;
	}

	/** The containers holding an item. */
	public static List<Box> holding(String baseItem, String name) {
		if (!ready()) return new ArrayList<>();
		build();
		String k = lookupKey(baseItem, name);
		Held h = k == null ? null : lookup.get(k);
		return h == null ? new ArrayList<>() : new ArrayList<>(h.boxes);
	}

	/** Every item across your storage, most first: [item, containers holding it]. */
	public static List<Object[]> totals() {
		if (!ready()) return List.of();
		build();
		return totalsCache;
	}

	private static List<Object[]> computeTotals() {
		Map<String, Object[]> out = new LinkedHashMap<>();
		for (Box b : all()) for (Item i : b.items) {
			Object[] t = out.computeIfAbsent(i.key(), k -> new Object[] { new Item(i.b, i.n, 0), 0 });
			((Item) t[0]).c += i.c;
			t[1] = (Integer) t[1] + 1;
		}
		List<Object[]> list = new ArrayList<>(out.values());
		list.sort(Comparator.comparingInt((Object[] t) -> -((Item) t[0]).c));
		return list;
	}

	// ---- the hologram's summary ----

	private static final String[] PREFIXES = { "light_blue_", "light_gray_", "white_", "orange_", "magenta_", "yellow_", "lime_", "pink_", "gray_", "cyan_",
			"purple_", "blue_", "brown_", "green_", "red_", "black_", "stripped_", "dark_oak_", "pale_oak_", "oak_", "spruce_", "birch_", "jungle_",
			"acacia_", "mangrove_", "cherry_", "bamboo_", "crimson_", "warped_" };

	/** "minecraft:red_wool" -> "wool", "minecraft:stripped_oak_log" -> "log": colours and woods count as one kind. */
	private static String kindOf(String base) {
		String id = base.contains(":") ? base.substring(base.indexOf(':') + 1) : base;
		boolean again = true;
		while (again) {
			again = false;
			for (String p : PREFIXES) if (id.startsWith(p) && id.length() > p.length()) { id = id.substring(p.length()); again = true; break; }
		}
		return id;
	}

	private static String pretty(String id) {
		String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder b = new StringBuilder();
		for (String w : s.split("_")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		return b.toString();
	}

	/**
	 * Line one and two of a container's hologram. The main kind of item (renamed
	 * ones and colour/wood variants count as their plain block) when it clearly
	 * leads (60% of everything, or 3x the next kind), else "Mixed"; then how many.
	 */
	public static String[] summary(Box b) {
		build();
		return summaries.computeIfAbsent(b, StorageIndex::computeSummary);
	}

	private static String[] computeSummary(Box b) {
		if (b.waiting() && b.locked) return new String[] { "Locked", "open it to read it" };
		if (b.waiting() && b.failures >= StorageScanner.MAX_FAILURES) return new String[] { "Indexed", "open it once to read it" };
		if (b.waiting()) return new String[] { "Indexed", "scans when you're close" };
		int total = b.total();
		if (total == 0) return new String[] { "Empty", "" };
		Map<String, int[]> kinds = new HashMap<>();
		Map<String, java.util.Set<String>> basesOf = new HashMap<>();
		for (Item i : b.items) {
			String k = kindOf(i.b);
			kinds.computeIfAbsent(k, x -> new int[1])[0] += i.c;
			basesOf.computeIfAbsent(k, x -> new java.util.HashSet<>()).add(i.b);
		}
		List<Map.Entry<String, int[]>> sorted = new ArrayList<>(kinds.entrySet());
		sorted.sort(Comparator.comparingInt((Map.Entry<String, int[]> e) -> -e.getValue()[0]));
		int top = sorted.get(0).getValue()[0];
		int next = sorted.size() > 1 ? sorted.get(1).getValue()[0] : 0;
		if (top >= total * 0.6 || top >= next * 3) {
			String k = sorted.get(0).getKey();
			java.util.Set<String> bases = basesOf.get(k);
			String name = bases.size() == 1 ? pretty(bases.iterator().next()) : pretty(k);
			return new String[] { name, String.format(Locale.ROOT, "%,d", top) };
		}
		return new String[] { "Mixed", String.format(Locale.ROOT, "%,d items", total) };
	}
}
