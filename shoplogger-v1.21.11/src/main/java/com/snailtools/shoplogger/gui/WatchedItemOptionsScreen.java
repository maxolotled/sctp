package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchedItem;
import com.snailtools.shoplogger.WatchlistStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Opened by clicking an already-watched item in WatchlistScreen — set a max
 * price (in diamonds-equivalent) and/or skip display/no-price listings for
 * just this item, or stop watching it entirely.
 */
public class WatchedItemOptionsScreen extends Screen {

	private final Screen parent;
	private final WatchedItem item;
	private TextFieldWidget maxPriceField;
	private boolean excludeNoPriceOrDisplay;

	public WatchedItemOptionsScreen(Screen parent, WatchedItem item) {
		super(Text.literal(item.itemName));
		this.parent = parent;
		this.item = item;
		this.excludeNoPriceOrDisplay = item.excludeNoPriceOrDisplay;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int w = 220;
		int y = height / 2 - 50;

		maxPriceField = new TextFieldWidget(textRenderer, centerX - w / 2, y, w, 20, Text.literal("Max price"));
		maxPriceField.setMaxLength(10);
		if (item.maxPrice != null) maxPriceField.setText(formatPrice(item.maxPrice));
		addDrawableChild(maxPriceField);
		y += 28;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(excludeNoPriceOrDisplay)
				.build(centerX - w / 2, y, w, 20, Text.literal("Skip display/no-price"),
						(btn, value) -> excludeNoPriceOrDisplay = value));
		y += 28;

		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> save())
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Stop watching"), btn -> remove())
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(centerX - w / 2, y, w, 20).build());
	}

	private static String formatPrice(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	/** Blank/zero/invalid all mean "no cap" rather than erroring — this is a quick in-game field, not a form with validation messages. */
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
		WatchlistStore.updateOptions(item.itemName, parsePrice(maxPriceField.getText()), excludeNoPriceOrDisplay);
		ChatFormat.send(client, ChatFormat.SUCCESS, "Updated watchlist options for " + item.itemName + ".");
		close();
	}

	private void remove() {
		WatchlistStore.remove(item.itemName);
		ChatFormat.send(client, ChatFormat.NEUTRAL, "Stopped watching " + item.itemName + ".");
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 92, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, "Max price is in diamonds-equivalent (1 diamond block = 9)", width / 2, height / 2 - 78, 0xFF8FA593);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
