package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchedItem;
import com.snailtools.shoplogger.WatchlistStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Opened by clicking an already-watched item in WatchlistScreen — set a max
 * price (entered in diamond blocks) and/or skip display/no-price listings
 * for just this item, or stop watching it entirely.
 */
public class WatchedItemOptionsScreen extends Screen {

	// The field is entered/shown in diamond blocks (the more natural unit for
	// a price cap), but WatchedItem.maxPrice is still stored in diamonds
	// internally, matching every price comparison elsewhere in the mod.
	private static final double DIAMONDS_PER_BLOCK = 9.0;

	private final Screen parent;
	private final WatchedItem item;
	private EditBox maxPriceField;
	private boolean excludeNoPriceOrDisplay;

	public WatchedItemOptionsScreen(Screen parent, WatchedItem item) {
		super(Component.literal(item.itemName));
		this.parent = parent;
		this.item = item;
		this.excludeNoPriceOrDisplay = item.excludeNoPriceOrDisplay;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int w = 220;
		int y = height / 2 - 50;

		maxPriceField = new EditBox(font, centerX - w / 2, y, w, 20, Component.literal("Max price (diamond blocks)"));
		maxPriceField.setMaxLength(10);
		if (item.maxPrice != null) maxPriceField.setValue(formatPrice(item.maxPrice / DIAMONDS_PER_BLOCK));
		addRenderableWidget(maxPriceField);
		y += 28;

		addRenderableWidget(CycleButton.onOffBuilder(excludeNoPriceOrDisplay)
				.create(centerX - w / 2, y, w, 20, Component.literal("Skip display/no-price"),
						(btn, value) -> excludeNoPriceOrDisplay = value));
		y += 28;

		addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Stop watching"), btn -> remove())
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(centerX - w / 2, y, w, 20).build());
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
		Double maxPriceBlocks = parsePrice(maxPriceField.getValue());
		Double maxPriceDiamonds = maxPriceBlocks == null ? null : maxPriceBlocks * DIAMONDS_PER_BLOCK;
		WatchlistStore.updateOptions(item.itemName, maxPriceDiamonds, excludeNoPriceOrDisplay);
		ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Updated watchlist options for " + item.itemName + ".");
		onClose();
	}

	private void remove() {
		WatchlistStore.remove(item.itemName);
		ChatFormat.send(minecraft, ChatFormat.NEUTRAL, "Stopped watching " + item.itemName + ".");
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, height / 2 - 92, 0xFFFFFFFF);
		context.centeredText(font, "Max price is in diamond blocks (e.g. 2 = 18 diamonds)", width / 2, height / 2 - 78, 0xFF8FA593);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
