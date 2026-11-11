package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RareIcons;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiPickerScreen;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import com.snailtools.shoplogger.storage.StorageGroups;
import com.snailtools.shoplogger.storage.StorageHighlight;
import com.snailtools.shoplogger.storage.StorageIndex;
import com.snailtools.shoplogger.storage.StorageSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * My storage: everything in the containers you added (X+M), searchable. Click
 * an item to light up the containers holding it; "Containers" lists each one
 * with what's inside and what went in and out lately. Items can be narrowed to
 * rares or vanilla items, a group (a rare category or type, a creative menu
 * tab) or a mark you put on them, and sorted.
 */
public class StorageScreen extends UiScreen {

	private static final String ALL = "All", RARES = "Rares", VANILLA = "Vanilla", MARKED = "Any mark";
	private static final String ANY = "Any";
	private static final String TYPE = "Type: ";
	private static final List<String> ITEM_SORTS = List.of("Most first", "Fewest first", "A to Z", "Z to A", "Recently changed");
	private static final List<String> BOX_SORTS = List.of("Nearest first", "Most items", "Recently read", "Waiting first");
	private static final int FIND_UNREAD = 5;

	// kept while the game runs, so coming back finds the list as you left it
	private static boolean byContainer;
	private static String keepSearch = "";
	private static String show = ALL, group = ANY, itemSort = ITEM_SORTS.get(0), boxSort = BOX_SORTS.get(0);

	private UiField search;
	private ItemListWidget list;
	private int listX, listY, listW, listH;

	/** Containers ticked in the Containers view, for doing something to several at once. */
	private static final java.util.Set<StorageIndex.Box> selected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private UiButton selLight, selIcon, selRemove, selAll, selClear;
	private long removeArmedAt;
	private int barY;

	public StorageScreen(Screen parent) {
		super("My storage", parent, Section.STORAGE);
	}

