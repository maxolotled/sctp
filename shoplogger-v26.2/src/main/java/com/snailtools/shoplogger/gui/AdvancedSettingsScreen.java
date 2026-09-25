package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.RareRentalHighlighter;
import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.SearchPreferences;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.TempScanWaitOverlay;
import com.snailtools.shoplogger.TeleportHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Less-commonly-touched settings, split out of SettingsScreen (reachable via
 * its "Advanced settings..." button) to keep that screen's two columns from
 * growing a third.
 */
public class AdvancedSettingsScreen extends Screen {

	private static final int ROW_COUNT = 8; // chat log format, cooldown field, beam style, /search opens, shop info on visit, rare highlights in shulkers, scan wait overlay (TEMP, see CHANGELOG 2.2), Back
	private static final int NATURAL_GAP = 24;
	private static final int MIN_GAP = 16; // never shrink spacing below this — rows start overlapping past this point
	private static final int TOP_Y = 50;
	private static final int BOTTOM_MARGIN = 10;
	private static final int COL_W = 220;

	private final Screen parent;
	private EditBox cooldownField;

	public AdvancedSettingsScreen(Screen parent) {
		super(Component.literal("Advanced Settings"));
		this.parent = parent;
	}

	/** Same idea as SettingsScreen's computeLayout() — single source of truth for row Ys, so "does this fit?" and the real placement can't disagree. */
	private int[] computeRowY(int gap) {
		int[] rowY = new int[ROW_COUNT];
		int y = TOP_Y;
		for (int i = 0; i < ROW_COUNT; i++) {
			rowY[i] = y;
			// Extra room after rows 0 and 1 (the cooldown field's own label is
			// drawn 10px above it, so both it and the row before it need a
			// bigger gap than usual to avoid overlapping that label) and after
			// row 6, before Back, for the same visual grouping the screen had
			// before this became data-driven.
			y += (i == 0 || i == 1 || i == 6) ? gap + 12 : gap;
		}
		return rowY;
	}

	@Override
	protected void init() {
		int x = width / 2 - COL_W / 2;

		int gap = NATURAL_GAP;
		while (gap > MIN_GAP && computeRowY(gap)[ROW_COUNT - 1] + 20 > height - BOTTOM_MARGIN) gap--;
		int[] rowY = computeRowY(gap);

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "Single line" : "Multiple lines"), ScanChatLogger.isSingleLine())
				.withValues(Boolean.FALSE, Boolean.TRUE)
				.create(x, rowY[0], COL_W, 20, Component.literal("Chat log format"),
						(btn, value) -> ScanChatLogger.setSingleLine(value)));

		cooldownField = new EditBox(font, x, rowY[1], COL_W, 20, Component.literal("Cooldown (minutes)"));
		cooldownField.setValue(Integer.toString((int) (ShopAutoScanner.getPerShopCooldownMs() / 60000L)));
		cooldownField.setResponder(s -> {
			try {
				int minutes = Integer.parseInt(s);
				if (minutes > 0) ShopAutoScanner.setPerShopCooldownMinutes(minutes);
			} catch (NumberFormatException ignored) {
				// not a full number yet — wait for more input
			}
		});
		addRenderableWidget(cooldownField);

		addRenderableWidget(CycleButton.builder((TeleportHighlight.BeamStyle v) -> Component.literal(v.label), TeleportHighlight.getStyle())
				.withValues(TeleportHighlight.BeamStyle.values())
				.create(x, rowY[2], COL_W, 20, Component.literal("Teleport beam style"),
						(btn, value) -> TeleportHighlight.setStyle(value)));

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "GUI" : "Chat"), SearchPreferences.isGuiSearch())
				.withValues(Boolean.TRUE, Boolean.FALSE)
				.create(x, rowY[3], COL_W, 20, Component.literal("/search opens"),
						(btn, value) -> SearchPreferences.setGuiSearch(value)));

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isShopInfoOnVisitEnabled())
				.create(x, rowY[4], COL_W, 20, Component.literal("Shop info on visit"),
						(btn, value) -> ShopVisitAlert.setShopInfoOnVisitEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(RareRentalHighlighter.isInShulkersEnabled())
				.create(x, rowY[5], COL_W, 20, Component.literal("Rare highlights in shulkers"),
						(btn, value) -> RareRentalHighlighter.setInShulkersEnabled(value)));

		// TEMPORARY (see CHANGELOG 2.2) — remove this row along with TempScanWaitOverlay
		// once 2.2's accuracy check is done.
		addRenderableWidget(CycleButton.onOffBuilder(TempScanWaitOverlay.isEnabled())
				.create(x, rowY[6], COL_W, 20, Component.literal("Show scan wait (temp)"),
						(btn, value) -> TempScanWaitOverlay.setEnabled(value)));

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(x, rowY[7], COL_W, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
		// EditBox has no built-in visible label (its Component constructor arg is
		// narration-only), unlike the toggle buttons above which show "Label: value"
		// on their own — so this one needs an explicit label drawn above it.
		context.text(font, "Recently-scanned cooldown, in minutes:", cooldownField.getX(), cooldownField.getY() - 10, 0xFF8FA593);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
