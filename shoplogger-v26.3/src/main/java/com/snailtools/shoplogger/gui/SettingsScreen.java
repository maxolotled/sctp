package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ModKeys;
import com.snailtools.shoplogger.CsvExporter;
import com.snailtools.shoplogger.ExcelExporter;
import com.snailtools.shoplogger.OwnShopSaleTracker;
import com.snailtools.shoplogger.RareRentalHighlighter;
import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.SearchPreferences;
import com.snailtools.shoplogger.StockHolograms;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopLog;
import com.snailtools.shoplogger.ShopMarkerRenderer;
import com.snailtools.shoplogger.ShopUploader;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.TempScanWaitOverlay;
import com.snailtools.shoplogger.TeleportHighlight;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.SettingRow;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.qol.BuyConfirm;
import com.snailtools.shoplogger.qol.EmptyHandItems;
import com.snailtools.shoplogger.qol.StorageSwitcher;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Every setting in one place (this used to be two screens, "Settings" and
 * "Advanced settings"), grouped into categories. Each setting is one row
 * with a short explanation; the hotkeys keep working as before.
 */
public class SettingsScreen extends UiScreen {

	private enum Category {
		SCANNING("Scanning", "How the mod reads shop chests"),
		ALERTS("Alerts", "What gets posted in your chat, and buy warnings"),
		DISPLAY("Look & tools", "Colour theme, in-world highlights and shortcuts"),
		EMPTY_HAND("Empty hand", "Click doors, chests and other blocks with an empty slot instead of these items"),
		STORAGE("My storage", "Your own chests: X+M adds one (or everything within 16 blocks)"),
		KEYS("Keys", "Click a key to change it. They're here, not in Options › Controls"),
		DATA("Data", "Export or upload what you've scanned"),
		ADVANCED("Advanced", "Temporary testing tools");

		final String label, description;

		Category(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private static final int[] COOLDOWN_STEPS = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240};
	private static final int ROW_GAP = 4;

	private Category category = Category.SCANNING;
	private final List<SettingRow> rows = new ArrayList<>();
	private int scroll = 0;
	private int rowsX, rowsW, rowsTop;
	private boolean sideNav;

	public SettingsScreen(Screen parent) {
		super("Settings", parent, Section.SETTINGS);
	}

	/** Opened on the Empty hand page — where you land after registering an item with X. */
	public static SettingsScreen emptyHand(Screen parent) {
		SettingsScreen s = new SettingsScreen(parent);
		s.category = Category.EMPTY_HAND;
		return s;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		sideNav = w >= 380;

		if (sideNav) {
			int navW = 118;
			int ny = y;
			for (Category c : Category.values()) {
				addRenderableWidget(new UiButton(x, ny, navW, 20, c.label, UiButton.Style.TAB, () -> select(c)).selected(() -> category == c));
				ny += 24;
			}
			rowsX = x + navW + 10;
			rowsW = w - navW - 10;
			rowsTop = y + 26;
		} else {
			int cx = x;
			for (Category c : Category.values()) {
				int cw = font.width(c.label) + 12;
				if (cx + cw > x + w) break;
				addRenderableWidget(new UiButton(cx, y, cw, 18, c.label, UiButton.Style.TAB, () -> select(c)).selected(() -> category == c));
				cx += cw + 4;
			}
			rowsX = x;
			rowsW = w;
			rowsTop = y + 24 + 26;
		}

		rows.clear();
		for (SettingRow r : rowsFor(category)) {
			r.setWidth(rowsW);
			rows.add(addRenderableWidget(r));
		}
		layoutRows();
	}

	private void select(Category c) {
		if (c == category) return;
		category = c;
		scroll = 0;
		rebuild();
	}

