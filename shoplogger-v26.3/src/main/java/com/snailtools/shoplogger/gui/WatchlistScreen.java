package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchedItem;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

/**
 * Your watchlist. Type to search both catalogs (vanilla and rare) and click
 * a result to add it; clear the search to see (and click to edit) what
 * you're watching — one list, two modes. See WatchlistAlert for what happens
 * once an item is being watched.
 */
public class WatchlistScreen extends UiScreen {

	private static final long SEARCH_DEBOUNCE_MS = 300;
	// maxPrice is stored in diamonds, but shown in diamond blocks everywhere in the UI.
	private static final double DIAMONDS_PER_BLOCK = 9.0;

	private UiField search;
	private ItemListWidget list;
	private int listX, listY, listW, listH;

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean vanillaLoaded = false;
	private boolean rareLoaded = false;
	private boolean loadFailed = false;
	private boolean requested = false;
	private long searchChangedAtMillis = -1;
	// Search text and scroll position survive a trip to a child screen (options, item page).
	private String keepSearch = "";
	private double pendingScroll = -1;

	public WatchlistScreen(Screen parent) {
		super("Watchlist", parent, Section.WATCHLIST);
	}

	@Override
	protected void initContent() {
		double keepScroll = list != null ? list.scrollAmount() : 0;
		int x = contentX(), y = contentY(), w = contentW();

		search = new UiField(font, x, y, w, 20, "Search items to add to your watchlist…", true);
		// set BEFORE the responder, so restoring it doesn't count as a fresh edit
		search.box.setValue(keepSearch);
		search.box.setResponder(s -> { keepSearch = s; searchChangedAtMillis = System.currentTimeMillis(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		y += 26;

		listX = x - 6;
		listY = y + 14;
		listW = w + 12;
		listH = contentBottom() - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);

		pendingScroll = keepScroll;
		setInitialFocus(search.box);
		if (!requested) {
			requested = true;
			loadData();
		} else {
			refreshList(); // catalogs are already in memory from the first visit
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (searchChangedAtMillis > 0 && System.currentTimeMillis() - searchChangedAtMillis >= SEARCH_DEBOUNCE_MS) {
			searchChangedAtMillis = -1;
			pendingScroll = 0;
			refreshList();
		}
	}

	private void loadData() {
		WebDataClient.fetchVanillaCatalog().thenAccept(items -> minecraft.execute(() -> {
			vanillaItems = items;
			vanillaLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { vanillaLoaded = true; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchRareCatalog().thenAccept(items -> minecraft.execute(() -> {
			rareItems = items;
			rareLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { rareLoaded = true; loadFailed = true; });
			return null;
		});
	}

	private boolean isLoading() {
		return !vanillaLoaded || !rareLoaded;
	}

	private boolean searching() {
		return search != null && !search.value().trim().isEmpty();
	}

	private VanillaItem findVanillaItem(String name) {
		for (VanillaItem it : vanillaItems) if (it.name.equalsIgnoreCase(name)) return it;
		return null;
	}

	private RareItem findRareItem(String name) {
		for (RareItem it : rareItems) if (it.name.equalsIgnoreCase(name)) return it;
		return null;
	}

	private void refreshList() {
		if (list == null) return;
		populateList();
		// only once both catalogs are in — an early refresh has nothing to scroll through yet
		if (pendingScroll >= 0 && !isLoading()) {
			list.setScrollAmount(pendingScroll);
			pendingScroll = -1;
		}
	}

	private void populateList() {
		list.clearAllEntries();
		String q = search.value().trim().toLowerCase(Locale.ROOT);

		if (q.isEmpty()) {
			for (WatchedItem watched : WatchlistStore.getAll()) {
				String name = watched.itemName;
				String subtitle = watchedSubtitle(watched);
				VanillaItem vMatch = findVanillaItem(name);
				if (vMatch != null) {
					list.addItemEntry(ItemListWidget.forVanilla(name, vMatch.baseItem, subtitle, () -> openOptions(watched),
							() -> openItemPage(name, vMatch.baseItem, vMatch.texture, false, null)));
					continue;
				}
				RareItem rMatch = findRareItem(name);
				if (rMatch != null) {
					list.addItemEntry(ItemListWidget.forRare(name, subtitle, rMatch.texture, () -> openOptions(watched),
							() -> openItemPage(name, null, rMatch.texture, true, rMatch)));
					continue;
				}
				list.addItemEntry(ItemListWidget.forVanilla(name, null, subtitle, () -> openOptions(watched)));
			}
			return;
		}

		// Searching also surfaces items you already watch — clicking one of those
		// opens its options instead of re-adding it.
		for (VanillaItem it : vanillaItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			WatchedItem existing = WatchlistStore.find(it.name);
			list.addItemEntry(existing != null
					? ItemListWidget.forVanilla(it.name, it.baseItem, watchedSubtitle(existing), () -> openOptions(existing)).withBadge("Watching", Theme.TEAL)
					: ItemListWidget.forVanilla(it.name, it.baseItem, "Vanilla item", () -> addWatched(it.name)).withBadge("+ Add", Theme.ACCENT));
		}
		for (RareItem it : rareItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			WatchedItem existing = WatchlistStore.find(it.name);
			list.addItemEntry(existing != null
					? ItemListWidget.forRare(it.name, watchedSubtitle(existing), it.texture, () -> openOptions(existing)).withBadge("Watching", Theme.TEAL)
					: ItemListWidget.forRare(it.name, it.category, it.texture, () -> addWatched(it.name)).withBadge("+ Add", Theme.ACCENT));
		}
	}

	/** "Max 2 DB · Skips display/no-price" — shown under a watched item's name. */
	private String watchedSubtitle(WatchedItem watched) {
		StringBuilder sb = new StringBuilder();
		if (watched.maxPrice != null) {
			double blocks = watched.maxPrice / DIAMONDS_PER_BLOCK;
			sb.append("Max ").append(blocks == Math.floor(blocks) ? String.valueOf((long) blocks) : String.valueOf(blocks)).append(" DB");
		}
		if (watched.excludeNoPriceOrDisplay) {
			if (sb.length() > 0) sb.append(" · ");
			sb.append("Skips display/no-price");
		}
		return sb.length() > 0 ? sb.toString() : "Any price · click to set a limit";
	}

	private void addWatched(String name) {
		WatchlistStore.add(name);
		ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Added " + name + " to your watchlist.");
		// Go straight to the new item's options instead of making a second trip.
		WatchedItem justAdded = WatchlistStore.find(name);
		if (justAdded != null) openOptions(justAdded);
		else refreshList();
	}

	private void openOptions(WatchedItem watched) {
		minecraft.setScreenAndShow(new WatchedItemOptionsScreen(this, watched));
	}

	/** The [View] button on a watched item — its page (current listings, price history). */
	private void openItemPage(String name, String baseItem, String textureUrl, boolean isRare, RareItem rareData) {
		minecraft.setScreenAndShow(new ItemDetailScreen(this, name, baseItem, textureUrl, isRare, rareData));
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int y = contentY() + 28;
		if (searching()) {
			g.text(font, "SEARCH RESULTS", contentX(), y, Theme.FAINT, false);
			if (list != null) Draw.right(g, font, "Click an item to add it", contentX() + contentW(), y, Theme.FAINT);
		} else {
			int n = WatchlistStore.getAll().size();
			g.text(font, "YOUR WATCHLIST · " + n + " item" + (n == 1 ? "" : "s"), contentX(), y, Theme.FAINT, false);
			if (n > 0) Draw.right(g, font, "Click to edit · View for listings", contentX() + contentW(), y, Theme.FAINT);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (isLoading() || loadFailed) {
			listState(g, listX, listY, listW, listH, isLoading(), loadFailed, false, null, null);
		} else if (!searching() && WatchlistStore.getAll().isEmpty()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "You're not watching anything yet",
					"Search above to add an item. You'll get a chat alert when it's listed.", Theme.MUTED);
		} else if (searching() && list != null && list.size() == 0) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "No items match that", "Try a shorter search.", Theme.MUTED);
		}
	}
}
