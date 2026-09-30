package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A filter chip: "World  Honeybee ▾". Click to pick from a popup list of
 * every value (UiPickerScreen); Shift+click cycles to the next one in place.
 */
public class UiChip<T> extends AbstractButton {

	private final String label;
	private final List<T> values;
	private final Function<T, String> format;
	private final Consumer<T> onChange;
	private int index;

	public UiChip(int x, int y, int w, int h, String label, List<T> values, T initial, Function<T, String> format, Consumer<T> onChange) {
		super(x, y, w, h, Component.literal(label));
		this.label = label;
		this.values = List.copyOf(values);
		this.format = format;
		this.onChange = onChange;
		this.index = Math.max(0, this.values.indexOf(initial));
		setTooltip(Tooltip.create(Component.literal("Click to pick · Shift+click for the next one")));
	}

	public T getValue() {
		return values.get(index);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		int n = values.size();
		if (n == 0) return;
		var screen = Minecraft.getInstance().gui.screen();
		if (!input.hasShiftDown() && screen instanceof UiScreen owner) {
			Minecraft.getInstance().setScreenAndShow(new UiPickerScreen<>(owner, label, values, getValue(), format, this::set));
			return;
		}
		set(values.get((index + 1) % n));
	}

	/** Selects a value (from the picker or cycling) and reports it. */
	private void set(T value) {
		int i = values.indexOf(value);
		if (i < 0) return;
		index = i;
		if (onChange != null) onChange.accept(value);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		var font = Minecraft.getInstance().font;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hover = isHoveredOrFocused();
		Draw.card(g, x, y, w, h, hover ? Theme.PANEL_HI : Theme.PANEL_ALT, hover ? Theme.ACCENT_DIM : Theme.LINE);
		int ty = y + (h - 8) / 2;
		int lx = x + 6;
		String lab = label.isEmpty() ? "" : label + " ";
		if (!lab.isEmpty()) {
			g.text(font, lab, lx, ty, Theme.MUTED, false);
			lx += font.width(lab);
		}
		String value = Draw.trim(font, format.apply(getValue()), x + w - 12 - lx);
		g.text(font, value, lx, ty, Theme.TEXT, false);
		// small caret
		int cx = x + w - 9, cy = y + h / 2 - 1;
		g.fill(cx, cy, cx + 5, cy + 1, hover ? Theme.ACCENT : Theme.MUTED);
		g.fill(cx + 1, cy + 1, cx + 4, cy + 2, hover ? Theme.ACCENT : Theme.MUTED);
		g.fill(cx + 2, cy + 2, cx + 3, cy + 3, hover ? Theme.ACCENT : Theme.MUTED);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
