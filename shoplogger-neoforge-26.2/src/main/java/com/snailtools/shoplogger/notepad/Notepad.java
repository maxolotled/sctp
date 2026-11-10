package com.snailtools.shoplogger.notepad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ShopEntryFactory;
import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.TeleportHighlight;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The notepad: a shopping list and a page of notes, kept on this computer
 * (config/sctp/notepad.json). A shopping line counts down by itself as the item
 * arrives in your inventory (what you gain counts, not what you already had),
 * and can teleport you to the cheapest shop selling it. Lines can be typed in,
 * pasted as a code from sctp.nl/list, or taken from Litematica's material list.
 * It can also stay on screen as a small card (Notepad › Show on screen).
 */
public final class Notepad {

	private Notepad() {}

	/** One thing to buy. base is the item id ("minecraft:glass_pane"), null for a rare (matched by name). */
	public static final class Need {
		public String name, base;
		public int need, got;
		public boolean done;           // ticked off by hand
		transient int lastSeen = -1;   // how many were in your inventory at the last check, -1 = not checked yet

		public boolean complete() { return done || got >= need; }
		public int left() { return Math.max(0, need - got); }
	}

	/** One line on the notes page. */
	public static final class Note {
		public String text;
		public boolean done;
	}

	private static final class Data {
		List<Need> shopping = new ArrayList<>();
		List<Note> notes = new ArrayList<>();
		boolean onScreen;
		String screenPage = "shopping"; // which page the card on screen shows
		int x = 4, y = 40;              // where that card is
	}

	public static final String CODE_PREFIX = "SCTP-LIST:";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Pattern POSITION = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");
	private static final int CARD_W = 150, CARD_ROWS = 10;

	private static Data data;
	private static boolean dirty;
	private static long dirtySince;
	private static int ticks;

	private static Path file() { return Config.dir().resolve("notepad.json"); }

	private static Data data() {
		if (data != null) return data;
		data = new Data();
		Path f = file();
		if (Files.exists(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				Data d = GSON.fromJson(r, Data.class);
				if (d != null) {
					if (d.shopping == null) d.shopping = new ArrayList<>();
					if (d.notes == null) d.notes = new ArrayList<>();
					data = d;
				}
			} catch (Exception e) {
				System.err.println("[ShopLogger] Couldn't read " + f + ": " + e);
			}
		}
		return data;
	}

	/** Something changed: written within a second (tick). */
	public static void changed() {
		if (!dirty) dirtySince = System.currentTimeMillis();
		dirty = true;
	}

	private static void flush() {
		dirty = false;
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling("notepad.json.tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(data(), w); }
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			System.err.println("[ShopLogger] Couldn't save " + f + ": " + e);
		}
	}

	// ---- the lists ----

	public static List<Need> shopping() { return data().shopping; }
	public static List<Note> notes() { return data().notes; }

	/** Adds something to buy; the same item already on the list gets the amount added instead. */
	public static void add(String name, String base, int amount) {
		if (name == null || name.isBlank() || amount <= 0) return;
		for (Need n : shopping()) {
			if (same(n, name, base)) {
				n.need += amount;
				n.done = false;
				changed();
				return;
			}
		}
		Need n = new Need();
		n.name = name.trim();
		n.base = base;
		n.need = amount;
		shopping().add(n);
		changed();
	}

	private static boolean same(Need n, String name, String base) {
		if (n.base == null || base == null) return n.base == null && base == null && MatchUtil.rareNormalize(n.name).equals(MatchUtil.rareNormalize(name));
		return n.base.equals(base) && n.name.equalsIgnoreCase(name);
	}

	public static void remove(Need n) { shopping().remove(n); changed(); }
	public static void clearDone() { shopping().removeIf(Need::complete); changed(); }

	public static void addNote(String text) {
		if (text == null || text.isBlank()) return;
		Note n = new Note();
		n.text = text.trim();
		notes().add(n);
		changed();
	}

	public static void removeNote(Note n) { notes().remove(n); changed(); }

	// ---- on screen ----

	public static boolean onScreen() { return data().onScreen; }
	public static void setOnScreen(boolean v) { data().onScreen = v; changed(); }
	public static boolean screenShowsNotes() { return "notes".equals(data().screenPage); }
	public static void setScreenShowsNotes(boolean notes) { data().screenPage = notes ? "notes" : "shopping"; changed(); }
	public static int cardX() { return data().x; }
	public static int cardY() { return data().y; }
	public static void moveCard(int x, int y) { data().x = Math.max(0, x); data().y = Math.max(0, y); changed(); }
	public static int cardWidth() { return CARD_W; }