	private List<SettingRow> rowsFor(Category c) {
		return switch (c) {
			case SCANNING -> List.of(
					SettingRow.toggle("Chest scanning", "Read shop chests automatically as you walk past",
							() -> ShopAutoScanner.getInstance().isEnabled(), v -> ShopAutoScanner.getInstance().setEnabled(v)),
					SettingRow.toggle("Recently-scanned markers", "Show a marker on shops scanned recently",
							() -> ShopMarkerRenderer.getInstance().isEnabled(), v -> ShopMarkerRenderer.getInstance().setEnabled(v)),
					SettingRow.stepper("Rescan cooldown", "How long a shop counts as recently scanned",
							COOLDOWN_STEPS, () -> (int) (ShopAutoScanner.getPerShopCooldownMs() / 60000L), ShopAutoScanner::setPerShopCooldownMinutes, "min"),
					SettingRow.toggle("Print scans in chat", "Post each scanned shop's contents in chat",
							ScanChatLogger::isEnabled, ScanChatLogger::setEnabled),
					SettingRow.choice("Chat log format", "How a printed scan is laid out",
							List.of(Boolean.TRUE, Boolean.FALSE), ScanChatLogger::isSingleLine, ScanChatLogger::setSingleLine,
							v -> v ? "Single line" : "Multiple lines"),
					SettingRow.toggle("Shop info on visit", "Send /shops plot info at a shop (max once an hour each)",
							ShopVisitAlert::isShopInfoOnVisitEnabled, ShopVisitAlert::setShopInfoOnVisitEnabled));
			case ALERTS -> List.of(
					SettingRow.toggle("New-item alerts", "Tell me when a shop has something new",
							ShopVisitAlert::isEnabled, ShopVisitAlert::setEnabled),
					SettingRow.toggle("Only for rares", "Limit new-item alerts to rare items",
							ShopVisitAlert::isRaresOnly, ShopVisitAlert::setRaresOnly),
					SettingRow.toggle("Own-shop sale alerts", "Tell me when something sells from my shop",
							OwnShopSaleTracker::isMessagesEnabled, OwnShopSaleTracker::setMessagesEnabled),
					SettingRow.toggle("Watchlist: marketplace posts", "Include sctp.nl marketplace posts in watchlist alerts",
							WatchlistStore::isMarketplaceAlertsEnabled, WatchlistStore::setMarketplaceAlertsEnabled),
					SettingRow.toggle("Confirm expensive buys", "Click twice to buy from a shop at or above the price below",
							BuyConfirm::isEnabled, BuyConfirm::setEnabled),
					SettingRow.stepper("Confirm from", "Shop price that needs a second click",
							BuyConfirm.THRESHOLD_STEPS_DB, BuyConfirm::thresholdDb, BuyConfirm::setThresholdDb, "DB"));
			case DISPLAY -> List.of(
					SettingRow.choice("Colour theme", "The colours of every Shop Logger screen",
							Arrays.asList(Theme.Palette.values()), Theme::get, Theme::set, v -> v.label),
					SettingRow.toggle("Rare rental highlights", "Highlight rentable rares in shops",
							RareRentalHighlighter::isEnabled, RareRentalHighlighter::setEnabled),
					SettingRow.toggle("Highlights inside shulkers", "Also highlight rentable rares inside shulker boxes",
							RareRentalHighlighter::isInShulkersEnabled, RareRentalHighlighter::setInShulkersEnabled),
					SettingRow.toggle("Ender chest / backpack button", "A button beside /ec and /bp that opens the other one",
							StorageSwitcher::isEnabled, StorageSwitcher::setEnabled),
					SettingRow.toggle("Stack repeated chat", "Show ×2, ×3 next to a message instead of repeating it",
							com.snailtools.shoplogger.qol.ChatStacker::isEnabled, com.snailtools.shoplogger.qol.ChatStacker::setEnabled),
					SettingRow.toggle("Stock holograms", "Small stock line on the front of scanned shop chests",
							StockHolograms::isEnabled, StockHolograms::setEnabled),
					SettingRow.choice("Teleport beam style", "The beam that points to a shop after TP",
							Arrays.asList(TeleportHighlight.BeamStyle.values()), TeleportHighlight::getStyle, TeleportHighlight::setStyle,
							v -> v.label),
					SettingRow.choice("/search opens", "Where the /search command shows results",
							List.of(Boolean.TRUE, Boolean.FALSE), SearchPreferences::isGuiSearch, SearchPreferences::setGuiSearch,
							v -> v ? "This menu" : "Chat"),
					SettingRow.toggle("Hide display listings", "Leave out [DISPLAY] shops (not for sale) in searches and lists",
							SearchPreferences::hideDisplayListings, SearchPreferences::setHideDisplayListings));
			case DATA -> List.of(
					SettingRow.action("Export to CSV + Excel", "Save all " + ShopLog.size() + " logged entries to run/shoplogger/",
							"Export", this::exportBoth),
					SettingRow.action("Upload to the Trading Post", "Send your scans to sctp.nl right now",
							"Upload", () -> ShopUploader.uploadAsync(minecraft, true)));
			case EMPTY_HAND -> emptyHandRows();
			case STORAGE -> storageRows();
			case KEYS -> keyRows();
			case ADVANCED -> List.of(
					SettingRow.toggle("Auctions on the dashboard", "Run rare auctions in-game: the Auctions screen, /sold, /unsold, /info, /auction (rejoin for the commands)",
							com.snailtools.shoplogger.auction.AuctionHost::isShown, com.snailtools.shoplogger.auction.AuctionHost::setShown),
					SettingRow.toggle("Show scan wait (temporary)", "Show the scanner's wait time, in ms, top-right",
							TempScanWaitOverlay::isEnabled, TempScanWaitOverlay::setEnabled));
		};
	}

