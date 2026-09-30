package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game equivalent of the website homepage's listings browser
 * (index.html) — every current listing across the whole marketplace, with
 * the same search/world/type/sort filters, rather than scoped to one item
 * like ItemDetailScreen's table. Also what /search opens (with the query
 * pre-filled).
 */
public class ListingsScreen extends UiScreen {

	private static final String ALL_WORLDS = "All worlds";
	private static final String ANY_TYPE = "Any";
	private static final List<String> SORTS = List.of("Most recent", "Price low-high", "Price high-low", "Stock high-low", "Item A-Z");
	// The real dataset can be ~18,000+ rows — re-filtering/re-sorting that on
	// every keystroke felt slow. Wait for a short pause in typing instead.
	private static final long SEARCH_DEBOUNCE_MS = 300;
	// Building an interactive row per listing is heavy; cap how many exist.
	private static final int MAX_RESULTS = 200;

	private UiField search;
	private UiChip<String> worldFilter;
	private UiChip<String> typeFilter;
	private UiChip<String> sortMode;
	private ListingListWidget list;
	private int listX, listY, listW, listH;

	// kept across rebuilds (resizing the window re-runs initContent)
	private String keepQuery;
	private String keepWorld;
	private String keepType = ANY_TYPE;
	private String keepSort = SORTS.get(0);

	private List<Listing> allListings = List.of();
	private boolean loading = true;
	private boolean loadFailed = false;
	private boolean requested = false;
	private long searchChangedAtMillis = -1;
	private int totalMatches = 0;
	private boolean truncated = false;

	public ListingsScreen(Screen parent, String initialQuery) {
		super("Listings", parent, Section.LISTINGS);
		this.keepQuery = initialQuery;
		ShopWorld detected = WorldSelection.get();
		this.keepWorld = detected != null ? detected.label() : ALL_WORLDS;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();

		search = new UiField(font, x, y, w, 20, "Search item or seller…", true);
		if (keepQuery != null) search.box.setValue(keepQuery);
		search.box.setResponder(s -> { keepQuery = s; searchChangedAtMillis = System.currentTimeMillis(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		y += 26;

		int chipW = Math.min(130, (w - 12 - 90) / 3);
		worldFilter = addRenderableWidget(new UiChip<>(x, y, chipW, 18, "World", List.of(ALL_WORLDS, "Firefly", "Honeybee"), keepWorld,
				v -> v, v -> { keepWorld = v; refreshList(); }));
		typeFilter = addRenderableWidget(new UiChip<>(x + chipW + 6, y, chipW, 18, "Type", List.of(ANY_TYPE, "Bulk", "Bundled", "Single"), keepType,
				v -> v, v -> { keepType = v; refreshList(); }));
		sortMode = addRenderableWidget(new UiChip<>(x + 2 * (chipW + 6), y, chipW, 18, "Sort", SORTS, keepSort,
				v -> v, v -> { keepSort = v; refreshList(); }));
		y += 24;

		listX = x - 6;
		listY = y;
		listW = w + 12;
		listH = contentBottom() - y - 10; // room for the footnote under the list
		list = new ListingListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);

		setInitialFocus(search.box);
		if (!requested) {
			requested = true;
			loadData();
		} else {
			refreshList();
		}
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
		WebDataClient.fetchListings().thenAccept(all -> minecraft.execute(() -> {
			allListings = all;
			loading = false;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> {
				loading = false;
				loadFailed = true;
			});
			return null;
		});
	}

	private boolean isUnfiltered() {
		return search.value().trim().isEmpty()
				&& ALL_WORLDS.equals(worldFilter.getValue())
				&& ANY_TYPE.equals(typeFilter.getValue());
	}

	private void refreshList() {
		if (list == null) return;
		list.clearAllEntries();
		totalMatches = 0;
		truncated = false;

		// Never bulk-render the whole dataset — require a search or filter first.
		if (loading || isUnfiltered()) return;

		String q = search.value().trim().toLowerCase(Locale.ROOT);
		String world = worldFilter.getValue();
		String type = typeFilter.getValue();

		List<Listing> filtered = new ArrayList<>();
		for (Listing l : allListings) {
			if (!q.isEmpty() && !(l.itemName.toLowerCase(Locale.ROOT).contains(q) || l.seller.toLowerCase(Locale.ROOT).contains(q))) continue;
			if (!ALL_WORLDS.equals(world) && !world.equalsIgnoreCase(l.world)) continue;
			if ("Bulk".equals(type) && !l.bulk) continue;
			if ("Bundled".equals(type) && !l.bundled) continue;
			if ("Single".equals(type) && (l.bulk || l.bundled)) continue;
			filtered.add(l);
		}

		String sort = sortMode.getValue();
		filtered.sort((a, b) -> switch (sort) {
			case "Price low-high" -> Double.compare(a.pricePerItemInDiamonds(), b.pricePerItemInDiamonds());
			case "Price high-low" -> Double.compare(b.pricePerItemInDiamonds(), a.pricePerItemInDiamonds());
			case "Stock high-low" -> Integer.compare(b.amount, a.amount);
			case "Item A-Z" -> a.itemName.compareToIgnoreCase(b.itemName);
			default -> parseInstant(b.lastSeen).compareTo(parseInstant(a.lastSeen));
		});

		totalMatches = filtered.size();
		truncated = totalMatches > MAX_RESULTS;
		int limit = Math.min(totalMatches, MAX_RESULTS);
		for (int i = 0; i < limit; i++) {
			Listing l = filtered.get(i);
			list.addListingEntry(ListingListWidget.withItemName(l, seller -> minecraft.setScreenAndShow(new SellerProfileScreen(this, seller, l.world))));
		}
		list.setScrollAmount(0);
	}

	private static Instant parseInstant(String s) {
		try {
			return Instant.parse(s);
		} catch (Exception e) {
			return Instant.EPOCH;
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// result count, right of the filter chips
		String status;
		int color = Theme.MUTED;
		if (loading) status = "Loading…";
		else if (loadFailed) { status = "Couldn't load"; color = Theme.WARN; }
		else if (isUnfiltered()) status = fmtNum(allListings.size()) + " listings";
		else if (truncated) { status = "First " + MAX_RESULTS + " of " + fmtNum(totalMatches); color = Theme.WARN; }
		else status = fmtNum(totalMatches) + " match" + (totalMatches == 1 ? "" : "es");
		Draw.right(g, font, status, contentX() + contentW(), contentY() + 31, color);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (loading || loadFailed) {
			listState(g, listX, listY, listW, listH, loading, loadFailed, false, null, null);
		} else if (isUnfiltered()) {
			Draw.emptyState(g, font, listX, listY, listW, listH,
					"Search or pick a world to browse " + fmtNum(allListings.size()) + " listings",
					"Tip: click a row to see that seller's shop · TP takes you there", Theme.MUTED);
		} else if (totalMatches == 0) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "No listings match that", "Try a shorter search or another world.", Theme.MUTED);
		} else if (truncated) {
			Draw.centered(g, font, "Showing the first " + MAX_RESULTS + " — refine your search to narrow it down", listX + listW / 2, listY + listH + 2, Theme.FAINT);
		}
	}
}
