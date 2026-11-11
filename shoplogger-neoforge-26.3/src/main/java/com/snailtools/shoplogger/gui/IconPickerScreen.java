package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Pick an item to show as an icon (a container's in My storage, a note's):
 * any item in the game, searched by name, with a few suggestions on top
 * (what's in that container, say). "No icon" hands back null.
 */
public class IconPickerScreen extends UiScreen {

	private static final int MAX_RESULTS = 80;

	private final String what;
	private final List<String> suggestions;
	private final Consumer<String> onPick;
	private UiField search;
	private ItemListWidget list;
	private int listX, listY, listW, listH;

	/** suggestions: item ids shown first while nothing's typed (may be empty). */
	public IconPickerScreen(UiScreen parent, String what, List<String> suggestions, Consumer<String> onPick) {
		super("Pick an icon", parent, parent.section());
		this.what = what;
		this.suggestions = suggestions;
		this.onPick = onPick;
	}

	@Override
	protected String crumb() { return "Icon for " + what; }

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		String none = "No icon";
		int nw = font.width(none) + 16;
		search = new UiField(font, x, y, w - nw - 6, 20, "Search any item…", true);
		search.box.setResponder(s -> fill());
		addRenderableOnly(search.frame());
		addRenderableWidget(search.box);
		addRenderableWidget(new UiButton(x + w - nw, y, nw, 20, none, UiButton.Style.GHOST, () -> pick(null)));
		setInitialFocus(search.box);

		listX = x - 6;
		listY = y + 40;
		listW = w + 12;
		listH = contentBottom() - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);
		fill();
	}

	private void pick(String id) {
		onPick.accept(id);
		onClose();
	}

	private void fill() {
		if (list == null) return;
		list.clearAllEntries();
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		List<String> ids = new ArrayList<>();
		if (q.isEmpty()) {
			for (String s : suggestions) if (!ids.contains(s)) ids.add(s);
		} else {
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) continue;
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				String name = Draw.stack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
				if (name.contains(q) || id.contains(q)) ids.add(id);
				if (ids.size() >= MAX_RESULTS) break;
			}
		}
		for (String id : ids) {
			net.minecraft.world.item.ItemStack st = Draw.stackFor(id);
			String name = st.isEmpty() ? id : st.getHoverName().getString();
			list.addItemEntry(ItemListWidget.forVanilla(name, id, "Click to use it", () -> pick(id)));
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		String line = search.value().isBlank() ? (suggestions.isEmpty() ? "Type an item's name." : "What's in it, or type any item's name.") : "";
		if (!line.isEmpty()) g.text(font, line, contentX(), contentY() + 27, Theme.MUTED, false);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list != null && list.size() == 0 && !search.value().isBlank()) {
			Draw.emptyState(g, font, listX, listY, listW, listH, "Nothing matches that", "Try a shorter name.", Theme.MUTED);
		}
	}
}
