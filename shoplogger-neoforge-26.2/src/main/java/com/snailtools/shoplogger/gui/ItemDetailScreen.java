package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.gui.data.HistoryMerge;
import com.snailtools.shoplogger.gui.data.HistoryPoint;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import com.snailtools.shoplogger.gui.widget.PriceChartWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game equivalent of a website item page: a header with the icon and key
 * facts, then tabs for the current listings (filterable by world), the
 * price history charts, and (rare items) the full details. Listings and
 * history are fetched fresh every time the page opens, like the website.
 */
public class ItemDetailScreen extends UiScreen {

	private enum Tab { LISTINGS, HISTORY, DETAILS }

	private static final String ALL_WORLDS = "All worlds";

	private final String name;
	private final String baseItem; // null for rare items
	private final String textureUrl; // rare items; vanilla items render their real ItemStack instead
	private final boolean isRare;
	private final RareItem rareData; // null for vanilla items
	private final ItemStack icon;

	private Tab tab = Tab.LISTINGS;
	private String worldFilter;
	private List<Listing> allMatches = List.of();
	private ListingListWidget listingList;
	private int bodyY;

	private List<HistoryPoint> history = List.of();
	private boolean loading = true;
	private boolean loadFailed = false;
	private boolean historyLoading = true;
	private boolean requested = false;

	public ItemDetailScreen(Screen parent, String name, String baseItem, String textureUrl, boolean isRare, RareItem rareData) {
		super(name, parent, isRare ? Section.RARES : Section.VANILLA);
		this.name = name;
		this.baseItem = baseItem;
		this.textureUrl = textureUrl;
		this.isRare = isRare;
		this.rareData = rareData;
		this.icon = isRare ? ItemStack.EMPTY : Draw.stackFor(baseItem);
		ShopWorld detected = WorldSelection.get();
		this.worldFilter = detected != null ? detected.label() : ALL_WORLDS;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();

		// world filter lives in the header card, top-right
		int chipW = Math.min(140, w / 3);
		addRenderableWidget(new UiChip<>(x + w - chipW - 8, y + 11, chipW, 18, "World", List.of(ALL_WORLDS, "Firefly", "Honeybee"), worldFilter,
				v -> v, v -> { worldFilter = v; rebuild(); }));

		// tabs
		int ty = y + 46;
		int tx = x;
		tx += tab(tx, ty, Tab.LISTINGS, "Listings") + 4;
		tx += tab(tx, ty, Tab.HISTORY, "Price history") + 4;
		if (isRare && rareData != null) tab(tx, ty, Tab.DETAILS, "Details");
		bodyY = ty + 24;

		if (tab == Tab.LISTINGS) {
			listingList = new ListingListWidget(minecraft, x - 6, bodyY, w + 12, contentBottom() - bodyY);
			addRenderableWidget(listingList);
			refreshListingList();
		} else {
			listingList = null;
		}

		if (!requested) {
			requested = true;
			loadData();
		}
	}

	private int tab(int x, int y, Tab t, String label) {
		String text = t == Tab.LISTINGS && !loading ? label + " (" + countForWorld() + ")" : label;
		int w = font.width(text) + 16;
		addRenderableWidget(new UiButton(x, y, w, 18, text, UiButton.Style.TAB, () -> { tab = t; rebuild(); })
				.selected(() -> tab == t));
		return w;
	}

	private int countForWorld() {
		int n = 0;
		for (Listing l : allMatches) if (ALL_WORLDS.equals(worldFilter) || worldFilter.equalsIgnoreCase(l.world)) n++;
		return n;
	}

	private void loadData() {
		WebDataClient.fetchListings().thenAccept(all -> minecraft.execute(() -> {
			List<Listing> matched = new ArrayList<>();
			for (Listing l : all) {
				if (isRare) {
					if (MatchUtil.isRareNameMatch(l.itemName, name)) matched.add(l);
				} else {
					if (baseItem != null && baseItem.equalsIgnoreCase(l.baseItem) && name.equalsIgnoreCase(l.itemName)) matched.add(l);
				}
			}
			allMatches = matched;
			loading = false;
			rebuild(); // updates the Listings tab count
			loadHistory(matched);
		})).exceptionally(ex -> {
			minecraft.execute(() -> { loading = false; loadFailed = true; historyLoading = false; });
			return null;
		});
	}

	private void loadHistory(List<Listing> matched) {
		Set<String> keys = new LinkedHashSet<>();
		if (isRare) {
			for (Listing l : matched) keys.add(MatchUtil.vanillaItemKey(l.baseItem, l.itemName));
			if (keys.isEmpty()) {
				historyLoading = false;
				return;
			}
		} else {
			keys.add(MatchUtil.vanillaItemKey(baseItem, name));
		}

		List<java.util.concurrent.CompletableFuture<List<HistoryPoint>>> futures = new ArrayList<>();
		for (String key : keys) futures.add(WebDataClient.fetchItemHistory(key));

		java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
				.thenAccept(v -> minecraft.execute(() -> {
					List<List<HistoryPoint>> results = new ArrayList<>();
					for (var f : futures) {
						try {
							results.add(f.join());
						} catch (Exception ignored) {
							results.add(List.of());
						}
					}
					history = HistoryMerge.merge(results);
					historyLoading = false;
				}))
				.exceptionally(ex -> {
					minecraft.execute(() -> historyLoading = false);
					return null;
				});
	}

	private void refreshListingList() {
		if (listingList == null) return;
		listingList.clearAllEntries();
		for (Listing l : allMatches) {
			if (!ALL_WORLDS.equals(worldFilter) && !worldFilter.equalsIgnoreCase(l.world)) continue;
			listingList.addListingEntry(ListingListWidget.of(l, seller -> minecraft.setScreenAndShow(new SellerProfileScreen(this, seller, l.world))));
		}
	}

