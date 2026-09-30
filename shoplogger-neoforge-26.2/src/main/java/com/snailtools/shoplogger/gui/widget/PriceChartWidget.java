package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.data.HistoryPoint;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

/**
 * Hand-drawn line chart (no charting library exists for Minecraft GUIs),
 * one line per world (Firefly / Honeybee) like the website's item pages:
 * a card with the title and a legend, faint gridlines with values, and each
 * line drawn over a soft shaded area.
 */
public final class PriceChartWidget {

	private PriceChartWidget() {}

	public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title,
			List<HistoryPoint> history, ToDoubleFunction<HistoryPoint> field, double scale) {
		Draw.card(g, x, y, w, h, Theme.PANEL, Theme.LINE_SOFT);
		int legendW = Draw.pillWidth(font, "Firefly") + Draw.pillWidth(font, "Honeybee") + 20;
		g.text(font, Draw.trim(font, title, w - 16 - (w > 200 ? legendW : 0)), x + 8, y + 7, Theme.SHELL, false);
		if (w > 200) {
			int lx = x + w - 8 - legendW + 10;
			lx += Draw.dotPill(g, font, "Firefly", lx, y + 5, Theme.FIREFLY, Theme.PANEL_ALT, Theme.MUTED) + 3;
			Draw.dotPill(g, font, "Honeybee", lx, y + 5, Theme.HONEYBEE, Theme.PANEL_ALT, Theme.MUTED);
		}

		int boxY = y + 20;
		int boxH = h - 26;
		if (history == null || history.isEmpty()) {
			Draw.emptyState(g, font, x, boxY, w, boxH, "No price history yet", "It builds up as shops get scanned.", Theme.MUTED);
			return;
		}

		TreeMap<String, Double> firefly = new TreeMap<>();
		TreeMap<String, Double> honeybee = new TreeMap<>();
		for (HistoryPoint hp : history) {
			double v = field.applyAsDouble(hp) * scale;
			if ("Firefly".equalsIgnoreCase(hp.world)) firefly.put(hp.date, v);
			else if ("Honeybee".equalsIgnoreCase(hp.world)) honeybee.put(hp.date, v);
		}

		TreeSet<String> dateSet = new TreeSet<>();
		dateSet.addAll(firefly.keySet());
		dateSet.addAll(honeybee.keySet());
		List<String> dates = new ArrayList<>(dateSet);
		if (dates.isEmpty()) {
			Draw.emptyState(g, font, x, boxY, w, boxH, "No price history yet", "It builds up as shops get scanned.", Theme.MUTED);
			return;
		}

		double maxVal = 0;
		for (double v : firefly.values()) maxVal = Math.max(maxVal, v);
		for (double v : honeybee.values()) maxVal = Math.max(maxVal, v);
		if (maxVal <= 0) maxVal = 1;

		int leftPad = Math.max(font.width(fmtAxis(maxVal)), font.width(fmtAxis(maxVal / 2))) + 12;
		int plotX = x + leftPad, plotY = boxY + 4;
		int plotW = w - leftPad - 10, plotH = boxH - 12;
		if (plotW < 20 || plotH < 10) return;

		axisRow(g, font, plotX, plotY, plotW, maxVal);
		axisRow(g, font, plotX, plotY + plotH / 2, plotW, maxVal / 2);
		axisRow(g, font, plotX, plotY + plotH, plotW, 0);

		series(g, dates, firefly, plotX, plotY, plotW, plotH, maxVal, Theme.FIREFLY);
		series(g, dates, honeybee, plotX, plotY, plotW, plotH, maxVal, Theme.HONEYBEE);

		// first / last date under the plot
		if (dates.size() > 1 && boxH > 40) {
			g.text(font, shortDate(dates.get(0)), plotX, plotY + plotH + 3, Theme.FAINT, false);
			Draw.right(g, font, shortDate(dates.get(dates.size() - 1)), plotX + plotW, plotY + plotH + 3, Theme.FAINT);
		}
	}

	private static String shortDate(String iso) {
		return iso != null && iso.length() >= 10 ? iso.substring(5, 10) : String.valueOf(iso);
	}

	/** Faint gridline plus its value, right-aligned just left of the plot. */
	private static void axisRow(GuiGraphicsExtractor g, Font font, int plotX, int rowY, int plotW, double value) {
		for (int dx = 0; dx < plotW; dx += 4) g.fill(plotX + dx, rowY, plotX + Math.min(dx + 2, plotW), rowY + 1, 0x33FFFFFF);
		String label = fmtAxis(value);
		g.text(font, label, plotX - 5 - font.width(label), rowY - 4, Theme.FAINT, false);
	}

	private static String fmtAxis(double v) {
		double r = Math.round(v * 100.0) / 100.0;
		return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
	}

	private static void series(GuiGraphicsExtractor g, List<String> dates, Map<String, Double> values,
			int px, int py, int pw, int ph, double maxVal, int color) {
		int n = dates.size();
		int bottom = py + ph;
		Integer prevX = null, prevY = null;
		for (int i = 0; i < n; i++) {
			Double v = values.get(dates.get(i));
			if (v == null) { prevX = null; prevY = null; continue; }
			int cx = px + (n <= 1 ? pw / 2 : (int) ((double) i / (n - 1) * pw));
			int cy = py + ph - (int) (v / maxVal * ph);
			if (prevX != null) {
				// shaded area under the segment
				for (int sx = prevX; sx <= cx; sx++) {
					int sy = cx == prevX ? cy : prevY + (cy - prevY) * (sx - prevX) / (cx - prevX);
					g.fill(sx, sy, sx + 1, bottom, Theme.alpha(color, 0x22));
				}
				line(g, prevX, prevY, cx, cy, color);
			}
			g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
			prevX = cx;
			prevY = cy;
		}
	}

	private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		int dx = x2 - x1, dy = y2 - y1;
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps == 0) { g.fill(x1, y1, x1 + 1, y1 + 1, color); return; }
		for (int i = 0; i <= steps; i++) {
			int x = x1 + dx * i / steps;
			int y = y1 + dy * i / steps;
			g.fill(x, y, x + 1, y + 1, color);
		}
	}
}