	@Override
	protected void initContent() {
		StorageGroups.load();
		int x = contentX(), y = contentY(), w = contentW();
		int tw = 80;
		addRenderableWidget(new UiButton(x, y, tw, 18, "Items", UiButton.Style.TAB, () -> { byContainer = false; rebuild(); }).selected(() -> !byContainer));
		addRenderableWidget(new UiButton(x + tw + 4, y, tw + 10, 18, "Containers", UiButton.Style.TAB, () -> { byContainer = true; rebuild(); }).selected(() -> byContainer));
		int sx = x + tw * 2 + 22;
		search = new UiField(font, sx, y - 1, w - (sx - x), 20, byContainer ? "Search containers…" : "Search your storage…", true);
		search.box.setValue(keepSearch);
		search.box.setResponder(s -> { keepSearch = s; refresh(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		setInitialFocus(search.box);

		int cy = y + 24, cx = x;
		int waiting = waitingHere();
		int findW = waiting > 0 ? font.width("Find " + FIND_UNREAD + " unread") + 16 : 0;
		int room = w - (findW > 0 ? findW + 6 : 0);
		if (byContainer) {
			addRenderableWidget(new UiChip<>(cx, cy, Math.min(170, room), 18, "Sort", BOX_SORTS, boxSort, v -> v, v -> { boxSort = v; refresh(); }));
		} else {
			int chipW = Math.max(90, Math.min(160, (room - 12) / 3));
			List<String> shows = showValues();
			if (!shows.contains(show)) show = ALL;
			List<String> groups = groupValues();
			if (!groups.contains(group)) group = ANY;
			addRenderableWidget(new UiChip<>(cx, cy, chipW, 18, "Show", shows, show, v -> v, v -> { show = v; refresh(); }));
			addRenderableWidget(new UiChip<>(cx + chipW + 6, cy, chipW, 18, "Group", groups, group, v -> v, v -> { group = v; refresh(); }));
			addRenderableWidget(new UiChip<>(cx + (chipW + 6) * 2, cy, chipW, 18, "Sort", ITEM_SORTS, itemSort, v -> v, v -> { itemSort = v; refresh(); }));
		}
		if (waiting > 0) {
			addRenderableWidget(new UiButton(x + w - findW, cy, findW, 18, "Find " + FIND_UNREAD + " unread", UiButton.Style.GHOST,
					() -> StorageHighlight.showNearestWaiting(FIND_UNREAD)));
		}

		listX = x - 6;
		listY = y + 60;
		listW = w + 12;
		listH = contentBottom() - listY;
		if (byContainer) {
			// the bar for the ticked containers
			selected.retainAll(StorageIndex.all());
			listH -= 26;
			barY = contentBottom() - 20;
			int bx = x + w;
			selClear = barButton(bx, "Clear", UiButton.Style.GHOST, () -> { selected.clear(); refresh(); });
			bx -= selClear.getWidth() + 4;
			selRemove = barButton(bx, "Click again to remove", UiButton.Style.DANGER, this::removeSelected);
			bx -= selRemove.getWidth() + 4;
			selIcon = barButton(bx, "Icon", UiButton.Style.SECONDARY, this::iconSelected);
			bx -= selIcon.getWidth() + 4;
			selLight = barButton(bx, "Light up", UiButton.Style.PRIMARY, () -> {
				List<StorageIndex.Box> boxes = new ArrayList<>(selected);
				StorageHighlight.show(boxes.size() == 1 ? boxes.get(0).label() : boxes.size() + " containers", boxes);
			});
			bx -= selLight.getWidth() + 4;
			selAll = barButton(bx, "Select all", UiButton.Style.GHOST, this::selectAllShown);
			updateBar();
		}
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);
		refresh();
	}

	private UiButton barButton(int right, String label, UiButton.Style style, Runnable onPress) {
		int bw = font.width(label) + 16;
		UiButton b = new UiButton(right - bw, barY, bw, 20, label, style, onPress);
		addRenderableWidget(b);
		return b;
	}

	/** The bar's buttons follow how many are ticked. */
	private void updateBar() {
		if (selLight == null) return;
		boolean any = !selected.isEmpty();
		for (UiButton b : new UiButton[] { selLight, selIcon, selRemove, selClear }) { b.visible = any; }
		selAll.visible = true;
		boolean armed = System.currentTimeMillis() - removeArmedAt < 3000L;
		selRemove.setMessage(net.minecraft.network.chat.Component.literal(armed ? "Click again to remove" : "Remove " + selected.size()));
	}

	private void selectAllShown() {
		String q = keepSearch.trim().toLowerCase(Locale.ROOT);
		for (StorageIndex.Box b : sortedBoxes()) if (matchesBox(b, q)) selected.add(b);
		refresh();
	}

	private void removeSelected() {
		if (System.currentTimeMillis() - removeArmedAt > 3000L) { removeArmedAt = System.currentTimeMillis(); updateBar(); return; }
		removeArmedAt = 0;
		int n = selected.size();
		for (StorageIndex.Box b : new ArrayList<>(selected)) StorageIndex.remove(b);
		selected.clear();
		com.snailtools.shoplogger.ChatFormat.send(minecraft, com.snailtools.shoplogger.ChatFormat.SUCCESS, "Took " + n + " container" + (n == 1 ? "" : "s") + " out of My storage.");
		rebuild();
	}

	private void iconSelected() {
		List<String> ids = new ArrayList<>();
		for (StorageIndex.Box b : selected) for (StorageIndex.Item i : b.items) if (!ids.contains(i.b) && ids.size() < 40) ids.add(i.b);
		List<StorageIndex.Box> boxes = new ArrayList<>(selected);
		minecraft.setScreenAndShow(new IconPickerScreen(this, boxes.size() + " containers", ids, id -> { for (StorageIndex.Box b : boxes) StorageIndex.setIcon(b, id); }));
	}

	@Override
	public void tick() {
		super.tick();
		if (selRemove != null && removeArmedAt != 0 && System.currentTimeMillis() - removeArmedAt > 3000L) { removeArmedAt = 0; updateBar(); }
	}

	private static boolean matchesBox(StorageIndex.Box b, String q) {
		if (q.isEmpty()) return true;
		String[] s = StorageIndex.summary(b);
		String sub = (s[0] + " " + s[1] + " " + b.plainLabel()).toLowerCase(Locale.ROOT);
		return b.label().toLowerCase(Locale.ROOT).contains(q) || sub.contains(q) || holds(b, q);
	}

	private static int waitingHere() {
		String dim = StorageIndex.dimension();
		int n = 0;
		for (StorageIndex.Box b : StorageIndex.boxes()) if (b.waiting() && b.dim.equals(dim)) n++;
		return n;
	}

	private static List<String> showValues() {
		List<String> out = new ArrayList<>(List.of(ALL, RARES, VANILLA, MARKED));
		for (StorageIndex.Mark m : StorageIndex.Mark.values()) out.add(markLabel(m));
		return out;
	}

	private static String markLabel(StorageIndex.Mark m) { return m.glyph + " " + m.label; }

	/** The groups the items you have are in: rare categories, then rare types, then creative tabs. */
	private static List<String> groupValues() {
		TreeSet<String> categories = new TreeSet<>(), types = new TreeSet<>();
		TreeSet<String> tabs = new TreeSet<>(Comparator.comparingInt(t -> { int i = StorageGroups.tabNames().indexOf(t); return i < 0 ? 99 : i; }));
		for (StorageIndex.Total t : StorageIndex.totals()) {
			if (StorageGroups.rare(t.item()) != null) {
				categories.add(StorageGroups.groupOf(t.item()));
				String type = StorageGroups.typeOf(t.item());
				if (type != null) types.add(TYPE + type);
			} else tabs.add(StorageGroups.groupOf(t.item()));
		}
		List<String> out = new ArrayList<>();
		out.add(ANY);
		out.addAll(categories);
		out.addAll(types);
		out.addAll(tabs);
		return out;
	}

	private void refresh() {
		if (list == null) return;
		list.clearAllEntries();
		String q = keepSearch.trim().toLowerCase(Locale.ROOT);
		if (byContainer) {
			for (StorageIndex.Box b : sortedBoxes()) {
				if (!matchesBox(b, q)) continue;
				String[] s = StorageIndex.summary(b);
				// a named one still says where it is
				String sub = (b.named() ? b.where() + " · " : "") + s[0] + (s[1].isEmpty() ? "" : " · " + s[1]);
				boolean on = selected.contains(b);
				ItemListWidget.ItemEntry e = ItemListWidget.forVanilla(b.label(), iconFor(b), sub, () -> minecraft.setScreenAndShow(new StorageBoxScreen(this, b)));
				e.withTag(on ? "☑" : "☐", on ? Theme.ACCENT : Theme.FAINT, on ? "Selected · click to unselect" : "Select it, to do something to several at once",
						() -> { if (!selected.remove(b)) selected.add(b); refresh(); });
				list.addItemEntry(e);
			}
			updateBar();
			return;
		}
		List<StorageIndex.Total> items = new ArrayList<>();
		for (StorageIndex.Total t : StorageIndex.totals()) {
			StorageIndex.Item i = t.item();
			if (!q.isEmpty() && !i.n.toLowerCase(Locale.ROOT).contains(q) && !i.b.toLowerCase(Locale.ROOT).contains(q)) continue;
			if (!shows(i) || !inGroup(i)) continue;
			items.add(t);
		}
		items.sort(itemOrder());
		for (StorageIndex.Total t : items) {
			StorageIndex.Item i = t.item();
			int boxes = t.boxes();
			String where = StorageGroups.ready() ? " · " + StorageGroups.groupOf(i) : "";
			String sub = String.format(Locale.ROOT, "%,d", i.c) + " in " + boxes + " container" + (boxes == 1 ? "" : "s") + where;
			String texture = RareIcons.isLoaded() ? RareIcons.textureFor(i.n) : null;
			boolean rare = texture != null;
			// exactly this item: two rares that differ only in the symbols around their name stay apart
			Runnable find = () -> StorageHighlight.showExactItem(i.b, i.n);
			Runnable page = () -> minecraft.setScreenAndShow(new ItemDetailScreen(this, i.n, rare ? null : i.b, texture, rare, null));
			ItemListWidget.ItemEntry e = rare ? ItemListWidget.forRare(i.n, sub, texture, find, page) : ItemListWidget.forVanilla(i.n, i.b, sub, find, page);
			StorageIndex.Mark m = StorageIndex.markOf(i);
			e.withTag(m == null ? "☆" : m.glyph, m == null ? Theme.FAINT : m.color,
					m == null ? "Mark this item, to find it again by its mark" : m.label + " · click to change or remove", () -> pickMark(i));
			list.addItemEntry(e);
		}
	}

	private boolean shows(StorageIndex.Item i) {
		if (show.equals(ALL)) return true;
		boolean rare = StorageGroups.rare(i) != null || (RareIcons.isLoaded() && RareIcons.textureFor(i.n) != null);
		if (show.equals(RARES)) return rare;
		if (show.equals(VANILLA)) return !rare;
		StorageIndex.Mark m = StorageIndex.markOf(i);
		if (show.equals(MARKED)) return m != null;
		return m != null && show.equals(markLabel(m));
	}

	private boolean inGroup(StorageIndex.Item i) {
		if (group.equals(ANY)) return true;
		if (group.startsWith(TYPE)) return group.substring(TYPE.length()).equals(StorageGroups.typeOf(i));
		return group.equals(StorageGroups.groupOf(i));
	}

	/** A name as it's sorted: without the symbols around rare names. */
	private static String sortName(String name) {
		return name.replaceAll("[^\\p{L}\\p{N} ']", "").trim().toLowerCase(Locale.ROOT);
	}

	private static Comparator<StorageIndex.Total> itemOrder() {
		Comparator<StorageIndex.Total> az = Comparator.comparing(t -> sortName(t.item().n));
		return switch (itemSort) {
			case "Fewest first" -> Comparator.comparingInt((StorageIndex.Total t) -> t.item().c).thenComparing(az);
			case "A to Z" -> az;
			case "Z to A" -> az.reversed();
			case "Recently changed" -> Comparator.comparingLong((StorageIndex.Total t) -> -t.changedAt()).thenComparing(az);
			default -> Comparator.comparingInt((StorageIndex.Total t) -> -t.item().c).thenComparing(az);
		};
	}

	private List<StorageIndex.Box> sortedBoxes() {
		List<StorageIndex.Box> boxes = new ArrayList<>(StorageIndex.all());
		String dim = StorageIndex.dimension();
		Vec3 eye = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
		Comparator<StorageIndex.Box> nearest = Comparator.comparingDouble(b -> b.isEnder() ? Double.MAX_VALUE : !b.dim.equals(dim) ? Double.MAX_VALUE / 2 : Vec3.atCenterOf(b.pos()).distanceTo(eye));
		boxes.sort(switch (boxSort) {
			case "Most items" -> Comparator.comparingInt((StorageIndex.Box b) -> -b.total()).thenComparing(nearest);
			case "Recently read" -> Comparator.comparingLong((StorageIndex.Box b) -> -b.scannedAt).thenComparing(nearest);
			case "Waiting first" -> Comparator.comparing((StorageIndex.Box b) -> !b.waiting()).thenComparing(nearest);
			default -> nearest;
		});
		return boxes;
	}

	/** The mark picker: none, or one of the marks. */
	private void pickMark(StorageIndex.Item item) {
		List<String> values = new ArrayList<>();
		values.add("");
		for (StorageIndex.Mark m : StorageIndex.Mark.values()) values.add(m.id);
		StorageIndex.Mark current = StorageIndex.markOf(item);
		minecraft.setScreenAndShow(new UiPickerScreen<>(this, "Mark " + item.n, values, current == null ? "" : current.id,
				v -> { StorageIndex.Mark m = StorageIndex.Mark.of(v); return m == null ? "No mark" : markLabel(m); },
				v -> StorageIndex.setMark(item, StorageIndex.Mark.of(v))));
	}

	private static boolean holds(StorageIndex.Box b, String q) {
		for (StorageIndex.Item i : b.items) if (i.n.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	static String iconFor(StorageIndex.Box b) {
		if (b.icon != null) return b.icon;
		return switch (b.kind) {
			case "barrel" -> "minecraft:barrel";
			case "shulker" -> "minecraft:shulker_box";
			case "ender" -> "minecraft:ender_chest";
			default -> "minecraft:chest";
		};
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int y = contentY() + 48;
		List<StorageIndex.Box> all = StorageIndex.all();
		int waiting = 0, items = 0;
		for (StorageIndex.Box b : all) { if (b.waiting()) waiting++; items += b.total(); }
		String head = all.size() + " container" + (all.size() == 1 ? "" : "s") + " · " + String.format(Locale.ROOT, "%,d", items) + " items"
				+ (waiting > 0 ? " · " + waiting + " waiting for their first read (walk past them)" : "");
		g.text(font, Draw.trim(font, head.toUpperCase(Locale.ROOT), contentW() - 4), contentX(), y, Theme.FAINT, false);
		if (byContainer && selLight != null) {
			int right = selAll.getX() - 8;
			String t = selected.isEmpty() ? "Tick ☐ on a row to select several" : selected.size() + " selected";
			g.text(font, Draw.trim(font, t, Math.max(0, right - contentX())), contentX(), barY + 6, selected.isEmpty() ? Theme.FAINT : Theme.TEXT, false);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (!StorageSettings.isEnabled()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "My storage is turned off", "Turn it on in Settings › My storage.", Theme.MUTED);
		} else if (!StorageIndex.ready()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Which world is this?", "Join Firefly or Honeybee (or use /setworld) to see your storage.", Theme.MUTED);
		} else if (StorageIndex.all().isEmpty()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing in My storage yet",
					"Look at a chest, barrel or shulker box and press " + com.snailtools.shoplogger.ModKeys.text("storage") + ". Press it looking at nothing to add everything within 16 blocks.", Theme.MUTED);
		} else if (list != null && list.size() == 0) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing matches that",
					byContainer ? "Try an item name or coordinates." : "Try another search, or set Show and Group back to All and Any.", Theme.MUTED);
		}
	}
}
