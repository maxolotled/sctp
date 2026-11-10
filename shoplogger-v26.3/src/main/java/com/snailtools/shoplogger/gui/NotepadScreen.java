package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.gui.data.RareIcons;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
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

		listX = x - 6;
		listY = ay + 30;
		listW = w + 12;
		listH = contentBottom() - listY;
		list = new ItemListWidget(minecraft, listX, listY, listW, listH);
		addRenderableWidget(list);
		fill();
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
				ItemListWidget.ItemEntry e = ItemListWidget.forVanilla((n.done ? "✓ " : "") + n.text, n.done ? "minecraft:book" : "minecraft:writable_book",
						n.done ? "Done · click to undo" : "Click to tick it off", () -> { n.done = !n.done; Notepad.changed(); fill(); });
				e.withTag("✕", Theme.BAD, "Delete this note", () -> { Notepad.removeNote(n); fill(); });
				list.addItemEntry(e);
			}
			return;
		}
		for (Notepad.Need n : Notepad.shopping()) {
			String sub = n.complete() ? "Bought ✓" + (n.done && n.got < n.need ? " (ticked off)" : "")
					: Notepad.fmt(n.left()) + " left · " + Notepad.fmt(n.got) + " of " + Notepad.fmt(n.need);
			String texture = n.base == null && RareIcons.isLoaded() ? RareIcons.textureFor(n.name) : null;
			Runnable edit = () -> minecraft.setScreenAndShow(new NotepadItemScreen(this, n));
			Runnable tp = () -> Notepad.teleportToCheapest(n);
			ItemListWidget.ItemEntry e = texture != null ? ItemListWidget.forRare(n.name, sub, texture, edit, tp)
					: ItemListWidget.forVanilla(n.name, n.base, sub, edit, tp);
			e.withPageButton("TP", "Teleport to the cheapest shop selling it");
			e.withTag(n.complete() ? "✓" : "○", n.complete() ? Theme.ACCENT : Theme.FAINT, n.complete() ? "Bought · click to undo" : "Tick it off",
					() -> { if (n.complete()) { n.done = false; if (n.got >= n.need) n.got = 0; } else n.done = true; Notepad.changed(); fill(); });
			list.addItemEntry(e);
		}
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (notesPage) return;
		int open = 0;
		for (Notepad.Need n : Notepad.shopping()) if (!n.complete()) open++;
		if (!Notepad.shopping().isEmpty()) {
			Draw.right(g, font, open + " to buy · " + (Notepad.shopping().size() - open) + " bought", contentX() + contentW(), contentY() + 54, Theme.MUTED);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list == null || list.size() > 0) return;
		if (notesPage) Draw.emptyState(g, font, listX, listY, listW, listH, "No notes yet", "Type one above and press Enter.", Theme.MUTED);
		else Draw.emptyState(g, font, listX, listY, listW, listH, "Your shopping list is empty",
				"Add items, paste a code from sctp.nl/list, or take Litematica's material list. Lines tick themselves off as the items arrive in your inventory.", Theme.MUTED);
	}
}
