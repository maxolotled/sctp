package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** A big dashboard tile: icon, name, short description and an optional live badge. */
public class Tile extends AbstractButton {

	private final ItemStack icon;
	private final String title;
	private final String description;
	private final Supplier<String> badge;
	private final Supplier<String> shortBadge; // used when the full badge would squeeze the title
	private final Supplier<Integer> badgeColor;
	private final Runnable onPress;

	public Tile(int x, int y, int w, int h, ItemStack icon, String title, String description,
			Supplier<String> badge, Supplier<String> shortBadge, Supplier<Integer> badgeColor, Runnable onPress) {
		super(x, y, w, h, Component.literal(title));
		this.icon = icon;
		this.title = title;
		this.description = description;
		this.badge = badge;
		this.shortBadge = shortBadge;
		this.badgeColor = badgeColor;
		this.onPress = onPress;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hover = isHoveredOrFocused();
		if (hover) Draw.shadow(g, x, y, w, h);
		Draw.card(g, x, y, w, h, hover ? Theme.PANEL_HI : Theme.PANEL, hover ? Theme.ACCENT_DIM : Theme.LINE_SOFT);

		boolean tall = h >= 42;
		int iconSize = tall ? 24 : 16;
		int ix = x + 8;
		int iy = y + (h - iconSize - 4) / 2;
		Draw.round(g, ix, iy, iconSize + 4, iconSize + 4, hover ? Theme.ACCENT_TINT : Theme.SLOT);
		// 24px isn't a clean multiple of 16; scale 1.5 still looks crisp enough for items
		if (tall) {
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(ix + 2, iy + 2);
			pose.scale(1.5f, 1.5f);
			g.item(icon, 0, 0);
			pose.popMatrix();
		} else {
			Draw.item(g, icon, ix + 2, iy + 2, 16);
		}

		int tx = ix + iconSize + 12;
		int textW = x + w - 8 - tx;
		int titleW = font.width(title);
		// The title always gets its full width: the badge shrinks to its short
		// form first, then (tall tiles) drops down to the description line.
		String full = badge == null ? null : badge.get();
		String b = full;
		if (b != null && titleW + Draw.pillWidth(font, b) + 6 > textW && shortBadge != null) b = shortBadge.get();
		boolean onTitleLine = b != null && titleW + Draw.pillWidth(font, b) + 6 <= textW;
		boolean onDescLine = b != null && !onTitleLine && tall;
		int bw = b == null ? 0 : Draw.pillWidth(font, b) + 6;
		int color = badgeColor == null ? Theme.ACCENT : badgeColor.get();
		if (tall) {
			g.text(font, Draw.trim(font, title, textW - (onTitleLine ? bw : 0)), tx, y + h / 2 - 10, Theme.TEXT, true);
			g.text(font, Draw.trim(font, description, textW - (onDescLine ? bw : 0)), tx, y + h / 2 + 3, Theme.MUTED, false);
		} else {
			g.text(font, Draw.trim(font, title, textW - (onTitleLine ? bw : 0)), tx, y + (h - 8) / 2, Theme.TEXT, true);
		}
		if (onTitleLine || onDescLine) {
			int by = !tall ? y + (h - 11) / 2 : onTitleLine ? y + h / 2 - 12 : y + h / 2 + 1;
			Draw.pill(g, font, b, x + w - 8 - Draw.pillWidth(font, b), by, Theme.alpha(color, 0x33), color);
		}
		boolean shownInFull = full != null && full.equals(b) && (onTitleLine || onDescLine);
		if (hover && full != null && !shownInFull) {
			g.setTooltipForNextFrame(font, Component.literal(full), mouseX, mouseY);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
