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
import com.snailtools.shoplogger.storage.StorageHighlight;
import com.snailtools.shoplogger.storage.StorageIndex;
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
		// this session: the shop TP took you to, and what you've bought at each shop since (taken off their stock in the plan)
		transient String tpSeller;
		transient java.util.Map<String, Integer> boughtFrom;
		transient String planKey;
		transient BuyPlan plan;
		transient long storageShownAt;  // when TP last lit up My storage for this line instead

		public boolean complete() { return done || got >= need; }
		public int left() { return Math.max(0, need - got); }
	}

	/** One line on the notes page. */
	public static final class Note {
		public String text;
		public boolean done;
		public String icon; // an item id shown next to it, null = none
	}

	private static final class Data {
		List<Need> shopping = new ArrayList<>();
		List<Note> notes = new ArrayList<>();
		boolean onScreen;
		String screenPage = "shopping"; // which page the card on screen shows
		String sort = "Order added";    // the shopping list's order (also on the card)
		int x = 4, y = 40;              // where that card is
		int w = 150, rows = 10;         // and how big: its width, and how many lines of text it shows before scrolling
	}

	public static final String CODE_PREFIX = "SCTP-LIST:";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Pattern POSITION = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");
	public static final int MIN_W = 100, MAX_W = 320, MIN_ROWS = 3, MAX_ROWS = 30;
	private static final int LINE_H = 10;

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
	public static void clearShopping() { shopping().clear(); changed(); }
	public static void clearNotes() { notes().clear(); changed(); }

	// ---- the shopping list's order ----

	public static final List<String> SORTS = List.of("Order added", "Most left", "Fewest left", "A to Z", "Z to A", "Cheapest first", "Priciest first");

	public static String sort() { String s = data().sort; return SORTS.contains(s) ? s : SORTS.get(0); }
	public static void setSort(String s) { data().sort = s; changed(); }
	public static boolean sortsByPrice() { return sort().equals("Cheapest first") || sort().equals("Priciest first"); }

	/** The shopping list in the chosen order; bought lines always at the bottom. */
	public static List<Need> ordered() {
		List<Need> out = new ArrayList<>(shopping());
		java.util.Comparator<Need> az = java.util.Comparator.comparing((Need n) -> n.name.toLowerCase(Locale.ROOT));
		java.util.Comparator<Need> order = switch (sort()) {
			case "Most left" -> java.util.Comparator.comparingInt((Need n) -> -n.left()).thenComparing(az);
			case "Fewest left" -> java.util.Comparator.comparingInt(Need::left).thenComparing(az);
			case "A to Z" -> az;
			case "Z to A" -> az.reversed();
			// lines nobody sells go last either way
			case "Cheapest first" -> java.util.Comparator.comparingDouble((Need n) -> priceOrInf(n, false));
			case "Priciest first" -> java.util.Comparator.comparingDouble((Need n) -> priceOrInf(n, true));
			default -> (a, b) -> 0;
		};
		out.sort(java.util.Comparator.comparing(Need::complete).thenComparing(order));
		return out;
	}

	private static double priceOrInf(Need n, boolean negate) {
		if (toBuy(n) == 0) return 0; // all in My storage: nothing to pay
		BuyPlan p = plan(n);
		if (p == null || p.empty()) return Double.POSITIVE_INFINITY;
		return negate ? -p.diamonds : p.diamonds;
	}
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
	public static int cardWidth() { return Math.max(MIN_W, Math.min(MAX_W, data().w)); }
	public static int cardRows() { return Math.max(MIN_ROWS, Math.min(MAX_ROWS, data().rows)); }
	public static void resizeCard(int w, int rows) {
		data().w = Math.max(MIN_W, Math.min(MAX_W, w));
		data().rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
		changed();
	}

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
				int gained = have - n.lastSeen;
				n.got = Math.min(n.need, n.got + gained);
				if (n.tpSeller != null) {
					if (n.boughtFrom == null) n.boughtFrom = new java.util.HashMap<>();
					n.boughtFrom.merge(n.tpSeller, gained, Integer::sum);
				}
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

	/** One entry on the card, wrapped to the card's width: its text lines, the amount on the right (shopping), its icon (notes). */
	private record CardEntry(List<String> lines, String right, int color, String icon, Need need) {}

	/** Where each entry was drawn last frame (for clicks with chat open): {top, bottom} and its line. */
	private static final List<int[]> hitBoxes = new ArrayList<>();
	private static final List<Need> hitNeeds = new ArrayList<>();
	private static int lastX, lastY, lastH;
	/** How many text lines the card is scrolled down (with chat open, the mouse wheel over it). */
	private static int scrollLines;

	private static List<CardEntry> cardEntries(Font font, int w) {
		List<CardEntry> out = new ArrayList<>();
		if (screenShowsNotes()) {
			for (Note n : notes()) {
				int textW = w - 12 - (n.icon != null ? 11 : 0);
				out.add(new CardEntry(wrap(font, (n.done ? "✓ " : "• ") + n.text, textW), null, n.done ? Theme.FAINT : Theme.TEXT, n.icon, null));
			}
		} else {
			for (Need n : ordered()) {
				if (n.complete()) continue;
				String right = fmt(n.left());
				out.add(new CardEntry(wrap(font, n.name, w - 18 - font.width(right)), right, Theme.TEXT, null, n));
			}
		}
		return out;
	}

	/** Splits text into lines that fit, between words where it can. */
	private static List<String> wrap(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		if (width < 20) { out.add(Draw.trim(font, text, Math.max(1, width))); return out; }
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (font.width(next) <= width) { line.setLength(0); line.append(next); continue; }
			if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
			// a single word wider than the card: cut it into pieces
			while (font.width(word) > width) {
				String piece = font.plainSubstrByWidth(word, width);
				if (piece.isEmpty()) break;
				out.add(piece);
				word = word.substring(piece.length());
			}
			line.append(word);
		}
		if (line.length() > 0 || out.isEmpty()) out.add(line.toString());
		return out;
	}

	/** Nothing to show (an empty list, everything bought, no notes): the card stays away until there is. */
	public static boolean cardEmpty() {
		if (screenShowsNotes()) return notes().isEmpty();
		for (Need n : shopping()) if (!n.complete()) return false;
		return true;
	}

	/** The card on screen (QolHookManager.onHudRender): the shopping list's open lines, or the notes. */
	public static void renderCard(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		hitBoxes.clear();
		hitNeeds.clear();
		lastH = 0;
		if (!onScreen() || mc.player == null || mc.gui.screen() instanceof com.snailtools.shoplogger.gui.NotepadMoveScreen || cardEmpty()) return;
		boolean chat = mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
		int mx = -1, my = -1;
		if (chat) {
			mx = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
			my = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());
		}
		drawCard(g, mc.font, cardX(), cardY(), chat, mx, my);
	}

	/** Also used while you move or resize it (NotepadMoveScreen). Returns its height. */
	public static int drawCard(GuiGraphicsExtractor g, Font font, int x, int y) { return drawCard(g, font, x, y, false, -1, -1); }

	private static int drawCard(GuiGraphicsExtractor g, Font font, int x, int y, boolean chat, int mx, int my) {
		boolean notes = screenShowsNotes();
		int w = cardWidth(), rows = cardRows();
		List<CardEntry> entries = cardEntries(font, w);
		int total = 0;
		for (CardEntry e : entries) total += e.lines().size();
		scrollLines = Math.max(0, Math.min(scrollLines, total - rows));
		int shownLines = Math.min(total, rows);
		boolean above = scrollLines > 0, below = scrollLines + shownLines < total;
		int h = 16 + Math.max(1, shownLines) * LINE_H + (below ? LINE_H : 0);
		Draw.card(g, x, y, w, h, Theme.alpha(Theme.PANEL, 0xCC), Theme.LINE);
		g.text(font, notes ? "Notes" : "Shopping list", x + 6, y + 4, Theme.ACCENT, false);
		if (above) Draw.right(g, font, "▲ " + scrollLines, x + w - 6, y + 4, Theme.FAINT);
		else if (chat && !notes) Draw.right(g, font, "click: TP", x + w - 6, y + 4, Theme.FAINT);
		if (entries.isEmpty()) g.text(font, notes ? "No notes yet" : "All bought ✓", x + 6, y + 15, Theme.MUTED, false);

		int line = 0, ty = y + 15;
		for (CardEntry e : entries) {
			int first = line, last = line + e.lines().size() - 1;
			line += e.lines().size();
			if (last < scrollLines || first >= scrollLines + rows) continue;
			int top = ty + Math.max(0, first - scrollLines) * LINE_H;
			int bottom = ty + (Math.min(last, scrollLines + rows - 1) - scrollLines + 1) * LINE_H;
			if (chat && e.need() != null && mx >= x && mx < x + w && my >= top - 1 && my < bottom - 1) {
				g.fill(x + 2, top - 1, x + w - 2, bottom - 1, Theme.alpha(Theme.ACCENT, 0x33));
			}
			hitBoxes.add(new int[] { top - 1, bottom - 1 });
			hitNeeds.add(e.need());
			for (int i = 0; i < e.lines().size(); i++) {
				int l = first + i;
				if (l < scrollLines || l >= scrollLines + rows) continue;
				int ly = ty + (l - scrollLines) * LINE_H;
				int tx = x + 6;
				if (e.icon() != null) {
					if (i == 0) Draw.item(g, Draw.stackFor(e.icon()), tx, ly - 1, 9);
					tx += 11;
				}
				g.text(font, e.lines().get(i), tx, ly, e.color(), false);
				if (i == 0 && e.right() != null) Draw.right(g, font, e.right(), x + w - 6, ly, Theme.ACCENT);
			}
		}
		if (below) g.text(font, "▼ " + (total - scrollLines - shownLines) + " more" + (chat ? "" : " (open chat to scroll)"), x + 6, y + 15 + shownLines * LINE_H, Theme.FAINT, false);
		lastX = x; lastY = y; lastH = h;
		return h;
	}

	private static boolean overCard(double mx, double my) {
		return lastH > 0 && mx >= lastX && mx < lastX + cardWidth() && my >= lastY && my < lastY + lastH;
	}

	/** A click with chat open (QolHookManager.onMouseEvent): on a shopping line it's TP, like the TP button. True when it was the card's. */
	public static boolean onChatClick(Minecraft mc) {
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return false;
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		if (!overCard(mx, my)) return false;
		for (int i = 0; i < hitBoxes.size(); i++) {
			int[] hb = hitBoxes.get(i);
			if (my >= hb[0] && my < hb[1] && hitNeeds.get(i) != null) {
				Need n = hitNeeds.get(i);
				mc.execute(() -> teleportToCheapest(n));
				return true;
			}
		}
		return true; // on the card, not on a line: don't let it fall through to the chat
	}

	/** The mouse wheel with chat open, over the card: scrolls it. True when it was over the card. */
	public static boolean onChatScroll(Minecraft mc, double amount) {
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return false;
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		if (!overCard(mx, my) || amount == 0) return false;
		scrollLines = Math.max(0, scrollLines - (int) Math.signum(amount));
		return true;
	}

	// ---- the cheapest way to buy it, and teleporting there ----

	private static List<Listing> listingsCache;
	private static long listingsAt;
	private static boolean loading;

	/** The listings loaded in the last minute, or null (then loadListings). */
	public static List<Listing> listings() {
		return listingsCache != null && System.currentTimeMillis() - listingsAt < 60_000L ? listingsCache : null;
	}

	/** Loads the listings (or uses the ones from the last minute), then runs {@code then} on the game thread. */
	public static void loadListings(Runnable then) {
		Minecraft mc = Minecraft.getInstance();
		if (listings() != null) { if (then != null) mc.execute(then); return; }
		if (loading && then == null) return;
		loading = true;
		WebDataClient.fetchListings().thenAccept(all -> mc.execute(() -> {
			loading = false;
			listingsCache = all;
			listingsAt = System.currentTimeMillis();
			if (then != null) then.run();
		})).exceptionally(ex -> { mc.execute(() -> {
			loading = false;
			if (then != null) ChatFormat.send(mc, ChatFormat.ERROR, "Couldn't load the listings. Try again in a moment.");
		}); return null; });
	}

	/**
	 * The cheapest way to buy what's still left of this line in this world, from
	 * the listings already loaded (null when they aren't, or no world is set).
	 * Signs sell fixed amounts, so it's not just the lowest price per item: no
	 * bulk shop for 1 item when a single one is cheaper. See BuyPlan.
	 */
	public static BuyPlan plan(Need n) { return plan(n, toBuy(n)); }

	/** How many of this line are in My storage (all containers, the ender chest too). */
	public static int stored(Need n) {
		return n.complete() ? 0 : StorageIndex.count(n.base, n.name);
	}

	/** What's left that isn't in My storage already: what you'd still have to buy. */
	public static int toBuy(Need n) { return Math.max(0, n.left() - stored(n)); }

	private static BuyPlan plan(Need n, int amount) {
		ShopWorld world = WorldSelection.get();
		List<Listing> all = listingsCache;
		if (world == null || all == null || n.complete()) return null;
		int bought = 0;
		if (n.boughtFrom != null) for (int v : n.boughtFrom.values()) bought += v;
		String key = amount + ":" + listingsAt + ":" + bought + ":" + world.label();
		if (key.equals(n.planKey)) return n.plan;
		List<Listing> sellers = new ArrayList<>();
		for (Listing l : all) {
			if (l.marketplace || !world.label().equalsIgnoreCase(l.world) || l.isDisplay()) continue;
			boolean match = n.base == null ? MatchUtil.isRareNameMatch(l.itemName, n.name)
					: n.base.equalsIgnoreCase(l.baseItem) && n.name.equalsIgnoreCase(l.itemName);
			if (match) sellers.add(l);
		}
		n.plan = BuyPlan.cheapest(sellers, amount, n.boughtFrom);
		n.planKey = key;
		return n.plan;
	}

	/**
	 * First My storage: when some are in your own containers, those light up and
	 * chat says so instead (press TP again within 30 seconds to go buy the rest,
	 * or all of it). Otherwise it works out the cheapest way to buy what's left
	 * (see plan), says it in chat and teleports you to the shop you'd buy the
	 * most at (/shop seller, then the beam). Bought there? Press TP again for the next shop.
	 */
	public static void teleportToCheapest(Need n) {
		Minecraft mc = Minecraft.getInstance();
		ShopWorld world = WorldSelection.get();
		if (n.complete()) { ChatFormat.send(mc, ChatFormat.INFO, "You've got all " + n.need + " " + n.name + " already."); return; }
		int stored = stored(n), left = n.left();
		long now = System.currentTimeMillis();
		if (stored > 0 && now - n.storageShownAt > 30_000L) {
			n.storageShownAt = now;
			n.tpSeller = null; // what you take out of your own chests isn't bought at a shop
			if (n.base != null) StorageHighlight.showExactItem(n.base, n.name);
			if (!StorageHighlight.isShowing()) StorageHighlight.showItem(n.base, n.name);
			mc.setScreenAndShow(null);
			ChatFormat.send(mc, ChatFormat.SUCCESS, stored >= left
					? "You have " + fmt(stored) + " " + n.name + " in My storage already, enough for this line. They're lit up. (Press TP again to go buy them anyway.)"
					: fmt(stored) + " " + n.name + " " + (stored == 1 ? "is" : "are") + " in My storage already, lit up. Press TP again to buy the other " + fmt(left - stored) + ".");
			return;
		}
		n.storageShownAt = 0;
		if (world == null) { ChatFormat.send(mc, ChatFormat.INFO, "Which world is this? Use /setworld first."); return; }
		// the rest after what's in storage; everything when storage alone would do but you're buying anyway
		int amount = stored > 0 && stored < left ? left - stored : left;
		loadListings(() -> {
			BuyPlan p = plan(n, amount);
			if (p == null || p.empty()) { ChatFormat.send(mc, ChatFormat.INFO, "Nobody in " + world.label() + " sells " + n.name + " right now."); return; }
			BuyPlan.Stop go = p.stops.get(0);
			StringBuilder msg = new StringBuilder();
			if (!p.enough()) msg.append("Only ").append(fmt(p.items)).append(" of ").append(fmt(p.wanted)).append(" ").append(n.name).append(" are for sale in ").append(world.label()).append(": ");
			else msg.append("Cheapest for ").append(fmt(p.wanted)).append(" ").append(n.name).append(": ");
			if (p.stops.size() == 1) {
				msg.append(go.listing().seller).append("'s shop, ").append(fmt(go.items())).append(" for ").append(go.costLabel()).append(".");
			} else {
				msg.append(p.stops.size()).append(" shops, about ").append(diamonds(p.diamonds)).append(" together: ");
				for (int i = 0; i < p.stops.size(); i++) {
					BuyPlan.Stop st = p.stops.get(i);
					if (i > 0) msg.append(", ");
					msg.append(st.listing().seller).append(" ").append(fmt(st.items())).append(" for ").append(st.costLabel());
				}
				msg.append(". Taking you to ").append(go.listing().seller).append(" first; press TP again after buying there.");
			}
			ChatFormat.send(mc, ChatFormat.SUCCESS, msg.toString());
			n.tpSeller = go.listing().seller == null ? null : go.listing().seller.toLowerCase(Locale.ROOT);
			BlockPos pos = position(go.listing().position);
			mc.setScreenAndShow(null);
			if (mc.getConnection() != null) mc.getConnection().sendCommand("shop " + go.listing().seller);
			if (pos != null) TeleportHighlight.getInstance().arm(go.listing().world, pos);
		});
	}

	/** "3 dia", or "2.5 dia" under 10. */
	public static String diamonds(double d) {
		if (d < 10 && Math.abs(d - Math.rint(d)) > 0.05) return String.format(Locale.ROOT, "%.1f dia", d);
		return fmt((int) Math.round(d)) + " dia";
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
