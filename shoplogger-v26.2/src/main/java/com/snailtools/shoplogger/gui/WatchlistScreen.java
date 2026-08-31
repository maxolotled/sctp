package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * X menu -> Watchlist. Type to search either catalog (vanilla and rare items
 * both — same two catalogs as the website's Item Library) and click a result
 * to add it; clear the search box to see (and click to remove) your current
 * watchlist instead — one list, two modes, rather than squeezing a second
 * scrollable widget into the same screen. See WatchlistAlert for what
 * actually happens once an item is being watched.
 */
public class WatchlistScreen extends Screen {

	private final Screen parent;
	private EditBox searchBox;
	private ItemListWidget list;

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean vanillaLoaded = false;
	private boolean rareLoaded = false;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private static final long SEARCH_DEBOUNCE_MS = 300;

	public WatchlistScreen(Screen parent) {
		super(Component.literal("Watchlist"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new EditBox(font, 10, top, width - 20, 20,
				Component.literal("Search to add, clear to view watchlist..."));
		searchBox.setResponder(s -> searchChangedAtMillis = System.currentTimeMillis());
		addRenderableWidget(searchBox);
		top += 24;

		list = new ItemListWidget(minecraft, width, height - top - 30, top, 22);
		addRenderableWidget(list);

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(10, height - 26, 60, 20).build());

		loadData();
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

	private VanillaItem findVanillaItem(String name) {
		for (VanillaItem it : vanillaItems) {
			if (it.name.equalsIgnoreCase(name)) return it;
		}
		return null;
	}

	private RareItem findRareItem(String name) {
		for (RareItem it : rareItems) {
			if (it.name.equalsIgnoreCase(name)) return it;
		}
		return null;
	}

	private void refreshList() {
		list.clearAllEntries();
		String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);

		if (q.isEmpty()) {
			for (String watched : WatchlistStore.getAll()) {
				VanillaItem vMatch = findVanillaItem(watched);
				if (vMatch != null) {
					list.addItemEntry(ItemListWidget.forVanilla(watched, vMatch.baseItem, () -> removeWatched(watched)));
					continue;
				}
				RareItem rMatch = findRareItem(watched);
				if (rMatch != null) {
					list.addItemEntry(ItemListWidget.forRare(watched, rMatch.category, rMatch.texture, () -> removeWatched(watched)));
					continue;
				}
				list.addItemEntry(ItemListWidget.forVanilla(watched, null, () -> removeWatched(watched)));
			}
			return;
		}

		for (VanillaItem it : vanillaItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.addItemEntry(ItemListWidget.forVanilla(labelFor(it.name), it.baseItem, () -> addWatched(it.name)));
		}
		for (RareItem it : rareItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.addItemEntry(ItemListWidget.forRare(labelFor(it.name), it.category, it.texture, () -> addWatched(it.name)));
		}
	}

	private String labelFor(String name) {
		return WatchlistStore.isWatching(name) ? name + " (watching)" : name;
	}

	private void addWatched(String name) {
		WatchlistStore.add(name);
		ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Added " + name + " to your watchlist.");
		refreshList();
	}

	private void removeWatched(String name) {
		WatchlistStore.remove(name);
		ChatFormat.send(minecraft, ChatFormat.NEUTRAL, "Stopped watching " + name + ".");
		refreshList();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		if (isLoading()) {
			context.centeredText(font, "Loading...", width / 2, height / 2, 0xFF8FA593);
		} else if (loadFailed) {
			context.centeredText(font, "Failed to load — check your connection and reopen this screen.", width / 2, height / 2, 0xFFE2A33D);
		} else if (searchBox.getValue().isBlank() && WatchlistStore.getAll().isEmpty()) {
			context.centeredText(font, "Not watching anything yet — search above to add an item.", width / 2, height / 2, 0xFF8FA593);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
