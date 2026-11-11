package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.gui.data.RareIcons;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import com.snailtools.shoplogger.notepad.Notepad;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The notepad (default key N): a shopping list that ticks itself off as the
 * items arrive in your inventory, with a TP to the cheapest shop for each line,
 * and a page of notes. Both can stay on screen as a small card. See Notepad.
 */
public class NotepadScreen extends UiScreen {

	private static boolean notesPage; // kept while the game runs

	private ItemListWidget list;
	private UiField noteField;
	private int listX, listY, listW, listH;

	public NotepadScreen(Screen parent) {
		super("Notepad", parent, Section.NOTEPAD);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		addRenderableWidget(new UiButton(x, y, 100, 18, "Shopping list", UiButton.Style.TAB, () -> { notesPage = false; rebuild(); }).selected(() -> !notesPage));
		addRenderableWidget(new UiButton(x + 104, y, 70, 18, "Notes", UiButton.Style.TAB, () -> { notesPage = true; rebuild(); }).selected(() -> notesPage));

		// on screen: shown or not, and moving it
		String showLabel = Notepad.onScreen() && Notepad.screenShowsNotes() == notesPage ? "Hide from screen" : "Show on screen";
		int sw = font.width(showLabel) + 16, mw = font.width("Move") + 16;
		addRenderableWidget(new UiButton(x + w - sw - mw - 4, y, sw, 18, showLabel, UiButton.Style.GHOST, () -> {
			boolean shownHere = Notepad.onScreen() && Notepad.screenShowsNotes() == notesPage;
			Notepad.setOnScreen(!shownHere);
			Notepad.setScreenShowsNotes(notesPage);
			rebuild();
		}));
		addRenderableWidget(new UiButton(x + w - mw, y, mw, 18, "Move", UiButton.Style.GHOST, () -> {
			Notepad.setScreenShowsNotes(notesPage);
			Notepad.setOnScreen(true);
			minecraft.setScreenAndShow(new NotepadMoveScreen(this));
		}));

		int ay = y + 24;
		if (notesPage) {
			int bw = 60;
			noteField = new UiField(font, x, ay, w - bw - 6, 20, "Write a note, e.g. \"Holding Snaruto for Tina\", then press Enter…", false);
			addRenderableOnly(noteField.frame());
			addRenderableWidget(noteField.box);
			addRenderableWidget(new UiButton(x + w - bw, ay, bw, 20, "Add", UiButton.Style.PRIMARY, this::addNote));
			setInitialFocus(noteField.box);
		} else {
			int bx = x;
			bx += button(bx, ay, "Add item", UiButton.Style.PRIMARY, () -> minecraft.setScreenAndShow(new NotepadAddScreen(this))) + 4;
			bx += button(bx, ay, "Paste code from /list", UiButton.Style.GHOST, this::pasteCode) + 4;
			bx += button(bx, ay, "From Litematica", UiButton.Style.GHOST, () -> { say(Notepad.importLitematica()); rebuild(); }) + 4;
			button(bx, ay, "Clear bought", UiButton.Style.GHOST, () -> { Notepad.clearDone(); rebuild(); });
		}

		// the bottom bar: the order (shopping list), and clearing the page
		int barY = contentBottom() - 20;
		if (!notesPage) {
			addRenderableWidget(new UiChip<>(x, barY + 1, Math.min(170, w / 3), 18, "Sort", Notepad.SORTS, Notepad.sort(), v -> v, v -> {
				Notepad.setSort(v);
				if (Notepad.sortsByPrice()) Notepad.loadListings(this::refillIfOpen);
				fill();
			}));
		}
		boolean empty = notesPage ? Notepad.notes().isEmpty() : Notepad.shopping().isEmpty();
		String clearLabel = notesPage ? "Clear notes" : "Clear shopping list";
		int cw = font.width("Click again to clear") + 16;
		clearButton = new UiButton(x + w - cw, barY, cw, 20, clearLabel, UiButton.Style.DANGER, () -> {
			if (System.currentTimeMillis() - clearArmedAt > 3000L) {
				// one more click within 3 seconds really clears it
				clearArmedAt = System.currentTimeMillis();
				clearButton.setMessage(net.minecraft.network.chat.Component.literal("Click again to clear"));
				return;
			}
			clearArmedAt = 0;
			if (notesPage) Notepad.clearNotes(); else Notepad.clearShopping();
			rebuild();
		});
		clearButton.active = !empty;
		clearButton.tooltip(notesPage ? "Deletes every note" : "Removes every line, bought or not");
		addRenderableWidget(clearButton);

		listX = x - 6;
		listY = ay + 30;
		listW = w + 12;
		listH = barY - 6 - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);
		fill();
		// the prices on each line (and the price sorts) need the listings
		if (!notesPage && !Notepad.shopping().isEmpty()) Notepad.loadListings(this::refillIfOpen);
	}

	private UiButton clearButton;
	private long clearArmedAt;

	private void refillIfOpen() {
		if (minecraft.gui.screen() == this && list != null) fill();
	}

	@Override
	public void tick() {
		super.tick();
		if (clearButton != null && clearArmedAt != 0 && System.currentTimeMillis() - clearArmedAt > 3000L) {
			clearArmedAt = 0;
			clearButton.setMessage(net.minecraft.network.chat.Component.literal(notesPage ? "Clear notes" : "Clear shopping list"));
		}
	}

	private int button(int x, int y, String label, UiButton.Style style, Runnable onPress) {
		int bw = font.width(label) + 16;
		addRenderableWidget(new UiButton(x, y, bw, 20, label, style, onPress));
		return bw;
	}

	private void say(String s) { ChatFormat.send(minecraft, ChatFormat.INFO, s); }

	private void addNote() {
		if (noteField == null || noteField.value().isBlank()) return;
		Notepad.addNote(noteField.value());
		noteField.box.setValue("");
		fill();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (notesPage && noteField != null && noteField.box.isFocused() && (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
			addNote();
			return true;
		}
		return super.keyPressed(event);
	}

	private void pasteCode() {
		int n = Notepad.importCode(minecraft.keyboardHandler.getClipboard());
		if (n < 0) say("Copy the code from sctp.nl/list first (\"Copy for the notepad\" under your list), then press Paste again.");
		else say("Added " + n + " item" + (n == 1 ? "" : "s") + " from the code.");
		rebuild();
	}

	private void fill() {
		list.clearAllEntries();
		if (notesPage) {
			for (Notepad.Note n : Notepad.notes()) {
				String icon = n.icon != null ? n.icon : n.done ? "minecraft:book" : "minecraft:writable_book";
				ItemListWidget.ItemEntry e = ItemListWidget.forVanilla((n.done ? "✓ " : "") + n.text, icon,
						n.done ? "Done · click to undo" : "Click to tick it off", () -> { n.done = !n.done; Notepad.changed(); fill(); },
						() -> minecraft.setScreenAndShow(new IconPickerScreen(this, "this note", java.util.List.of(), id -> { n.icon = id; Notepad.changed(); })));
				e.withPageButton("Icon", "Pick an item to show next to this note (also on the card on screen)");
				e.withTag("✕", Theme.BAD, "Delete this note", () -> { Notepad.removeNote(n); fill(); });
				list.addItemEntry(e);
			}
			return;
		}
		for (Notepad.Need n : Notepad.ordered()) {
			String sub = n.complete() ? "Bought ✓" + (n.done && n.got < n.need ? " (ticked off)" : "")
					: Notepad.fmt(n.left()) + " left · " + Notepad.fmt(n.got) + " of " + Notepad.fmt(n.need) + priceNote(n);
			String texture = n.base == null && RareIcons.isLoaded() ? RareIcons.textureFor(n.name) : null;
			Runnable edit = () -> minecraft.setScreenAndShow(new NotepadItemScreen(this, n));
			Runnable tp = () -> Notepad.teleportToCheapest(n);
			ItemListWidget.ItemEntry e = texture != null ? ItemListWidget.forRare(n.name, sub, texture, edit, tp)
					: ItemListWidget.forVanilla(n.name, n.base, sub, edit, tp);
			e.withPageButton("TP", "Teleport to the cheapest shop for what's left (counting how much each shop sells at once)");
			e.withTag(n.complete() ? "✓" : "○", n.complete() ? Theme.ACCENT : Theme.FAINT, n.complete() ? "Bought · click to undo" : "Tick it off",
					() -> { if (n.complete()) { n.done = false; if (n.got >= n.need) n.got = 0; } else n.done = true; Notepad.changed(); fill(); });
			list.addItemEntry(e);
		}
	}

	/** What's in My storage, then what the rest costs: " · 40 in storage · about 3 dia", " · only 20 for sale", " · nobody sells it"... */
	private static String priceNote(Notepad.Need n) {
		int stored = Notepad.stored(n);
		if (stored >= n.left()) return " · all in My storage";
		String s = stored > 0 ? " · " + Notepad.fmt(stored) + " in storage" : "";
		com.snailtools.shoplogger.notepad.BuyPlan p = Notepad.plan(n);
		if (p == null) return s; // listings still loading
		if (p.empty()) return s + " · nobody sells it";
		if (!p.enough()) return s + " · only " + Notepad.fmt(p.items) + " for sale";
		return s + " · about " + Notepad.diamonds(p.diamonds) + (stored > 0 ? " for the rest" : "");
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (notesPage || Notepad.shopping().isEmpty()) return;
		int open = 0;
		double total = 0;
		boolean priced = true;
		for (Notepad.Need n : Notepad.shopping()) {
			if (n.complete()) continue;
			open++;
			com.snailtools.shoplogger.notepad.BuyPlan p = Notepad.plan(n);
			if (p == null) priced = false; else total += p.diamonds;
		}
		String text = open + " to buy · " + (Notepad.shopping().size() - open) + " bought" + (open > 0 && priced ? " · about " + Notepad.diamonds(total) + " in all" : "");
		// centred in the bottom bar, between the sort and the clear button
		int barY = contentBottom() - 20;
		int left = contentX() + Math.min(170, contentW() / 3) + 8, right = clearButton == null ? contentX() + contentW() : clearButton.getX() - 8;
		String shown = Draw.trim(font, text, Math.max(0, right - left));
		g.text(font, shown, left + (right - left - font.width(shown)) / 2, barY + 6, Theme.MUTED, false);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list == null || list.size() > 0) return;
		if (notesPage) Draw.emptyState(g, font, listX, listY, listW, listH, "No notes yet", "Type one above and press Enter.", Theme.MUTED);
		else Draw.emptyState(g, font, listX, listY, listW, listH, "Your shopping list is empty",
				"Add items, paste a code from sctp.nl/list, or take Litematica's material list. Lines tick themselves off as the items arrive in your inventory.", Theme.MUTED);
	}
}
