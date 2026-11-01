package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.gui.data.RareIcons;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import com.snailtools.shoplogger.storage.StorageHighlight;
import com.snailtools.shoplogger.storage.StorageIndex;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

/** One container in My storage: what's in it, what went in and out lately, and where it is. */
public class StorageBoxScreen extends UiScreen {

	private final StorageIndex.Box box;
	private ItemListWidget list;
	private int listY;

	public StorageBoxScreen(Screen parent, StorageIndex.Box box) {
		super(box.label(), parent, Section.STORAGE);
		this.box = box;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int bx = x + w;
		String rm = "Remove from My storage";
		int rw = font.width(rm) + 16;
		bx -= rw;
		addRenderableWidget(new UiButton(bx, y, rw, 18, rm, UiButton.Style.SECONDARY, () -> {
			StorageIndex.remove(box);
			ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Took " + box.label() + " out of My storage.");
			onClose();
		}));
		if (!box.isEnder()) {
			String hl = "Highlight it";
			int hw = font.width(hl) + 16;
			bx -= hw + 6;
			addRenderableWidget(new UiButton(bx, y, hw, 18, hl, UiButton.Style.PRIMARY, () -> StorageHighlight.show(box.label(), List.of(box))));
		}

		listY = y + 40;
		list = new ItemListWidget(minecraft, x - 6, listY, w + 12, contentBottom() - listY);
		addRenderableWidget(list);
		for (StorageIndex.Item i : box.items) {
			String texture = RareIcons.isLoaded() ? RareIcons.textureFor(i.n) : null;
			String sub = String.format(Locale.ROOT, "%,d", i.c);
			list.addItemEntry(texture != null ? ItemListWidget.forRare(i.n, sub, texture, null) : ItemListWidget.forVanilla(i.n, i.b, sub, null));
		}
		long now = System.currentTimeMillis();
		for (StorageIndex.Change c : box.log) {
			String what = (c.d > 0 ? "+" : "−") + String.format(Locale.ROOT, "%,d", Math.abs(c.d)) + " " + c.n;
			list.addItemEntry(ItemListWidget.forVanilla(what, c.b, (c.d > 0 ? "Put in · " : "Taken out · ") + ago(now - c.at), null)
					.withBadge(c.d > 0 ? "In" : "Out", c.d > 0 ? Theme.ACCENT : Theme.WARN));
		}
	}

	private static String ago(long ms) {
		long m = ms / 60000;
		if (m < 1) return "just now";
		if (m < 60) return m + "m ago";
		long h = m / 60;
		return h < 24 ? h + "h ago" : (h / 24) + "d ago";
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY();
		String[] s = StorageIndex.summary(box);
		g.text(font, box.isEnder() ? "Your ender chest" : box.where() + " · " + box.dim.replace("minecraft:", ""), x, y + 5, Theme.TEXT, false);
		String line = box.waiting() ? "Not read yet: walk within reach of it" : s[0] + (s[1].isEmpty() ? "" : " · " + s[1]) + " · read " + ago(System.currentTimeMillis() - box.scannedAt);
		g.text(font, Draw.trim(font, line, contentW() / 2), x, y + 18, Theme.MUTED, false);
		g.text(font, "INSIDE" + (box.log.isEmpty() ? "" : " · THEN RECENT CHANGES"), x, listY - 10, Theme.FAINT, false);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list != null && list.size() == 0) {
			Draw.emptyState(g, font, contentX() - 6, listY, contentW() + 12, contentBottom() - listY,
					box.waiting() ? "Not read yet" : "Empty", box.waiting() ? "It's read the first time you're within reach." : "Nothing in it right now.", Theme.MUTED);
		}
	}
}