	// ---- every tick: what arrived in your inventory ----

	public static void tick(Minecraft mc) {
		if (dirty && System.currentTimeMillis() - dirtySince >= 1000L) flush();
		if (mc.player == null) {
			// out of the world: whatever's in your inventory on the way back in counts as already there
			if (data != null) for (Need n : data.shopping) n.lastSeen = -1;
			return;
		}
		if (++ticks % 10 != 0 || data().shopping.isEmpty()) return;
		Inventory inv = mc.player.getInventory();
		for (Need n : data.shopping) {
			if (n.complete()) { n.lastSeen = -1; continue; }
			int have = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty() && matches(n, s)) have += s.getCount();
			}
			if (n.lastSeen >= 0 && have > n.lastSeen) {
				n.got = Math.min(n.need, n.got + have - n.lastSeen);
				changed();
				if (n.complete()) ChatFormat.send(mc, ChatFormat.SUCCESS, "Shopping list: got all " + n.need + " " + n.name + ".");
			}
			n.lastSeen = have;
		}
	}

	private static boolean matches(Need n, ItemStack s) {
		String name = ShopEntryFactory.displayNameFor(s);
		if (n.base == null) return MatchUtil.isRareNameMatch(name, n.name);
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(n.base) && name.equalsIgnoreCase(n.name);
	}

	/** The card on screen (QolHookManager.onHudRender): the shopping list's open lines, or the notes. */
	public static void renderCard(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!onScreen() || mc.player == null || mc.gui.screen() instanceof com.snailtools.shoplogger.gui.NotepadMoveScreen) return;
		drawCard(g, mc.font, cardX(), cardY());
	}

	/** Also used while you drag it into place (NotepadMoveScreen). Returns its height. */
	public static int drawCard(GuiGraphicsExtractor g, Font font, int x, int y) {
		boolean notes = screenShowsNotes();
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		if (notes) {
			for (Note n : notes()) { lines.add((n.done ? "✓ " : "• ") + n.text); colors.add(n.done ? Theme.FAINT : Theme.TEXT); }
		} else {
			for (Need n : shopping()) {
				if (n.complete()) continue;
				lines.add(n.name + "  " + n.left());
				colors.add(Theme.TEXT);
			}
		}
		int shown = Math.min(lines.size(), CARD_ROWS);
		int h = 16 + Math.max(1, shown) * 10 + (lines.size() > shown ? 10 : 0);
		Draw.card(g, x, y, CARD_W, h, Theme.alpha(Theme.PANEL, 0xCC), Theme.LINE);
		g.text(font, notes ? "Notes" : "Shopping list", x + 6, y + 4, Theme.ACCENT, false);
		if (lines.isEmpty()) g.text(font, notes ? "No notes yet" : "All bought ✓", x + 6, y + 15, Theme.MUTED, false);
		for (int i = 0; i < shown; i++) {
			String line = lines.get(i);
			if (!notes) {
				// the name on the left, how many are left on the right
				int cut = line.lastIndexOf("  ");
				String left = line.substring(cut + 2);
				Draw.right(g, font, left, x + CARD_W - 6, y + 15 + i * 10, Theme.ACCENT);
				g.text(font, Draw.trim(font, line.substring(0, cut), CARD_W - 18 - font.width(left)), x + 6, y + 15 + i * 10, colors.get(i), false);
			} else {
				g.text(font, Draw.trim(font, line, CARD_W - 12), x + 6, y + 15 + i * 10, colors.get(i), false);
			}
		}
		if (lines.size() > shown) g.text(font, "+" + (lines.size() - shown) + " more", x + 6, y + 15 + shown * 10, Theme.FAINT, false);
		return h;
	}

	// ---- teleport to the cheapest shop ----

	private static List<Listing> listingsCache;
	private static long listingsAt;

	/** Finds the cheapest shop in this world selling the item and teleports you there (/shop seller, then the beam). */
	public static void teleportToCheapest(Need n) {
		Minecraft mc = Minecraft.getInstance();
		ShopWorld world = WorldSelection.get();
		if (world == null) { ChatFormat.send(mc, ChatFormat.INFO, "Which world is this? Use /setworld first."); return; }
		java.util.concurrent.CompletableFuture<List<Listing>> f = listingsCache != null && System.currentTimeMillis() - listingsAt < 60_000L
				? java.util.concurrent.CompletableFuture.completedFuture(listingsCache)
				: WebDataClient.fetchListings();
		f.thenAccept(all -> mc.execute(() -> {
			listingsCache = all;
			listingsAt = System.currentTimeMillis();
			Listing best = null;
			for (Listing l : all) {
				if (l.marketplace || !world.label().equalsIgnoreCase(l.world) || "display".equalsIgnoreCase(l.currency)) continue;
				boolean match = n.base == null ? MatchUtil.isRareNameMatch(l.itemName, n.name)
						: n.base.equalsIgnoreCase(l.baseItem) && n.name.equalsIgnoreCase(l.itemName);
				if (!match || l.amount <= 0) continue;
				if (best == null || l.pricePerItemInDiamonds() < best.pricePerItemInDiamonds()) best = l;
			}
			if (best == null) { ChatFormat.send(mc, ChatFormat.INFO, "Nobody in " + world.label() + " sells " + n.name + " right now."); return; }
			BlockPos pos = position(best.position);
			ChatFormat.send(mc, ChatFormat.SUCCESS, "Cheapest " + n.name + ": " + best.priceLabel + " at " + best.seller + "'s shop.");
			mc.setScreenAndShow(null);
			if (mc.getConnection() != null) mc.getConnection().sendCommand("shop " + best.seller);
			if (pos != null) TeleportHighlight.getInstance().arm(best.world, pos);
		})).exceptionally(ex -> { mc.execute(() -> ChatFormat.send(mc, ChatFormat.ERROR, "Couldn't load the listings. Try again in a moment.")); return null; });
	}

	private static BlockPos position(String p) {
		if (p == null) return null;
		Matcher m = POSITION.matcher(p.trim());
		if (!m.matches()) return null;
		try { return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))); }
		catch (NumberFormatException e) { return null; }
	}

	// ---- imports ----

	private record CodeItem(String n, String b, int c) {}
	private record Code(int v, List<CodeItem> items) {}

	/** Adds the items from a sctp.nl/list code ("SCTP-LIST:..."). Returns how many lines, or -1 when it isn't a code. */
	public static int importCode(String text) {
		if (text == null) return -1;
		String t = text.trim();
		int at = t.indexOf(CODE_PREFIX);
		if (at < 0) return -1;
		try {
			String json = new String(Base64.getUrlDecoder().decode(t.substring(at + CODE_PREFIX.length()).trim()), StandardCharsets.UTF_8);
			Code code = GSON.fromJson(json, Code.class);
			if (code == null || code.items() == null) return -1;
			int n = 0;
			for (CodeItem it : code.items()) {
				if (it.n() == null || it.c() <= 0) continue;
				add(it.n(), it.b() == null || it.b().isEmpty() ? null : it.b(), it.c());
				n++;
			}
			return n;
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Adds what Litematica's open material list still misses (its "Missing"
	 * column), read through reflection: Litematica is optional. Returns a line
	 * for chat saying what happened.
	 */
	public static String importLitematica() {
		Class<?> dm;
		try { dm = Class.forName("fi.dy.masa.litematica.data.DataManager"); }
		catch (ClassNotFoundException e) { return "Litematica isn't installed."; }
		try {
			Object list = dm.getMethod("getMaterialList").invoke(null);
			if (list == null) return "Open the schematic's material list in Litematica first (Material List in its placement menu), then try again.";
			Object entries = list.getClass().getMethod("getMaterialsAll").invoke(list);
			int added = 0;
			for (Object e : (List<?>) entries) {
				ItemStack stack = (ItemStack) e.getClass().getMethod("getStack").invoke(e);
				long amount = number(e, "getCountMissing");
				if (amount < 0) amount = number(e, "getCountTotal");
				if (stack == null || stack.isEmpty() || amount <= 0) continue;
				add(ShopEntryFactory.displayNameFor(stack), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (int) Math.min(Integer.MAX_VALUE, amount));
				added++;
			}
			return added == 0 ? "Litematica's material list has nothing missing." : "Added " + added + " item" + (added == 1 ? "" : "s") + " from Litematica's material list.";
		} catch (Exception e) {
			return "Couldn't read Litematica's material list (" + e.getClass().getSimpleName() + ").";
		}
	}

	private static long number(Object o, String method) {
		try { return ((Number) o.getClass().getMethod(method).invoke(o)).longValue(); }
		catch (Exception e) { return -1; }
	}

	/** "1,234" style, for the screens. */
	public static String fmt(int n) { return String.format(Locale.ROOT, "%,d", n); }
}
