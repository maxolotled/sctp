package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.MarketplaceListing;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only in-game browser for the website's marketplace (no login needed
 * to read). Bidding or posting needs a real account on the site, so each
 * row opens that exact listing on sctp.nl instead of copying the whole
 * bidding flow in-game.
 */
public class MarketplaceScreen extends UiScreen {

	private static final Map<String, String> CURRENCY_LABELS = Map.of(
			"diamond", "Dia", "diamondblock", "DB", "diamondstack", "STX"
	);
	private static final long SEARCH_DEBOUNCE_MS = 300;

	private UiField search;
	private ItemListWidget list;
	private int listX, listY, listW, listH;
	private String keepQuery = "";

	private List<MarketplaceListing> listings = List.of();
	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean listingsLoaded = false;
	private boolean catalogLoaded = false;
	private boolean loadFailed = false;
	private boolean requested = false;
	private long searchChangedAtMillis = -1;

	public MarketplaceScreen(Screen parent) {
		super("Marketplace", parent, Section.MARKET);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int btnW = 108;
		search = new UiField(font, x, y, w - btnW - 6, 20, "Search by item name…", true);
		search.box.setValue(keepQuery);
		search.box.setResponder(s -> { keepQuery = s; searchChangedAtMillis = System.currentTimeMillis(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		addRenderableWidget(new UiButton(x + w - btnW, y, btnW, 20, "Post on sctp.nl ↗", UiButton.Style.SECONDARY,
				() -> UiLinks.open("https://sctp.nl/marketplace/")).tooltip("Posting and bidding happen on the website"));
		y += 26;

		listX = x - 6;
		listY = y + 14;
		listW = w + 12;
		listH = contentBottom() - listY;
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

	@Override
	public void tick() {
		super.tick();
		if (searchChangedAtMillis > 0 && System.currentTimeMillis() - searchChangedAtMillis >= SEARCH_DEBOUNCE_MS) {
			searchChangedAtMillis = -1;
			refreshList();
		}
	}

	private void loadData() {
		WebDataClient.fetchMarketplaceListings().thenAccept(rows -> minecraft.execute(() -> {
			listings = rows;
			listingsLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { listingsLoaded = true; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchVanillaCatalog().thenAccept(items -> minecraft.execute(() -> {
			vanillaItems = items;
			catalogLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> catalogLoaded = true);
			return null;
		});

		// Rare-item posts still render without their texture (placeholder icon),
		// so this doesn't gate loading the way the vanilla catalog does.
		WebDataClient.fetchRareCatalog().thenAccept(items -> minecraft.execute(() -> {
			rareItems = items;
			refreshList();
		})).exceptionally(ex -> null);
	}

	private boolean isLoading() {
		return !listingsLoaded || !catalogLoaded;
	}

	private String baseItemFor(String name) {
		for (VanillaItem it : vanillaItems) if (it.name.equalsIgnoreCase(name)) return it.baseItem;
		return null;
	}

	private String rareTextureFor(String name) {
		for (RareItem it : rareItems) if (it.name.equalsIgnoreCase(name)) return it.texture;
		return null;
	}

	private void refreshList() {
		if (list == null) return;
		list.clearAllEntries();
		String q = search.value().trim().toLowerCase(Locale.ROOT);

		for (MarketplaceListing l : listings) {
			if (!q.isEmpty() && !l.itemName.toLowerCase(Locale.ROOT).contains(q)) continue;
			String subtitle = subtitleFor(l);
			boolean selling = "selling".equals(l.type);
			String baseItem = baseItemFor(l.itemName);
			ItemListWidget.ItemEntry entry;
			if (baseItem != null) {
				entry = ItemListWidget.forVanilla(l.itemName, baseItem, subtitle, () -> openOnWebsite(l));
			} else {
				String texture = rareTextureFor(l.itemName);
				entry = texture != null
						? ItemListWidget.forRare(l.itemName, subtitle, texture, () -> openOnWebsite(l))
						: ItemListWidget.forVanilla(l.itemName, null, subtitle, () -> openOnWebsite(l));
			}
			list.addItemEntry(entry.withBadge(selling ? "Selling" : "Looking for", selling ? Theme.ACCENT : Theme.INFO));
		}
	}

	private String subtitleFor(MarketplaceListing l) {
		MarketplaceListing.PriceInfo price = l.priceInfo();
		String priceText = price == null
				? "No price set"
				: formatPrice(price.amount) + " " + CURRENCY_LABELS.getOrDefault(
						price.currency == null ? "" : price.currency.toLowerCase(Locale.ROOT), price.currency);
		return priceText + " · " + l.world;
	}

	private static String formatPrice(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private void openOnWebsite(MarketplaceListing l) {
		UiLinks.open("https://sctp.nl/marketplace/#listing=" + URLEncoder.encode(l.id, StandardCharsets.UTF_8));
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int y = contentY() + 27;
		Draw.glyph(g, Draw.GLYPH_LINK, contentX(), y, Theme.FAINT);
		g.text(font, Draw.trim(font, "Click a post to open it on sctp.nl, where you can bid or reply", contentW() - 110), contentX() + 11, y, Theme.FAINT, false);
		if (!isLoading() && list != null) {
			String count = list.size() == listings.size() ? fmtNum(listings.size()) + " posts" : fmtNum(list.size()) + " of " + fmtNum(listings.size());
			Draw.right(g, font, count, contentX() + contentW(), y, Theme.MUTED);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		boolean empty = !isLoading() && list != null && list.size() == 0;
		listState(g, listX, listY, listW, listH, isLoading(), loadFailed, empty,
				listings.isEmpty() ? "No marketplace posts right now" : "No posts match that",
				listings.isEmpty() ? "Be the first: post one on sctp.nl." : "Try a shorter search.");
	}
}