	private List<SettingRow> storageRows() {
		List<SettingRow> out = new ArrayList<>();
		out.add(SettingRow.toggle("My storage", "Track your own chests, barrels, shulker boxes and ender chest (kept on this computer only)",
				com.snailtools.shoplogger.storage.StorageSettings::isEnabled, v -> { com.snailtools.shoplogger.storage.StorageSettings.setEnabled(v); rebuild(); }));
		out.add(SettingRow.choice("Storage holograms", "What the text on the side of your containers shows",
				List.of(com.snailtools.shoplogger.storage.StorageSettings.HologramMode.values()), com.snailtools.shoplogger.storage.StorageSettings::hologramMode,
				com.snailtools.shoplogger.storage.StorageSettings::setHologramMode, s -> s.label));
		out.add(SettingRow.choice("Highlight style", "How a container lights up when you find an item",
				List.of(com.snailtools.shoplogger.storage.StorageSettings.HighlightStyle.values()), com.snailtools.shoplogger.storage.StorageSettings::highlightStyle,
				com.snailtools.shoplogger.storage.StorageSettings::setHighlightStyle, s -> s.label));
		out.add(SettingRow.choice("Opening a lit-up container", "Whether opening one turns off just that container's light, or all of them",
				List.of(com.snailtools.shoplogger.storage.StorageSettings.OpenStops.values()), com.snailtools.shoplogger.storage.StorageSettings::openStops,
				com.snailtools.shoplogger.storage.StorageSettings::setOpenStops, s -> s.label));
		// the containers themselves are listed (and renamed, removed...) in My storage › Containers
		int n = com.snailtools.shoplogger.storage.StorageIndex.all().size();
		out.add(SettingRow.action("Open My storage", n == 0 ? "Everything in your containers, searchable"
				: n + " container" + (n == 1 ? "" : "s") + ": see, rename or remove them under Containers", "Open",
				() -> minecraft.setScreenAndShow(new StorageScreen(this))));
		return out;
	}

	/** The key waiting for its new binding (Settings › Keys), or null. */
	private net.minecraft.client.KeyMapping listening;

	private List<SettingRow> keyRows() {
		List<SettingRow> out = new ArrayList<>();
		for (net.minecraft.client.KeyMapping k : ModKeys.all()) {
			// held with the menu key: shown as "X+M", and you only press the second key to change it
			String hint = ModKeys.isChord(k) ? "Pressed while holding " + ModKeys.menu() + ": click, then press the new second key"
					: "Click, then press the new key (Esc cancels, Backspace clears)";
			out.add(SettingRow.key(ModKeys.label(k), hint, () -> ModKeys.text(k), () -> listening == k, () -> { listening = listening == k ? null : k; keyNote = null; }));
		}
		out.add(SettingRow.action("Reset all keys", "Back to X, X+I, X+M and the rest", "Reset", () -> { listening = null; ModKeys.resetAll(); }));
		return out;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (listening == null) return super.keyPressed(event);
		int key = event.key();
		if (key != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			boolean clear = key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE || key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE;
			com.mojang.blaze3d.platform.InputConstants.Key k = clear ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN : com.mojang.blaze3d.platform.InputConstants.getKey(event);
			net.minecraft.client.KeyMapping taken = ModKeys.clash(listening, k);
			if (taken != null) {
				// one key, one job: the other one has to move first
				String shown = (ModKeys.isChord(listening) ? ModKeys.menu() + "+" : "") + k.getDisplayName().getString();
				keyNote = shown + " is already used for \"" + ModKeys.label(taken) + "\". Change that one first, or pick another key.";
				keyNoteBad = true;
			} else {
				net.minecraft.client.KeyMapping vanilla = ModKeys.vanillaClash(listening, k);
				ModKeys.set(listening, k);
				keyNote = vanilla == null ? null : "Set, but Minecraft uses " + k.getDisplayName().getString() + " for "
						+ net.minecraft.network.chat.Component.translatable(vanilla.getName()).getString() + " too.";
				keyNoteBad = false;
			}
			keyNoteAt = System.currentTimeMillis();
		}
		listening = null;
		return true;
	}

