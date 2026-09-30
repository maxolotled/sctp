package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.Tile;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.mapart.MapartScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

/**
 * The dashboard (default hotkey: X): a status strip, a search box that goes
 * straight to the listings, a tile for every section, and links to the
 * website's suggestion and bug forms.
 */
public class HomeScreen extends UiScreen {

	private static final int KEY_ENTER = 257;
	private static final int KEY_KP_ENTER = 335;

	private UiField search;
	private int heroY;
	private int pillsY;
	private boolean showPills;

	public HomeScreen() {
		super("Snailcraft Trading Post", null, Section.HOME);
	}

	@Override
	protected String crumb() {
		return "Home";
	}

	@Override
	protected void initContent() {
		int x = contentX(), w = contentW();
		int maxW = Math.min(w, 640);
		x = (width - maxW) / 2;
		w = maxW;

		heroY = contentY() + 2;
		showPills = height >= 220;
		int y = heroY + 30;
		pillsY = y;
		if (showPills) y += 18;

		// search, straight into the listings
		int btnW = 62;
		search = new UiField(font, x, y, w - btnW - 6, 20, "Search every listing: item or seller…", true);
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		addRenderableWidget(new UiButton(x + w - btnW, y, btnW, 20, "Search", UiButton.Style.PRIMARY, this::doSearch));
		y += 28;

		// section tiles
		Section[] tiles = { Section.LISTINGS, Section.VANILLA, Section.RARES, Section.WATCHLIST, Section.MARKET, Section.MAPART, Section.SETTINGS };
		int cols = w >= 520 ? 4 : w >= 330 ? 3 : 2;
		int rows = (tiles.length + cols - 1) / cols;
		int footerH = 24;
		int gap = 6;
		int areaH = height - 8 - footerH - y;
		int tileH = Math.max(22, Math.min(46, (areaH - (rows - 1) * gap) / rows));
		int tileW = (w - (cols - 1) * gap) / cols;
		for (int i = 0; i < tiles.length; i++) {
			Section s = tiles[i];
			int tx = x + (i % cols) * (tileW + gap);
			int ty = y + (i / cols) * (tileH + gap);
			addRenderableWidget(new Tile(tx, ty, tileW, tileH, s.icon(), s.label, blurb(s), () -> badge(s), () -> badgeColor(s),
					() -> minecraft.setScreenAndShow(s.open(this))));
		}

		// footer
		int fy = height - 8 - 18;
		int fx = x;
		fx += footerLink(fx, fy, "Suggest a feature", "https://sctp.nl/suggest/") + 4;
		fx += footerLink(fx, fy, "Report a bug", "https://sctp.nl/bug/") + 4;
		footerLink(fx, fy, "sctp.nl ↗", "https://sctp.nl/");
		addRenderableWidget(new UiButton(x + w - 60, fy, 60, 18, "Close", UiButton.Style.SECONDARY, this::onClose));

		setInitialFocus(search.box);
	}

	private int footerLink(int x, int y, String label, String url) {
		int w = font.width(label) + 12;
		addRenderableWidget(new UiButton(x, y, w, 18, label, UiButton.Style.GHOST, () -> UiLinks.open(url)).tooltip(url));
		return w;
	}

	private static String blurb(Section s) {
		return switch (s) {
			case LISTINGS -> "Every shop listing, searchable";
			case VANILLA -> "Vanilla items: prices & history";
			case RARES -> "Every rare, with its details";
			case WATCHLIST -> "Get pinged when items show up";
			case MARKET -> "Buy & sell posts from the site";
			case MAPART -> "Mapart near you & uploads";
			case SETTINGS -> "Scanning, alerts & more";
			default -> s.description;
		};
	}

	private static String badge(Section s) {
		return switch (s) {
			case WATCHLIST -> {
				int n = WatchlistStore.getAll().size();
				yield n == 0 ? null : n + " watched";
			}
			case MAPART -> MapartScanner.getInstance().isEnabled() ? "On" : "Off";
			case SETTINGS -> ShopAutoScanner.getInstance().isEnabled() ? null : "Scanning off";
			default -> null;
		};
	}

	private static int badgeColor(Section s) {
		return switch (s) {
			case MAPART -> MapartScanner.getInstance().isEnabled() ? Theme.ACCENT : Theme.MUTED;
			case SETTINGS -> Theme.WARN;
			default -> Theme.ACCENT;
		};
	}

	private void doSearch() {
		String q = search.value().trim();
		minecraft.setScreenAndShow(new ListingsScreen(this, q.isEmpty() ? null : q));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.box.isFocused() && (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER)) {
			doSearch();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int maxW = Math.min(contentW(), 640);
		int x = (width - maxW) / 2;
		Draw.scaled(g, font, "Snailcraft Trading Post", x, heroY, Theme.TEXT, 1.5f, true);
		Draw.text(g, font, Draw.trim(font, "Every shop on the server, in one list.", maxW), x, heroY + 16, Theme.MUTED);

		if (showPills) {
			int px = x;
			ShopWorld world = WorldSelection.get();
			px += Draw.dotPill(g, font, world != null ? world.label() : "World not detected yet", px, pillsY,
					world != null ? Theme.worldColor(world.label()) : Theme.FAINT, Theme.PANEL_ALT, Theme.TEXT) + 4;
			ShopAutoScanner scanner = ShopAutoScanner.getInstance();
			String scan = scanner.isEnabled() ? "Scanning · " + scanner.knownShopCount() + " shops nearby" : "Scanning off";
			if (px + Draw.pillWidth(font, scan) + 7 < x + maxW)
				px += Draw.dotPill(g, font, scan, px, pillsY, scanner.isEnabled() ? Theme.ACCENT : Theme.WARN, Theme.PANEL_ALT, Theme.TEXT) + 4;
			int watched = WatchlistStore.getAll().size();
			String watch = watched == 0 ? "Watchlist empty" : "Watching " + watched;
			if (px + Draw.pillWidth(font, watch) + 7 < x + maxW)
				Draw.dotPill(g, font, watch, px, pillsY, watched == 0 ? Theme.FAINT : Theme.TEAL, Theme.PANEL_ALT, Theme.TEXT);
		}
	}

	@Override
	public void onClose() {
		RemoteTextureCache.clear(Minecraft.getInstance());
		super.onClose();
	}
}
