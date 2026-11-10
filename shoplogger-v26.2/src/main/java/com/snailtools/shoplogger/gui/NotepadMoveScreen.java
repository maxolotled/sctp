package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.notepad.Notepad;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the notepad's card to where it should sit on screen; Esc or a click outside it is done. */
public class NotepadMoveScreen extends Screen {

	private final Screen parent;
	private int cardH = 40;
	private double grabX = -1, grabY;

	public NotepadMoveScreen(Screen parent) {
		super(Component.literal("Move the notepad"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x66000000);
		Draw.centered(g, font, "Drag the card to where you want it · Esc when done", width / 2, height - 20, Theme.TEXT);
		cardH = Notepad.drawCard(g, font, Notepad.cardX(), Notepad.cardY());
		g.fill(Notepad.cardX() - 1, Notepad.cardY() - 1, Notepad.cardX() + Notepad.cardWidth() + 1, Notepad.cardY(), Theme.ACCENT);
	}

	private boolean overCard(double x, double y) {
		return x >= Notepad.cardX() && x < Notepad.cardX() + Notepad.cardWidth() && y >= Notepad.cardY() && y < Notepad.cardY() + cardH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!overCard(event.x(), event.y())) { onClose(); return true; }
		grabX = event.x() - Notepad.cardX();
		grabY = event.y() - Notepad.cardY();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (grabX < 0) return false;
		int x = (int) Math.max(0, Math.min(width - Notepad.cardWidth(), event.x() - grabX));
		int y = (int) Math.max(0, Math.min(height - cardH, event.y() - grabY));
		Notepad.moveCard(x, y);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		grabX = -1;
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
