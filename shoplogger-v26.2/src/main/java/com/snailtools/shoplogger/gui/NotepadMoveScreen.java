package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.notepad.Notepad;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the notepad's card to where it should sit on screen, and its corner to resize it; Esc or a click outside it is done. */
public class NotepadMoveScreen extends Screen {

	private final Screen parent;
	private int cardH = 40;
	private double grabX = -1, grabY;
	private boolean resizing;
	private static final int GRIP = 8;

	public NotepadMoveScreen(Screen parent) {
		super(Component.literal("Move the notepad"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x66000000);
		Draw.centered(g, font, "Drag the card to move it, its corner ◢ to resize it · Esc when done", width / 2, height - 20, Theme.TEXT);
		cardH = Notepad.drawCard(g, font, Notepad.cardX(), Notepad.cardY());
		int x = Notepad.cardX(), y = Notepad.cardY(), w = Notepad.cardWidth();
		g.fill(x - 1, y - 1, x + w + 1, y, Theme.ACCENT);
		// the resize grip: a small triangle of dots in the bottom-right corner
		boolean over = overGrip(mouseX, mouseY) || resizing;
		int gx = x + w - 2, gy = y + cardH - 2;
		for (int r = 0; r < 3; r++) for (int c = 0; c <= r; c++) {
			int dx = gx - 2 - c * 3, dy = gy - 2 - (2 - r) * 3;
			g.fill(dx, dy, dx + 2, dy + 2, over ? Theme.ACCENT : Theme.MUTED);
		}
		if (resizing) Draw.centered(g, font, w + " wide · " + Notepad.cardRows() + " lines", x + w / 2, y + cardH + 4, Theme.ACCENT);
	}

	private boolean overGrip(double mx, double my) {
		int x = Notepad.cardX() + Notepad.cardWidth(), y = Notepad.cardY() + cardH;
		return mx >= x - GRIP - 2 && mx < x + 2 && my >= y - GRIP - 2 && my < y + 2;
	}

	private boolean overCard(double x, double y) {
		return x >= Notepad.cardX() && x < Notepad.cardX() + Notepad.cardWidth() && y >= Notepad.cardY() && y < Notepad.cardY() + cardH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (overGrip(event.x(), event.y())) { resizing = true; return true; }
		if (!overCard(event.x(), event.y())) { onClose(); return true; }
		grabX = event.x() - Notepad.cardX();
		grabY = event.y() - Notepad.cardY();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (resizing) {
			int w = (int) (event.x() - Notepad.cardX());
			int rows = (int) Math.round((event.y() - Notepad.cardY() - 16) / 10.0);
			Notepad.resizeCard(Math.min(w, width - Notepad.cardX()), rows);
			return true;
		}
		if (grabX < 0) return false;
		int x = (int) Math.max(0, Math.min(width - Notepad.cardWidth(), event.x() - grabX));
		int y = (int) Math.max(0, Math.min(height - cardH, event.y() - grabY));
		Notepad.moveCard(x, y);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		grabX = -1;
		resizing = false;
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
