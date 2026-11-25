package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.AveragePrices;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The marketplace was removed at the request of the Snailcraft admins. The
 * tile on the home screen stays for now and leads here: a short note and a
 * button to SCTP's statement about it.
 */
public class MarketplaceScreen extends UiScreen {

	private static final int CARD_W = 340;
	private static final String TITLE = "The marketplace has been removed";
	private static final String MESSAGE = "The Snailcraft admins required us to remove the marketplace from the website and the mod. "
			+ "This wasn't our decision. Our statement explains what happened and why.";

	private List<FormattedCharSequence> lines = List.of();
	private int cardX, cardY, cardW, cardH;

	public MarketplaceScreen(Screen parent) {
		super("Marketplace", parent, Section.MARKET);
	}

	@Override
	protected void initContent() {
		cardW = Math.min(CARD_W, contentW());
		lines = font.split(Component.literal(MESSAGE), cardW - 28);
		cardH = 14 + 12 + 8 + lines.size() * 10 + 12 + 20 + 14;
		cardX = contentX() + (contentW() - cardW) / 2;
		cardY = contentY() + Math.max(10, (contentH() - cardH) / 3);
		String label = "Read our statement ↗";
		addRenderableWidget(new UiButton(cardX + 14, cardY + cardH - 14 - 20, font.width(label) + 24, 20, label, UiButton.Style.PRIMARY,
				() -> UiLinks.open(AveragePrices.STATEMENT_URL)));
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Draw.card(g, cardX, cardY, cardW, cardH, Theme.PANEL, Theme.LINE);
		Draw.rect(g, cardX, cardY, cardW, 3, Theme.BAD);
		Draw.textShadow(g, font, Draw.trim(font, TITLE, cardW - 28), cardX + 14, cardY + 14, Theme.TEXT);
		int y = cardY + 14 + 12 + 8;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, cardX + 14, y, Theme.MUTED, false);
			y += 10;
		}
	}
}