	// ---- drawing ---------------------------------------------------------

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY(), w = contentW();

		// header card
		Draw.card(g, x, y, w, 40, Theme.PANEL, Theme.LINE_SOFT);
		Draw.round(g, x + 4, y + 4, 32, 32, Theme.SLOT);
		if (!icon.isEmpty()) Draw.item(g, icon, x + 4, y + 4, 32);
		else Draw.remoteTexture(g, textureUrl, x + 4, y + 4, 32);

		int chipW = Math.min(140, w / 3);
		int textW = w - 48 - chipW - 16;
		Draw.scaled(g, font, Draw.trim(font, name, (int) (textW / 1.25f)), x + 44, y + 7, Theme.TEXT, 1.25f, true);
		int sy = y + 24;
		int sx = x + 44;
		if (isRare && rareData != null) {
			sx += Draw.pill(g, font, safe(rareData.category), sx, sy, Theme.ACCENT_TINT, Theme.ACCENT) + 4;
			if (!isBlank(rareData.typeSlot) && sx + Draw.pillWidth(font, rareData.typeSlot) < x + 44 + textW)
				sx += Draw.pill(g, font, rareData.typeSlot, sx, sy, Theme.PANEL_ALT, Theme.MUTED) + 4;
			if (!isBlank(rareData.releaseDate) && sx + Draw.pillWidth(font, rareData.releaseDate) < x + 44 + textW)
				Draw.pill(g, font, rareData.releaseDate, sx, sy, Theme.PANEL_ALT, Theme.MUTED);
		} else if (baseItem != null) {
			g.text(font, Draw.trim(font, baseItem, textW), sx, sy + 2, Theme.FAINT, false);
		}

		// tab bodies drawn by hand
		if (tab == Tab.HISTORY) {
			int bh = contentBottom() - bodyY;
			double scale = isRare ? 1.0 : 64.0;
			String unit = isRare ? "dia per item" : "dia per stack of 64";
			if (historyLoading) {
				listState(g, x, bodyY, w, bh, true, false, false, null, null);
			} else if (w >= 360) {
				int cw = (w - 6) / 2;
				PriceChartWidget.draw(g, font, x, bodyY, cw, bh, "Average · " + unit, history, hp -> hp.avgPrice, scale);
				PriceChartWidget.draw(g, font, x + cw + 6, bodyY, cw, bh, "Lowest · " + unit, history, hp -> hp.lowestPrice, scale);
			} else {
				int ch = (bh - 6) / 2;
				PriceChartWidget.draw(g, font, x, bodyY, w, ch, "Average · " + unit, history, hp -> hp.avgPrice, scale);
				PriceChartWidget.draw(g, font, x, bodyY + ch + 6, w, ch, "Lowest · " + unit, history, hp -> hp.lowestPrice, scale);
			}
		} else if (tab == Tab.DETAILS && rareData != null) {
			drawDetails(g, x, bodyY, w);
		}
	}

	private void drawDetails(GuiGraphicsExtractor g, int x, int y, int w) {
		int bh = contentBottom() - y;
		Draw.card(g, x, y, w, bh, Theme.PANEL, Theme.LINE_SOFT);
		int colW = (w - 24) / 2;
		String[][] facts = {
				{ "Category", safe(rareData.category) },
				{ "Released", safe(rareData.releaseDate) },
				{ "Type / slot", safe(rareData.typeSlot) },
				{ "Dyeable", safe(rareData.dyeable) },
				{ "Glow / particles", safe(rareData.glowParticles) },
		};
		int fy = y + 8;
		for (int i = 0; i < facts.length; i++) {
			int fx = x + 10 + (i % 2) * (colW + 4);
			if (i > 0 && i % 2 == 0) fy += 22;
			g.text(font, facts[i][0].toUpperCase(), fx, fy, Theme.FAINT, false);
			g.text(font, Draw.trim(font, facts[i][1], colW - 4), fx, fy + 10, Theme.TEXT, false);
		}
		fy += 26;
		fy = longFact(g, "Obtained from", safe(rareData.obtainedFrom), x + 10, fy, w - 20, y + bh);
		longFact(g, "Effect", safe(rareData.effect), x + 10, fy, w - 20, y + bh);
	}

	/** A label and a wrapped value; returns the y below it. */
	private int longFact(GuiGraphicsExtractor g, String label, String value, int x, int y, int w, int maxY) {
		if (y + 18 > maxY) return y;
		g.text(font, label.toUpperCase(), x, y, Theme.FAINT, false);
		var lines = font.split(Component.literal(value), w);
		int ly = y + 10;
		for (var line : lines) {
			if (ly + 9 > maxY - 4) break;
			g.text(font, line, x, ly, Theme.TEXT, false);
			ly += 10;
		}
		return ly + 6;
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (tab == Tab.LISTINGS) {
			int x = contentX() - 6, w = contentW() + 12, h = contentBottom() - bodyY;
			boolean empty = listingList != null && listingList.size() == 0;
			listState(g, x, bodyY, w, h, loading, loadFailed, empty,
					"Nobody is selling this " + (ALL_WORLDS.equals(worldFilter) ? "right now" : "on " + worldFilter),
					ALL_WORLDS.equals(worldFilter) ? "Add it to your watchlist to hear when it shows up." : "Try All worlds with the World filter.");
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isEmpty();
	}

	private static String safe(String s) {
		return isBlank(s) ? "-" : s;
	}
}
