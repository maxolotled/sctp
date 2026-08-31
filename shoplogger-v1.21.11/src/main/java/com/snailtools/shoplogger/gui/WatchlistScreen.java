package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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
	private TextFieldWidget searchBox;
	private ItemListWidget list;

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean vanillaLoaded = false;
	private boolean rareLoaded = false;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private static final long SEARCH_DEBOUNCE_MS = 300;

	public WatchlistScreen(Screen parent) {
		super(Text.literal("Watchlist"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new TextFieldWidget(textRenderer, 10, top, width - 20, 20,
				Text.literal("Search to add, clear to view watchlist..."));
		searchBox.setChangedListener(s -> searchChangedAtMillis = System.currentTimeMillis());
		addDrawableChild(searchBox);
		top += 24;

		list = new ItemListWidget(client, width, height - top - 30, top, 22);
		addDrawableChild(list);

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(10, height - 26, 60, 20).build());

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
		WebDataClient.fetchVanillaCatalog().thenAccept(items -> client.execute(() -> {
			vanillaItems = items;
			vanillaLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			client.execute(() -> { vanillaLoaded = true; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchRareCatalog().thenAccept(items -> client.execute(() -> {
			rareItems = items;
			rareLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			client.execute(() -> { rareLoaded = true; loadFailed = true; });
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
		String q = searchBox.getText().trim().toLowerCase(Locale.ROOT);

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
		ChatFormat.send(client, ChatFormat.SUCCESS, "Added " + name + " to your watchlist.");
		refreshList();
	}

	private void removeWatched(String name) {
		WatchlistStore.remove(name);
		ChatFormat.send(client, ChatFormat.NEUTRAL, "Stopped watching " + name + ".");
		refreshList();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
		if (isLoading()) {
			context.drawCenteredTextWithShadow(textRenderer, "Loading...", width / 2, height / 2, 0xFF8FA593);
		} else if (loadFailed) {
			context.drawCenteredTextWithShadow(textRenderer, "Failed to load — check your connection and reopen this screen.", width / 2, height / 2, 0xFFE2A33D);
		} else if (searchBox.getText().isBlank() && WatchlistStore.getAll().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, "Not watching anything yet — search above to add an item.", width / 2, height / 2, 0xFF8FA593);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
