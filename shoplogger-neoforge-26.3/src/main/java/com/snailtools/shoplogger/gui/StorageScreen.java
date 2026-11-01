package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RareIcons;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import com.snailtools.shoplogger.storage.StorageHighlight;
import com.snailtools.shoplogger.storage.StorageIndex;
import com.snailtools.shoplogger.storage.StorageSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

/**
 * My storage: everything in the containers you added (X+M), searchable. Click
 * an item to light up the containers holding it; "Containers" lists each one
 * with what's inside and what went in and out lately.
 */
public class StorageScreen extends UiScreen {

	private boolean byContainer;
	private UiField search;
	private String keepSearch = "";
	private ItemListWidget list;
	private int listX, listY, listW, listH;

	public StorageScreen(Screen parent) {
		super("My storage", parent, Section.STORAGE);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int tw = 80;
		addRenderableWidget(new UiButton(x, y, tw, 18, "Items", UiButton.Style.TAB, () -> { byContainer = false; rebuild(); }).selected(() -> !byContainer));
		addRenderableWidget(new UiButton(x + tw + 4, y, tw + 10, 18, "Containers", UiButton.Style.TAB, () -> { byContainer = true; rebuild(); }).selected(() -> byContainer));
		int sx = x + tw * 2 + 22;
		search = new UiField(font, sx, y - 1, w - (sx - x), 20, byContainer ? "Search containers…" : "Search your storage…", true);
		search.box.setValue(keepSearch);
		search.box.setResponder(s -> { keepSearch = s; refresh(); });
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		setInitialFocus(search.box);

		listX = x - 6;
		listY = y + 40;
		listW = w + 12;
		listH = contentBottom() - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);
		refresh();
	}

	private void refresh() {
		if (list == null) return;
		list.clearAllEntries();
		String q = keepSearch.trim().toLowerCase(Locale.ROOT);
		if (byContainer) {
			for (StorageIndex.Box b : StorageIndex.all()) {
				String[] s = StorageIndex.summary(b);
				String sub = s[0] + (s[1].isEmpty() ? "" : " · " + s[1]);
				if (!q.isEmpty() && !b.label().toLowerCase(Locale.ROOT).contains(q) && !sub.toLowerCase(Locale.ROOT).contains(q) && !holds(b, q)) continue;
				list.addItemEntry(ItemListWidget.forVanilla(b.label(), iconFor(b), sub, () -> minecraft.setScreenAndShow(new StorageBoxScreen(this, b))));
			}
			return;
		}
		for (Object[] t : StorageIndex.totals()) {
			StorageIndex.Item i = (StorageIndex.Item) t[0];
			int boxes = (Integer) t[1];
			if (!q.isEmpty() && !i.n.toLowerCase(Locale.ROOT).contains(q) && !i.b.toLowerCase(Locale.ROOT).contains(q)) continue;
			String sub = String.format(Locale.ROOT, "%,d", i.c) + " in " + boxes + " container" + (boxes == 1 ? "" : "s") + " · click to find it";
			String texture = RareIcons.isLoaded() ? RareIcons.textureFor(i.n) : null;
			boolean rare = texture != null;
			Runnable find = () -> StorageHighlight.showItem(rare ? null : i.b, i.n);
			Runnable page = () -> minecraft.setScreenAndShow(new ItemDetailScreen(this, i.n, rare ? null : i.b, texture, rare, null));
			list.addItemEntry(rare ? ItemListWidget.forRare(i.n, sub, texture, find, page) : ItemListWidget.forVanilla(i.n, i.b, sub, find, page));
		}
	}

	private static boolean holds(StorageIndex.Box b, String q) {
		for (StorageIndex.Item i : b.items) if (i.n.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	static String iconFor(StorageIndex.Box b) {
		return switch (b.kind) {
			case "barrel" -> "minecraft:barrel";
			case "shulker" -> "minecraft:shulker_box";
			case "ender" -> "minecraft:ender_chest";
			default -> "minecraft:chest";
		};
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int y = contentY() + 26;
		List<StorageIndex.Box> all = StorageIndex.all();
		int waiting = 0, items = 0;
		for (StorageIndex.Box b : all) { if (b.waiting()) waiting++; items += b.total(); }
		String head = all.size() + " container" + (all.size() == 1 ? "" : "s") + " · " + String.format(Locale.ROOT, "%,d", items) + " items"
				+ (waiting > 0 ? " · " + waiting + " waiting for their first read (walk past them)" : "");
		g.text(font, Draw.trim(font, head.toUpperCase(Locale.ROOT), contentW() - 4), contentX(), y, Theme.FAINT, false);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (!StorageSettings.isEnabled()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "My storage is turned off", "Turn it on in Settings › My storage.", Theme.MUTED);
		} else if (!StorageIndex.ready()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Which world is this?", "Join Firefly or Honeybee (or use /setworld) to see your storage.", Theme.MUTED);
		} else if (StorageIndex.all().isEmpty()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing in My storage yet",
					"Look at a chest, barrel or shulker box and press X+M. Press X+M looking at nothing to add everything within 16 blocks.", Theme.MUTED);
		} else if (list != null && list.size() == 0) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing matches that", byContainer ? "Try an item name or coordinates." : "It isn't in any of your containers.", Theme.MUTED);
		}
	}
}
