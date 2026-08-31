package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.CsvExporter;
import com.snailtools.shoplogger.ExcelExporter;
import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.SearchPreferences;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopLog;
import com.snailtools.shoplogger.ShopMarkerRenderer;
import com.snailtools.shoplogger.ShopUploader;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.TeleportHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Everything that used to be hotkey-only, plus the two new settings
 * (chat-log format, recently-scanned cooldown), gathered in one screen.
 * Reachable from HomeScreen — the hotkeys themselves keep working unchanged,
 * this is just an additional way to reach the same actions.
 */
public class SettingsScreen extends Screen {

	private final Screen parent;
	private EditBox cooldownField;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Shop Logger Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int rowW = 220;
		int y = height / 2 - 100;
		int gap = 24;

		addRenderableWidget(CycleButton.onOffBuilder(ShopAutoScanner.getInstance().isEnabled())
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("Auto-scan"),
						(btn, value) -> ShopAutoScanner.getInstance().setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ShopMarkerRenderer.getInstance().isEnabled())
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("Recently-scanned markers"),
						(btn, value) -> ShopMarkerRenderer.getInstance().setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ScanChatLogger.isEnabled())
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("Print scans in chat"),
						(btn, value) -> ScanChatLogger.setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isRaresOnly())
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("New-item alerts: rares only"),
						(btn, value) -> ShopVisitAlert.setRaresOnly(value)));
		y += gap;

		addRenderableWidget(CycleButton.builder((TeleportHighlight.BeamStyle v) -> Component.literal(v.label), TeleportHighlight.getStyle())
				.withValues(TeleportHighlight.BeamStyle.values())
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("Teleport beam style"),
						(btn, value) -> TeleportHighlight.setStyle(value)));
		y += gap;

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "Single line" : "Multiple lines"), ScanChatLogger.isSingleLine())
				.withValues(Boolean.FALSE, Boolean.TRUE)
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("Chat log format"),
						(btn, value) -> ScanChatLogger.setSingleLine(value)));
		y += gap;

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "GUI" : "Chat"), SearchPreferences.isGuiSearch())
				.withValues(Boolean.TRUE, Boolean.FALSE)
				.create(centerX - rowW / 2, y, rowW, 20, Component.literal("/search opens"),
						(btn, value) -> SearchPreferences.setGuiSearch(value)));
		// Extra room here (vs. the plain "gap" used between the on/off toggles
		// above) because the cooldown field's label is drawn 10px above it —
		// a plain gap left that label overlapping this row's button.
		y += gap + 12;

		cooldownField = new EditBox(font, centerX - rowW / 2, y, rowW, 20, Component.literal("Cooldown (minutes)"));
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
		y += gap + 12;

		addRenderableWidget(Button.builder(Component.literal("Export now (CSV + Excel)"), btn -> exportBoth())
				.bounds(centerX - rowW / 2, y, rowW, 20).build());
		y += gap;

		addRenderableWidget(Button.builder(Component.literal("Upload now to Trading Post"), btn -> ShopUploader.uploadAsync(minecraft, true))
				.bounds(centerX - rowW / 2, y, rowW, 20).build());
		y += gap + 12;

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(centerX - rowW / 2, y, rowW, 20).build());
	}

	private void exportBoth() {
		try {
			Path runDir = minecraft.gameDirectory.toPath();
			Path csvOut = runDir.resolve("shoplogger").resolve("shops.csv");
			Path xlsxOut = runDir.resolve("shoplogger").resolve("shops.xlsx");

			CsvExporter.export(ShopLog.getAll(), csvOut);
			ExcelExporter.export(ShopLog.getAll(), xlsxOut);

			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			ChatFormat.send(minecraft, ChatFormat.SUCCESS,
					"Exported " + ShopLog.size() + " entries at " + time + " -> run/shoplogger/");
		} catch (Exception e) {
			ChatFormat.send(minecraft, ChatFormat.ERROR, "Export failed: " + e.getMessage());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, height / 2 - 130, 0xFFFFFFFF);
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
