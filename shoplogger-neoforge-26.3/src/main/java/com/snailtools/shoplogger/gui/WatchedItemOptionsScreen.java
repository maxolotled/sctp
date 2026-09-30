package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchedItem;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.SettingRow;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Options for one watched item: a max price (entered in diamond blocks)
 * and whether to skip display/no-price listings — or stop watching it.
 */
public class WatchedItemOptionsScreen extends UiScreen {

	// Entered/shown in diamond blocks, stored in diamonds like every other price in the mod.
	private static final double DIAMONDS_PER_BLOCK = 9.0;
	private static final int[] QUICK_PRICES = {1, 5, 10, 15, 20, 25, 32, 48};
	private static final int CARD_W = 300;

	private final WatchedItem item;
	private UiField maxPrice;
	private String keepPrice;
	private boolean excludeNoPriceOrDisplay;
	private int cardX, cardY, cardH;

	public WatchedItemOptionsScreen(Screen parent, WatchedItem item) {
		super(item.itemName, parent, Section.WATCHLIST);
		this.item = item;
		this.excludeNoPriceOrDisplay = item.excludeNoPriceOrDisplay;
		this.keepPrice = item.maxPrice != null ? formatPrice(item.maxPrice / DIAMONDS_PER_BLOCK) : "";
	}

	@Override
	protected void initContent() {
		int w = Math.min(CARD_W, contentW());
		cardH = 164;
		cardX = (width - w) / 2;
		cardY = Math.max(contentY(), contentY() + (contentH() - cardH) / 2);
		int x = cardX + 12, iw = w - 24;
		int y = cardY + 44;

		maxPrice = new UiField(font, x, y, iw, 20, "No limit", false).suffix("diamond blocks", font);
		maxPrice.box.setMaxLength(10);
		maxPrice.box.setValue(keepPrice);
		maxPrice.box.setResponder(s -> keepPrice = s);
		addRenderableOnly(maxPrice.frame());
		addRenderableWidget(maxPrice.box);
		y += 24;

		// quick prices, in diamond blocks like the field
		int gap = 3;
		int qw = (iw - (QUICK_PRICES.length - 1) * gap) / QUICK_PRICES.length;
		int qx = x;
		for (int price : QUICK_PRICES) {
			addRenderableWidget(new UiButton(qx, y, qw, 14, Integer.toString(price), UiButton.Style.SECONDARY,
					() -> maxPrice.box.setValue(Integer.toString(price))).tooltip(price + " diamond blocks = " + price * 9 + " diamonds"));
			qx += qw + gap;
		}
		y += 22;

		SettingRow skip = SettingRow.toggle("Skip display / no-price listings", "Ignore signs that don't sell anything",
				() -> excludeNoPriceOrDisplay, v -> excludeNoPriceOrDisplay = v);
		skip.setX(x);
		skip.setY(y);
		skip.setWidth(iw);
		addRenderableWidget(skip);
		y += SettingRow.HEIGHT + 12;

		int bw = (iw - 8) / 3;
		addRenderableWidget(new UiButton(x, y, bw, 20, "Stop watching", UiButton.Style.DANGER, this::remove));
		addRenderableWidget(new UiButton(x + bw + 4, y, bw, 20, "Cancel", UiButton.Style.SECONDARY, this::onClose));
		addRenderableWidget(new UiButton(x + 2 * (bw + 4), y, iw - 2 * (bw + 4), 20, "Save", UiButton.Style.PRIMARY, this::save));
		cardH = y + 20 + 12 - cardY;

		setInitialFocus(maxPrice.box);
	}

	private static String formatPrice(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	/** Blank/zero/invalid all mean "no limit" rather than an error. */
	private static Double parsePrice(String text) {
		text = text == null ? "" : text.trim();
		if (text.isEmpty()) return null;
		try {
			double v = Double.parseDouble(text);
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void save() {
		Double blocks = parsePrice(maxPrice.value());
		Double diamonds = blocks == null ? null : blocks * DIAMONDS_PER_BLOCK;
		WatchlistStore.updateOptions(item.itemName, diamonds, excludeNoPriceOrDisplay);
		ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Updated watchlist options for " + item.itemName + ".");
		onClose();
	}

	private void remove() {
		WatchlistStore.remove(item.itemName);
		ChatFormat.send(minecraft, ChatFormat.NEUTRAL, "Stopped watching " + item.itemName + ".");
		onClose();
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int w = Math.min(CARD_W, contentW());
		Draw.shadow(g, cardX, cardY, w, cardH);
		Draw.card(g, cardX, cardY, w, cardH, Theme.PANEL, Theme.LINE);
		g.text(font, "WATCHING", cardX + 12, cardY + 9, Theme.FAINT, false);
		Draw.textShadow(g, font, Draw.trim(font, item.itemName, w - 24), cardX + 12, cardY + 19, Theme.TEXT);
		g.text(font, "MAX PRICE PER ITEM", cardX + 12, cardY + 34, Theme.FAINT, false);
		Double blocks = parsePrice(maxPrice == null ? keepPrice : maxPrice.value());
		String hint = blocks == null ? "Alerts for any price" : "= " + formatPrice(blocks * DIAMONDS_PER_BLOCK) + " diamonds per item";
		Draw.right(g, font, hint, cardX + w - 12, cardY + 34, blocks == null ? Theme.FAINT : Theme.ACCENT);
	}
}
