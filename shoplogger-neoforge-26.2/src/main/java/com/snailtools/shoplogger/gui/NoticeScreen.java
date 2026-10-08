package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A small popup over the game (see NoticePopups): a title, a short message,
 * one button that opens a link, and an X. Used for "welcome", "updated" and
 * "update available" instead of chat lines that scroll away unnoticed.
 */
public class NoticeScreen extends Screen {

	private static final int CARD_W = 280;
	private static final int PAD = 14;

	private final String heading;
	private final String message;
	private final String buttonLabel;
	private final String url;
	private final Runnable afterClose;
	private List<FormattedCharSequence> lines = List.of();
	private int cardX, cardY, cardH;

	public NoticeScreen(String heading, String message, String buttonLabel, String url, Runnable afterClose) {
		super(Component.literal(heading));
		this.heading = heading;
		this.message = message;
		this.buttonLabel = buttonLabel;
		this.url = url;
		this.afterClose = afterClose;
	}

	@Override
	protected void init() {
		int w = Math.min(CARD_W, width - 20);
		lines = font.split(Component.literal(message), w - PAD * 2);
		cardH = PAD + 12 + 8 + lines.size() * 10 + 12 + 20 + PAD;
		cardX = (width - w) / 2;
		cardY = (height - cardH) / 2;

		addRenderableWidget(new UiButton(cardX + w - PAD - 12, cardY + 8, 16, 16, "×", UiButton.Style.GHOST, this::onClose).tooltip("Close (Esc)"));
		if (url != null && !url.isEmpty()) {
			int bw = Math.min(w - PAD * 2, font.width(buttonLabel) + 28);
			addRenderableWidget(new UiButton(cardX + PAD, cardY + cardH - PAD - 20, bw, 20, buttonLabel, UiButton.Style.PRIMARY, () -> {
				UiLinks.open(url);
				onClose();
			}));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int w = Math.min(CARD_W, width - 20);
		Draw.shadow(g, cardX, cardY, w, cardH);
		Draw.card(g, cardX, cardY, w, cardH, Theme.PANEL, Theme.ACCENT_DIM);
		Draw.rect(g, cardX, cardY, w, 3, Theme.ACCENT);
		Draw.textShadow(g, font, Draw.trim(font, heading, w - PAD * 2 - 18), cardX + PAD, cardY + PAD, Theme.TEXT);
		int y = cardY + PAD + 12 + 8;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, cardX + PAD, y, Theme.MUTED, false);
			y += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		super.onClose();
		if (afterClose != null) afterClose.run();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
