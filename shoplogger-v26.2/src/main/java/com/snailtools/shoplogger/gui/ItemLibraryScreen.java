package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-game equivalent of items/index.html — browse either catalog (tabs at
 * the top switch between them), search, and for rare items filter by
 * category and release date. The website's other filters (obtained from,
 * dyeable, glow/particles, type/slot) are folded into the search box: most of those
 * fields have far too many values (obtainedFrom alone has 100+) for a
 * click-to-cycle filter.
 */
public class ItemLibraryScreen extends UiScreen {

	public enum Catalog { VANILLA, RARE }

	private static final String ANY = "Any";
	private static final String ALL = "All";
	private static final long SEARCH_DEBOUNCE_MS = 300;
	private static final String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
	/** "Apr 2025", "Sept 2025", "Jun 2026 - Present"... one match per month mentioned. */
	private static final Pattern MONTH_YEAR = Pattern.compile("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(20\\d\\d)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern YEAR = Pattern.compile("\\b(20\\d\\d)\\b");

	private final Catalog catalog;

	private UiField search;
	private ItemListWidget list;
	private int listX, listY, listW, listH;

	private String keepQuery = "";
	private String selectedCategory = ANY;
	private String selectedRelease = ALL;
	private List<String> categoryValues = List.of(ANY);
	private List<String> releaseValues = List.of(ALL);
	/** Per rare: every filter value its release date counts for, e.g. {"2025", "Apr 2025", "2026", "Jan 2026"}. */
	private final Map<RareItem, Set<String>> releaseTags = new IdentityHashMap<>();

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean loading = true;
	private boolean loadFailed = false;
	private boolean requested = false;
	private long searchChangedAtMillis = -1;
	// scroll position to put back when returning from an item page (0 = top)
	private double restoreScroll = 0;

	public ItemLibraryScreen(Screen parent, Catalog catalog) {
		super(catalog == Catalog.VANILLA ? "Vanilla items" : "Rare items", parent, catalog == Catalog.VANILLA ? Section.VANILLA : Section.RARES);
		this.catalog = catalog;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();

		// catalog tabs
		addRenderableWidget(new UiButton(x, y, 84, 18, "Vanilla items", UiButton.Style.TAB, () -> switchTo(Catalog.VANILLA))
				.selected(() -> catalog == Catalog.VANILLA));
		addRenderableWidget(new UiButton(x + 88, y, 84, 18, "Rare items", UiButton.Style.TAB, () -> switchTo(Catalog.RARE))
				.selected(() -> catalog == Catalog.RARE));
		y += 24;

		search = new UiField(font, x, y, w, 20, catalog == Catalog.RARE ? "Search name, effect, source, type…" : "Search items…", true);
		search.box.setValue(keepQuery);
		search.box.setResponder(s -> { keepQuery = s; searchChangedAtMillis = System.currentTimeMillis(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		y += 26;

		if (catalog == Catalog.RARE) {
			int chipW = Math.min(170, (w - 6) / 2);
			addRenderableWidget(new UiChip<>(x, y, chipW, 18, "Category", categoryValues, selectedCategory, v -> v,
					v -> { selectedCategory = v; refreshList(); }));
			addRenderableWidget(new UiChip<>(x + chipW + 6, y, chipW, 18, "Released", releaseValues, selectedRelease, v -> v,
					v -> { selectedRelease = v; refreshList(); }));
			y += 24;
		}

		listX = x - 6;
		listY = y;
		listW = w + 12;
		listH = contentBottom() - y;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);

		setInitialFocus(search.box);
		if (!requested) {
			requested = true;
			loadData();
		} else {
			refreshList();
		}
	}

	private void switchTo(Catalog target) {
		if (target != catalog) minecraft.setScreenAndShow(new ItemLibraryScreen(parent, target));
	}

	@Override
	public void tick() {
		super.tick();
		if (searchChangedAtMillis > 0 && System.currentTimeMillis() - searchChangedAtMillis >= SEARCH_DEBOUNCE_MS) {
			searchChangedAtMillis = -1;
			refreshList();
		}
	}

	private void loadData() {
		if (catalog == Catalog.VANILLA) {
			WebDataClient.fetchVanillaCatalog().thenAccept(items -> minecraft.execute(() -> {
				vanillaItems = items;
				loading = false;
				refreshList();
			})).exceptionally(ex -> {
				minecraft.execute(() -> { loading = false; loadFailed = true; });
				return null;
			});
		} else {
			WebDataClient.fetchRareCatalog().thenAccept(items -> minecraft.execute(() -> {
				rareItems = items;
				loading = false;
				populateRareFilterValues();
				rebuild(); // the filter chips need the real category/release values
			})).exceptionally(ex -> {
				minecraft.execute(() -> { loading = false; loadFailed = true; });
				return null;
			});
		}
	}

	private void populateRareFilterValues() {
		TreeSet<String> categories = new TreeSet<>();
		TreeSet<Integer> years = new TreeSet<>();
		TreeSet<Integer> months = new TreeSet<>(); // year * 12 + month index, so it sorts chronologically
		releaseTags.clear();
		for (RareItem it : rareItems) {
			if (it.category != null) categories.add(it.category);
			Set<String> tags = new HashSet<>();
			if (it.releaseDate != null) {
				// "Summer 2026" or a bare "2025" only count for the year
				Matcher y = YEAR.matcher(it.releaseDate);
				while (y.find()) {
					int year = Integer.parseInt(y.group(1));
					years.add(year);
					tags.add(String.valueOf(year));
				}
				Matcher m = MONTH_YEAR.matcher(it.releaseDate);
				while (m.find()) {
					int month = monthIndex(m.group(1));
					int year = Integer.parseInt(m.group(2));
					months.add(year * 12 + month);
					tags.add(monthLabel(year * 12 + month));
				}
			}
			releaseTags.put(it, tags);
		}
		List<String> c = new ArrayList<>();
		c.add(ANY);
		c.addAll(categories);
		categoryValues = c;
		// All, then the years, then every month oldest first: All > 2025 > 2026 > Jan 2025 > ... > Sep 2026
		List<String> r = new ArrayList<>();
		r.add(ALL);
		for (int year : years) r.add(String.valueOf(year));
		for (int key : months) r.add(monthLabel(key));
		releaseValues = r;
	}

	private static int monthIndex(String name) {
		String prefix = name.substring(0, 3).toLowerCase(Locale.ROOT);
		for (int i = 0; i < MONTHS.length; i++) {
			if (MONTHS[i].toLowerCase(Locale.ROOT).equals(prefix)) return i;
		}
		return 0;
	}

	private static String monthLabel(int key) {
		return MONTHS[key % 12] + " " + (key / 12);
	}

	private void refreshList() {
		if (list == null) return;
		list.clearAllEntries();
		String q = search.value().trim().toLowerCase(Locale.ROOT);

		if (catalog == Catalog.VANILLA) {
			for (VanillaItem it : vanillaItems) {
				if (!q.isEmpty() && !it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
				list.addItemEntry(ItemListWidget.forVanilla(it.name, it.baseItem, () ->
						openDetail(new ItemDetailScreen(this, it.name, it.baseItem, it.texture, false, null))));
			}
		} else {
			for (RareItem it : rareItems) {
				if (!selectedCategory.equals(ANY) && !selectedCategory.equals(it.category)) continue;
				if (!selectedRelease.equals(ALL) && !releaseTags.getOrDefault(it, Set.of()).contains(selectedRelease)) continue;
				if (!q.isEmpty() && !matchesSearch(it, q)) continue;
				RareItem captured = it;
				String sub = (it.category == null ? "Rare" : it.category) + (it.typeSlot != null && !it.typeSlot.isEmpty() ? " · " + it.typeSlot : "");
				list.addItemEntry(ItemListWidget.forRare(it.name, sub, it.texture, () ->
						openDetail(new ItemDetailScreen(this, captured.name, null, captured.texture, true, captured))));
			}
		}
		list.setScrollAmount(restoreScroll);
		restoreScroll = 0;
	}

	/** Opens an item page, remembering where the list was scrolled to for when you come back. */
	private void openDetail(ItemDetailScreen detail) {
		restoreScroll = list != null ? list.scrollAmount() : 0;
		minecraft.setScreenAndShow(detail);
	}

	private static boolean matchesSearch(RareItem it, String q) {
		if (it.name != null && it.name.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.effect != null && it.effect.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.obtainedFrom != null && it.obtainedFrom.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.typeSlot != null && it.typeSlot.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.glowParticles != null && it.glowParticles.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.releaseDate != null && it.releaseDate.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int total = catalog == Catalog.VANILLA ? vanillaItems.size() : rareItems.size();
		String status = loading ? "Loading…" : list == null ? "" : list.size() == total
				? fmtNum(total) + " items" : fmtNum(list.size()) + " of " + fmtNum(total);
		Draw.right(g, font, status, contentX() + contentW(), contentY() + 5, Theme.MUTED);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		listState(g, listX, listY, listW, listH, loading, loadFailed, !loading && list != null && list.size() == 0,
				"No items match that", "Try a shorter search or clear the filters.");
	}
}
