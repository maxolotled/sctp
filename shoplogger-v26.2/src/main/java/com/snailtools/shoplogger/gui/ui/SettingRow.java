package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One setting as a full-width row: name, a one-line description, and the
 * control on the right (switch, choice, number stepper or action button).
 * Clicking anywhere on the row operates the control.
 */
public class SettingRow extends AbstractButton {

	public static final int HEIGHT = 30;

	private enum Kind { TOGGLE, CHOICE, STEPPER, ACTION }

	private final Kind kind;
	private final String label;
	private final String description;

	private BooleanSupplier toggleGet;
	private Consumer<Boolean> toggleSet;

	private List<Object> choices;
	private Supplier<Object> choiceGet;
	private Consumer<Object> choiceSet;
	private Function<Object, String> choiceFormat;

	private int[] steps;
	private IntSupplier stepGet;
	private IntConsumer stepSet;
	private String unit;

	private String actionLabel;
	private Runnable action;
	private long actionDoneAt;

	private double lastClickX = -1;

	private SettingRow(Kind kind, String label, String description) {
		super(0, 0, 100, HEIGHT, Component.literal(label));
		this.kind = kind;
		this.label = label;
		this.description = description;
	}

	public static SettingRow toggle(String label, String description, BooleanSupplier get, Consumer<Boolean> set) {
		SettingRow r = new SettingRow(Kind.TOGGLE, label, description);
		r.toggleGet = get;
		r.toggleSet = set;
		return r;
	}

	@SuppressWarnings("unchecked")
	public static <T> SettingRow choice(String label, String description, List<T> values, Supplier<T> get, Consumer<T> set, Function<T, String> format) {
		SettingRow r = new SettingRow(Kind.CHOICE, label, description);
		r.choices = (List<Object>) List.copyOf(values);
		r.choiceGet = (Supplier<Object>) get;
		r.choiceSet = v -> set.accept((T) v);
		r.choiceFormat = v -> format.apply((T) v);
		return r;
	}

	public static SettingRow stepper(String label, String description, int[] steps, IntSupplier get, IntConsumer set, String unit) {
		SettingRow r = new SettingRow(Kind.STEPPER, label, description);
		r.steps = steps;
		r.stepGet = get;
		r.stepSet = set;
		r.unit = unit;
		return r;
	}

	public static SettingRow action(String label, String description, String buttonLabel, Runnable action) {
		SettingRow r = new SettingRow(Kind.ACTION, label, description);
		r.actionLabel = buttonLabel;
		r.action = action;
		return r;
	}

	// ---- input -----------------------------------------------------------

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		lastClickX = event.x();
		super.onClick(event, doubleClick);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		double clickX = lastClickX;
		lastClickX = -1;
		switch (kind) {
			case TOGGLE -> toggleSet.accept(!toggleGet.getAsBoolean());
			case CHOICE -> {
				int i = choices.indexOf(choiceGet.get());
				int n = choices.size();
				boolean back = input.hasShiftDown() || (clickX >= 0 && clickX < controlLeft() + 12);
				int next = ((i < 0 ? 0 : i) + (back ? -1 : 1) + n) % n;
				choiceSet.accept(choices.get(next));
			}
			case STEPPER -> {
				int cur = nearestStep(stepGet.getAsInt());
				boolean minus = input.hasShiftDown() || (clickX >= 0 && clickX < controlLeft() + 14);
				int idx = Math.max(0, Math.min(steps.length - 1, cur + (minus ? -1 : 1)));
				stepSet.accept(steps[idx]);
			}
			case ACTION -> {
				action.run();
				actionDoneAt = System.currentTimeMillis();
			}
		}
	}

	private int nearestStep(int value) {
		int best = 0;
		for (int i = 0; i < steps.length; i++) {
			if (Math.abs(steps[i] - value) < Math.abs(steps[best] - value)) best = i;
		}
		return best;
	}

	// ---- drawing ---------------------------------------------------------

	private int controlWidth(Font font) {
		return switch (kind) {
			case TOGGLE -> 44;
			case CHOICE -> {
				int max = 0;
				for (Object o : choices) max = Math.max(max, font.width(choiceFormat.apply(o)));
				yield max + 28;
			}
			case STEPPER -> font.width("000 " + unit) + 36;
			case ACTION -> font.width(actionLabel) + 16;
		};
	}

	private int controlLeft() {
		Font font = Minecraft.getInstance().font;
		return getX() + getWidth() - 10 - controlWidth(font);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hover = isHoveredOrFocused();
		Draw.card(g, x, y + 1, w, h - 2, hover ? Theme.PANEL_HI : Theme.PANEL, hover ? Theme.LINE : Theme.LINE_SOFT);

		int cw = controlWidth(font);
		int cx = x + w - 10 - cw;
		int textW = cx - x - 18;
		g.text(font, Draw.trim(font, label, textW), x + 10, y + 6, Theme.TEXT, false);
		g.text(font, Draw.trim(font, description, textW), x + 10, y + 17, Theme.MUTED, false);

		int midY = y + h / 2;
		switch (kind) {
			case TOGGLE -> {
				boolean on = toggleGet.getAsBoolean();
				Draw.right(g, font, on ? "On" : "Off", x + w - 36, midY - 4, on ? Theme.ACCENT : Theme.MUTED);
				Draw.toggle(g, x + w - 30, midY - 5, on, hover);
			}
			case CHOICE -> {
				Draw.card(g, cx, midY - 7, cw, 14, Theme.SLOT, hover ? Theme.ACCENT_DIM : Theme.LINE);
				g.text(font, "‹", cx + 5, midY - 4, Theme.MUTED, false);
				g.text(font, "›", cx + cw - 9, midY - 4, Theme.MUTED, false);
				Draw.centered(g, font, choiceFormat.apply(choiceGet.get()), cx + cw / 2, midY - 4, Theme.TEXT);
			}
			case STEPPER -> {
				Draw.card(g, cx, midY - 7, cw, 14, Theme.SLOT, Theme.LINE);
				boolean overMinus = mouseX >= cx && mouseX < cx + 14 && mouseY >= midY - 7 && mouseY < midY + 7;
				boolean overPlus = mouseX >= cx + cw - 14 && mouseX < cx + cw && mouseY >= midY - 7 && mouseY < midY + 7;
				Draw.round(g, cx + 1, midY - 6, 12, 12, overMinus ? Theme.PANEL_HI : Theme.PANEL_ALT);
				Draw.round(g, cx + cw - 13, midY - 6, 12, 12, overPlus ? Theme.PANEL_HI : Theme.PANEL_ALT);
				Draw.centered(g, font, "-", cx + 7, midY - 4, overMinus ? Theme.ACCENT : Theme.TEXT);
				Draw.centered(g, font, "+", cx + cw - 7, midY - 4, overPlus ? Theme.ACCENT : Theme.TEXT);
				Draw.centered(g, font, stepGet.getAsInt() + " " + unit, cx + cw / 2, midY - 4, Theme.TEXT);
			}
			case ACTION -> {
				boolean done = System.currentTimeMillis() - actionDoneAt < 1800;
				Draw.round(g, cx, midY - 7, cw, 14, done ? Theme.GO : hover ? Theme.ACCENT_HI : Theme.ACCENT);
				Draw.centered(g, font, done ? "Done ✓" : actionLabel, cx + cw / 2, midY - 4, done ? Theme.TEXT : Theme.ACCENT_INK);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