	/** A line under the Keys heading after a change: a clash that was refused, or a warning. */
	private String keyNote;
	private boolean keyNoteBad;
	private long keyNoteAt;

	private List<SettingRow> emptyHandRows() {
		List<SettingRow> out = new ArrayList<>();
		out.add(SettingRow.toggle("Empty hand on block clicks", "Use an empty slot instead of the items below",
				EmptyHandItems::isEnabled, EmptyHandItems::setEnabled));
		out.add(SettingRow.action("Add an item", "Press Add, hold the item, then press " + ModKeys.menu(),
				"Add", () -> { EmptyHandItems.startCapture(); minecraft.setScreenAndShow(null); }));
		for (EmptyHandItems.Entry e : EmptyHandItems.items()) {
			String named = e.name == null || e.name.isEmpty() ? "" : " (named)";
			out.add(SettingRow.action(e.label, e.item.replace("minecraft:", "") + named,
					"Remove", () -> { EmptyHandItems.remove(e); rebuild(); }));
		}
		return out;
	}

	/** Room at the bottom for the ▼ that says there's more, when there is. */
	private static final int MORE_H = 12;

	private int rowsTotal() { return rows.size() * (SettingRow.HEIGHT + ROW_GAP) - ROW_GAP; }

	private boolean scrollable() { return rowsTotal() > contentBottom() - rowsTop; }

	private int rowsViewBottom() {
		return contentBottom() - (scrollable() ? MORE_H : 0);
	}

	private int maxScroll() {
		return Math.max(0, rowsTotal() - (rowsViewBottom() - rowsTop));
	}

	private void layoutRows() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		for (int i = 0; i < rows.size(); i++) {
			SettingRow r = rows.get(i);
			int ry = rowsTop + i * (SettingRow.HEIGHT + ROW_GAP) - scroll;
			r.setX(rowsX);
			r.setY(ry);
			r.visible = ry >= rowsTop - 1 && ry + SettingRow.HEIGHT <= rowsViewBottom() + 1;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= rowsX && maxScroll() > 0) {
			scroll -= (int) Math.signum(scrollY) * (SettingRow.HEIGHT + ROW_GAP);
			layoutRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void exportBoth() {
		try {
			Path runDir = minecraft.gameDirectory.toPath();
			Path csvOut = runDir.resolve("shoplogger").resolve("shops.csv");
			Path xlsxOut = runDir.resolve("shoplogger").resolve("shops.xlsx");
			CsvExporter.export(ShopLog.getAll(), csvOut);
			ExcelExporter.export(ShopLog.getAll(), xlsxOut);
			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Exported " + ShopLog.size() + " entries at " + time + " -> run/shoplogger/");
		} catch (Exception e) {
			ChatFormat.send(minecraft, ChatFormat.ERROR, "Export failed: " + e.getMessage());
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int hy = rowsTop - 24;
		Draw.textShadow(g, font, category.label, rowsX, hy, Theme.TEXT);
		String desc = category == Category.STORAGE ? "Your own chests: " + ModKeys.text("storage") + " adds one (or everything within 16 blocks)" : category.description;
		int descColor = Theme.MUTED;
		if (category == Category.KEYS && keyNote != null && System.currentTimeMillis() - keyNoteAt < 8000L) {
			desc = keyNote;
			descColor = keyNoteBad ? Theme.BAD : Theme.WARN;
		}
		// leave room for "Scroll for more" on the right
		int room = rowsW - (scroll < maxScroll() ? font.width("Scroll for more ↓") + 8 : 0);
		g.text(font, Draw.trim(font, desc, room), rowsX, hy + 11, descColor, false);
		if (sideNav) {
			// thin divider between the category list and the rows
			g.fill(rowsX - 6, contentY(), rowsX - 5, contentBottom(), Theme.LINE_SOFT);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (scroll < maxScroll()) {
			Draw.right(g, font, "Scroll for more ↓", rowsX + rowsW, rowsTop - 13, Theme.FAINT);
			// and at the bottom, right where the list is cut off: a fade and an arrow (bobbing a little)
			int by = rowsViewBottom();
			Draw.gradient(g, rowsX, by - 14, rowsW, 14, 0x00000000, Theme.alpha(Theme.BG, 0xCC));
			int bob = (int) ((System.currentTimeMillis() / 300L) % 2);
			Draw.centered(g, font, "▼  more below  ▼", rowsX + rowsW / 2, by + 2 + bob, Theme.MUTED);
		}
		if (scroll > 0) Draw.centered(g, font, "▲", rowsX + rowsW / 2, rowsTop - 10, Theme.FAINT);
	}
}
