package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.SharedShop;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** In-game equivalent of a seller's profile page: avatar, bio if set, and their current listings. */
public class SellerProfileScreen extends UiScreen {

	private static final String ALL_WORLDS = "All worlds";

	private final String username;
	private String worldFilter;

	private List<Listing> allListings = List.of();
	private SharedShop profile;
	private ListingListWidget listingList;
	private boolean loading = true;
	private boolean loadFailed = false;
	private boolean requested = false;
	private int listY;

	public SellerProfileScreen(Screen parent, String username, String defaultWorld) {
		super(username, parent, Section.LISTINGS);
		this.username = username;
		this.worldFilter = defaultWorld != null ? defaultWorld : ALL_WORLDS;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int chipW = Math.min(140, w / 3);
		addRenderableWidget(new UiChip<>(x + w - chipW - 8, y + 6, chipW, 18, "World", List.of(ALL_WORLDS, "Firefly", "Honeybee"), worldFilter,
				v -> v, v -> { worldFilter = v; refreshListingList(); }));
		String url = "https://sctp.nl/s/" + URLEncoder.encode(username, StandardCharsets.UTF_8);
		addRenderableWidget(new UiButton(x + w - chipW - 8, y + 28, chipW, 16, "Profile on sctp.nl ↗", UiButton.Style.GHOST, () -> UiLinks.open(url))
				.tooltip(url));

		listY = y + 56;
		listingList = new ListingListWidget(minecraft, x - 6, listY, w + 12, contentBottom() - listY);
		addRenderableWidget(listingList);
		refreshListingList();

		if (!requested) {
			requested = true;
			loadData();
		}
	}

	private void loadData() {
		WebDataClient.fetchListings().thenAccept(all -> minecraft.execute(() -> {
			List<Listing> mine = new ArrayList<>();
			for (Listing l : all) {
				if (username.equalsIgnoreCase(l.seller)) mine.add(l);
			}
			allListings = mine;
			loading = false;
			refreshListingList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { loading = false; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchSharedShops().thenAccept(shops -> minecraft.execute(() -> {
			for (SharedShop s : shops) {
				if (username.equalsIgnoreCase(s.username)) {
					profile = s;
					break;
				}
			}
		})).exceptionally(ex -> null);
	}

	private void refreshListingList() {
		if (listingList == null) return;
		listingList.clearAllEntries();
		for (Listing l : allListings) {
			if (!ALL_WORLDS.equals(worldFilter) && !worldFilter.equalsIgnoreCase(l.world)) continue;
			listingList.addListingEntry(ListingListWidget.withItemName(l, seller -> {}));
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY(), w = contentW();
		Draw.card(g, x, y, w, 48, Theme.PANEL, Theme.LINE_SOFT);
		Draw.round(g, x + 6, y + 6, 36, 36, Theme.SLOT);
		Draw.remoteTexture(g, "https://mc-heads.net/avatar/" + username + "/32", x + 8, y + 8, 32);

		int chipW = Math.min(140, w / 3);
		int textW = w - 52 - chipW - 16;
		Draw.scaled(g, font, Draw.trim(font, username, (int) (textW / 1.25f)), x + 50, y + 8, Theme.TEXT, 1.25f, true);
		int sy = y + 23;
		if (profile != null && profile.bio != null && !profile.bio.isEmpty()) {
			var lines = font.split(Component.literal(profile.bio), textW);
			for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), x + 50, sy + i * 10, Theme.MUTED, false);
		} else {
			String count = loading ? "Loading listings…" : fmtNum(allListings.size()) + " listing" + (allListings.size() == 1 ? "" : "s") + " across both worlds";
			g.text(font, Draw.trim(font, count, textW), x + 50, sy + 2, Theme.MUTED, false);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		listState(g, contentX() - 6, listY, contentW() + 12, contentBottom() - listY, loading, loadFailed,
				listingList != null && listingList.size() == 0,
				username + " has no listings " + (ALL_WORLDS.equals(worldFilter) ? "right now" : "on " + worldFilter),
				ALL_WORLDS.equals(worldFilter) ? null : "Try All worlds with the World filter.");
	}
}
