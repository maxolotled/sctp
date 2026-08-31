package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.CsvExporter;
import com.snailtools.shoplogger.ExcelExporter;
import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopLog;
import com.snailtools.shoplogger.SearchPreferences;
import com.snailtools.shoplogger.ShopMarkerRenderer;
import com.snailtools.shoplogger.ShopUploader;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.TeleportHighlight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Everything that used to be hotkey-only, gathered in one screen. Laid out as
 * two side-by-side categories (Scanning / Search & Alerts) rather than one
 * long vertical list — the list outgrew a single column a while ago.
 * Reachable from HomeScreen — the hotkeys themselves keep working unchanged,
 * this is just an additional way to reach the same actions.
 */
public class SettingsScreen extends Screen {

	private final Screen parent;
	private TextFieldWidget cooldownField;
	private final List<HeaderLabel> headers = new ArrayList<>();

	private record HeaderLabel(String text, int x, int y) {}

	public SettingsScreen(Screen parent) {
		super(Text.literal("Shop Logger Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		headers.clear();
		int centerX = width / 2;
		int colW = 200;
		int colGap = 20;
		int leftX = centerX - colGap / 2 - colW;
		int rightX = centerX + colGap / 2;
		int gap = 24;
		int topY = 56;

		// ---- left column: Scanning ----
		int y = topY;
		headers.add(new HeaderLabel("Scanning", leftX, y));
		y += 16;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(ShopAutoScanner.getInstance().isEnabled())
				.build(leftX, y, colW, 20, Text.literal("Auto-scan"),
						(btn, value) -> ShopAutoScanner.getInstance().setEnabled(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(ShopMarkerRenderer.getInstance().isEnabled())
				.build(leftX, y, colW, 20, Text.literal("Recently-scanned markers"),
						(btn, value) -> ShopMarkerRenderer.getInstance().setEnabled(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(ScanChatLogger.isEnabled())
				.build(leftX, y, colW, 20, Text.literal("Print scans in chat"),
						(btn, value) -> ScanChatLogger.setEnabled(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.builder((Boolean v) -> Text.literal(v ? "Single line" : "Multiple lines"), ScanChatLogger.isSingleLine())
				.values(Boolean.FALSE, Boolean.TRUE)
				.build(leftX, y, colW, 20, Text.literal("Chat log format"),
						(btn, value) -> ScanChatLogger.setSingleLine(value)));
		// Extra room here (vs. the plain "gap" used between the toggles above)
		// because the cooldown field's label is drawn 10px above it — a plain
		// gap left that label overlapping this row's button.
		y += gap + 12;

		cooldownField = new TextFieldWidget(textRenderer, leftX, y, colW, 20, Text.literal("Cooldown (minutes)"));
		cooldownField.setText(Integer.toString((int) (ShopAutoScanner.getPerShopCooldownMs() / 60000L)));
		cooldownField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{1,3}"));
		cooldownField.setChangedListener(s -> {
			try {
				int minutes = Integer.parseInt(s);
				if (minutes > 0) ShopAutoScanner.setPerShopCooldownMinutes(minutes);
			} catch (NumberFormatException ignored) {
				// not a full number yet — wait for more input
			}
		});
		addDrawableChild(cooldownField);
		y += gap + 12;
		int leftBottom = y;

		// ---- right column: Search & Alerts ----
		y = topY;
		headers.add(new HeaderLabel("Search & Alerts", rightX, y));
		y += 16;

		addDrawableChild(CyclingButtonWidget.builder((Boolean v) -> Text.literal(v ? "GUI" : "Chat"), SearchPreferences.isGuiSearch())
				.values(Boolean.TRUE, Boolean.FALSE)
				.build(rightX, y, colW, 20, Text.literal("/search opens"),
						(btn, value) -> SearchPreferences.setGuiSearch(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(ShopVisitAlert.isEnabled())
				.build(rightX, y, colW, 20, Text.literal("New-item alerts"),
						(btn, value) -> ShopVisitAlert.setEnabled(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.onOffBuilder(ShopVisitAlert.isRaresOnly())
				.build(rightX, y, colW, 20, Text.literal("New-item alerts: rares only"),
						(btn, value) -> ShopVisitAlert.setRaresOnly(value)));
		y += gap;

		addDrawableChild(CyclingButtonWidget.builder((TeleportHighlight.BeamStyle v) -> Text.literal(v.label), TeleportHighlight.getStyle())
				.values(TeleportHighlight.BeamStyle.values())
				.build(rightX, y, colW, 20, Text.literal("Teleport beam style"),
						(btn, value) -> TeleportHighlight.setStyle(value)));
		y += gap;
		int rightBottom = y;

		// ---- bottom: actions, shared full width across both columns ----
		int bottomY = Math.max(leftBottom, rightBottom) + 12;
		int actionW = colW * 2 + colGap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Export now (CSV + Excel)"), btn -> exportBoth())
				.dimensions(leftX, bottomY, actionW, 20).build());
		bottomY += gap;

		addDrawableChild(ButtonWidget.builder(Text.literal("Upload now to Trading Post"), btn -> ShopUploader.uploadAsync(client, true))
				.dimensions(leftX, bottomY, actionW, 20).build());
		bottomY += gap + 12;

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(leftX, bottomY, actionW, 20).build());
	}

	private void exportBoth() {
		try {
			Path runDir = client.runDirectory.toPath();
			Path csvOut = runDir.resolve("shoplogger").resolve("shops.csv");
			Path xlsxOut = runDir.resolve("shoplogger").resolve("shops.xlsx");

			CsvExporter.export(ShopLog.getAll(), csvOut);
			ExcelExporter.export(ShopLog.getAll(), xlsxOut);

			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			ChatFormat.send(client, ChatFormat.SUCCESS,
					"Exported " + ShopLog.size() + " entries at " + time + " -> run/shoplogger/");
		} catch (Exception e) {
			ChatFormat.send(client, ChatFormat.ERROR, "Export failed: " + e.getMessage());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFFFF);
		for (HeaderLabel h : headers) {
			context.drawTextWithShadow(textRenderer, h.text(), h.x(), h.y(), 0xFFB7E23D);
		}
		// TextFieldWidget has no built-in visible label (its Text constructor arg is
		// narration-only), unlike the toggle buttons above which show "Label: value"
		// on their own — so this one needs an explicit label drawn above it.
		context.drawTextWithShadow(textRenderer, "Recently-scanned cooldown, in minutes:", cooldownField.getX(), cooldownField.getY() - 10, 0xFF8FA593);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
