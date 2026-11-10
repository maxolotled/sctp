package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.notepad.Notepad;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One shopping list line: change how many, what you've got, teleport to the cheapest shop, or remove it. */
public class NotepadItemScreen extends UiScreen {

	private final Notepad.Need need;
	private UiField needField, gotField;

	public NotepadItemScreen(UiScreen parent, Notepad.Need need) {
		super(need.name, parent, parent.section());
		this.need = need;
	}

	@Override
	protected String crumb() { return "Notepad › " + need.name; }

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY() + 20, w = Math.min(contentW(), 340);
		int fw = 100;
		needField = new UiField(font, x + 110, y, fw, 20, "How many", false);
		needField.box.setValue(String.valueOf(need.need));
		needField.box.setResponder(s -> { need.need = parse(s, need.need); Notepad.changed(); });
		addRenderableOnly(needField.frame());
		addRenderableWidget(needField.box);

		gotField = new UiField(font, x + 110, y + 26, fw, 20, "Got", false);
		gotField.box.setValue(String.valueOf(need.got));
		gotField.box.setResponder(s -> { need.got = parse(s, need.got); Notepad.changed(); });
		addRenderableOnly(gotField.frame());
		addRenderableWidget(gotField.box);

		int by = y + 60, bx = x;
		bx += button(bx, by, "TP to cheapest", UiButton.Style.PRIMARY, () -> Notepad.teleportToCheapest(need)) + 4;
		bx += button(bx, by, need.done ? "Not bought yet" : "Tick off", UiButton.Style.GHOST, () -> { need.done = !need.done; Notepad.changed(); rebuild(); }) + 4;
		button(bx, by, "Remove", UiButton.Style.DANGER, () -> { Notepad.remove(need); onClose(); });
	}

	private static int parse(String s, int fallback) {
		try { return Math.max(0, Integer.parseInt(s.trim())); } catch (NumberFormatException e) { return fallback; }
	}

	private int button(int x, int y, String label, UiButton.Style style, Runnable onPress) {
		int bw = font.width(label) + 16;
		addRenderableWidget(new UiButton(x, y, bw, 20, label, style, onPress));
		return bw;
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY();
		g.text(font, Draw.trim(font, need.name, contentW()), x, y + 4, Theme.TEXT, false);
		g.text(font, "Need", x, y + 26, Theme.MUTED, false);
		g.text(font, "Got so far", x, y + 52, Theme.MUTED, false);
		g.text(font, need.complete() ? "Bought ✓" : Notepad.fmt(need.left()) + " left", x + 220, y + 26, need.complete() ? Theme.ACCENT : Theme.TEXT, false);
		g.text(font, Draw.trim(font, "\"Got\" goes up by itself as the item arrives in your inventory.", contentW()), x, y + 108, Theme.FAINT, false);
	}
}
