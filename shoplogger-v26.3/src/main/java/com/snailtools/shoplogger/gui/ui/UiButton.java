package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** Flat, rounded button in the site's style. Optional item icon, tooltip and "selected" state. */
public class UiButton extends AbstractButton {

	public enum Style { PRIMARY, SECONDARY, GHOST, DANGER, NAV, TAB }

	private final Style style;
	private final Runnable onPress;
	private ItemStack icon = ItemStack.EMPTY;
	private BooleanSupplier selected = () -> false;

	public UiButton(int x, int y, int w, int h, String label, Style style, Runnable onPress) {
		super(x, y, w, h, Component.literal(label));
		this.style = style;
		this.onPress = onPress;
	}

	public UiButton icon(ItemStack stack) {
		this.icon = stack == null ? ItemStack.EMPTY : stack;
		return this;
	}

	public UiButton tooltip(String text) {
		setTooltip(Tooltip.create(Component.literal(text)));
		return this;
	}

	public UiButton selected(BooleanSupplier selected) {
		this.selected = selected;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (onPress != null) onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		var font = Minecraft.getInstance().font;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hover = isHoveredOrFocused() && active;
		boolean sel = selected.getAsBoolean();
		int fg;
		switch (style) {
			case PRIMARY -> {
				Draw.round(g, x, y, w, h, !active ? Theme.LINE : hover ? Theme.ACCENT_HI : Theme.ACCENT);
				fg = active ? Theme.ACCENT_INK : Theme.MUTED;
			}
			case DANGER -> {
				Draw.card(g, x, y, w, h, hover ? Theme.BAD : Theme.BAD_DEEP, Theme.BAD);
				fg = hover ? 0xFFFFFFFF : 0xFFF2B8A8;
			}
			case GHOST -> {
				if (hover) Draw.round(g, x, y, w, h, 0x22FFFFFF);
				fg = hover ? Theme.TEXT : Theme.MUTED;
			}
			case NAV -> {
				if (sel) Draw.round(g, x, y, w, h, Theme.PANEL_HI);
				else if (hover) Draw.round(g, x, y, w, h, 0x22FFFFFF);
				if (sel) g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, Theme.ACCENT);
				fg = sel ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED;
			}
			case TAB -> {
				if (sel) Draw.card(g, x, y, w, h, Theme.PANEL_HI, Theme.ACCENT_DIM);
				else Draw.card(g, x, y, w, h, hover ? Theme.PANEL_ALT : Theme.PANEL, Theme.LINE_SOFT);
				fg = sel ? Theme.ACCENT : hover ? Theme.TEXT : Theme.MUTED;
			}
			default -> {
				Draw.card(g, x, y, w, h, hover ? Theme.PANEL_HI : Theme.PANEL_ALT, hover ? Theme.ACCENT_DIM : Theme.LINE);
				fg = active ? Theme.TEXT : Theme.FAINT;
			}
		}

		String label = getMessage().getString();
		boolean hasIcon = !icon.isEmpty();
		int textW = label.isEmpty() ? 0 : font.width(label);
		int contentW = (hasIcon ? 16 : 0) + (hasIcon && textW > 0 ? 4 : 0) + textW;
		int cx = x + (w - contentW) / 2;
		if (hasIcon) {
			Draw.item(g, icon, cx, y + (h - 16) / 2, 16);
			cx += 16 + (textW > 0 ? 4 : 0);
		}
		if (textW > 0) {
			String shown = Draw.trim(font, label, w - 8 - (hasIcon ? 20 : 0));
			if (!shown.equals(label)) cx = x + 4 + (hasIcon ? 20 : 0);
			g.text(font, shown, cx, y + (h - 8) / 2, fg, false);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
