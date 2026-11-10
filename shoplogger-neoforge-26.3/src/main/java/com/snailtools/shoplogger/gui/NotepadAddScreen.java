package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import com.snailtools.shoplogger.notepad.Notepad;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

/** Notepad › Add item: search any item or rare, set how many, click it to put it on the shopping list. */
public class NotepadAddScreen extends UiScreen {

	private static final int MAX_RESULTS = 60;
	private static String keepAmount = "64";

	private static List<VanillaItem> vanilla = List.of();
	private static List<RareItem> rares = List.of();
	private static boolean requested;

	private UiField search, amount;
	private ItemListWidget list;
	private int listX, listY, listW, listH;
	private String added; // "Added 64 Glass Pane", shown under the fields

	public NotepadAddScreen(UiScreen parent) {
		super("Add item", parent, parent.section());
	}

	@Override
	protected String crumb() { return "Notepad › Add item"; }

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int aw = 90;
		search = new UiField(font, x, y, w - aw - 6, 20, "Search an item or rare to add…", true);
		search.box.setResponder(s -> fill());
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		amount = new UiField(font, x + w - aw, y, aw, 20, "How many", false);
		amount.box.setValue(keepAmount);
		amount.box.setResponder(s -> keepAmount = s);
		addRenderableOnly(amount.frame());
		addRenderableWidget(amount.box);
		setInitialFocus(search.box);

		listX = x - 6;
		listY = y + 40;
		listW = w + 12;
		listH = contentBottom() - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);

		if (!requested) {
			requested = true;
			WebDataClient.fetchVanillaCatalog().thenAccept(v -> minecraft.execute(() -> { vanilla = v; fill(); })).exceptionally(ex -> null);
			WebDataClient.fetchRareCatalog().thenAccept(r -> minecraft.execute(() -> { rares = r; fill(); })).exceptionally(ex -> null);
		}
		fill();
	}

	private int amount() {
		try { return Math.max(1, Integer.parseInt(amount.value().trim())); } catch (NumberFormatException e) { return 1; }
	}

	private void add(String name, String base) {
		int n = amount();
		Notepad.add(name, base, n);
		added = "Added " + Notepad.fmt(n) + " " + name + ". Add more, or press Esc.";
	}

	private void fill() {
		if (list == null) return;
		list.clearAllEntries();
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return;
		int shown = 0;
		for (RareItem r : rares) {
			if (shown >= MAX_RESULTS) break;
			if (r.name == null || !r.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.addItemEntry(ItemListWidget.forRare(r.name, (r.category == null ? "Rare" : r.category) + " · click to add", r.texture, () -> add(r.name, null)));
			shown++;
		}
		for (VanillaItem v : vanilla) {
			if (shown >= MAX_RESULTS) break;
			if (v.name == null || !v.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.addItemEntry(ItemListWidget.forVanilla(v.name, v.baseItem, "Click to add", () -> add(v.name, v.baseItem)));
			shown++;
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		String line = added != null ? added : "Set how many on the right, then click an item to put it on your shopping list.";
		g.text(font, Draw.trim(font, line, contentW()), contentX(), contentY() + 27, added != null ? Theme.ACCENT : Theme.MUTED, false);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list != null && list.size() == 0 && !search.value().isBlank()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing matches that", "Try a shorter name.", Theme.MUTED);
		}
	}
}
