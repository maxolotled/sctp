package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The popup a filter chip opens: every option in a scrollable card, the
 * current one highlighted, plus a search box when the list is long (months,
 * categories). Picking an option applies it and goes straight back.
 */
public class UiPickerScreen<T> extends UiScreen {

	private static final int CARD_W = 240;
	private static final int ROW_H = 18;
	private static final int SEARCH_FROM = 9; // options before a search box shows up

	private final UiScreen owner;
	private final String label;
	private final List<T> values;
	private final T current;
	private final Function<T, String> format;
	private final Consumer<T> onPick;

	private UiField search;
	private String keepQuery = "";
	private OptionList list;
	private int cardX, cardY, cardW, cardH, listY, listH;

	public UiPickerScreen(UiScreen owner, String label, List<T> values, T current, Function<T, String> format, Consumer<T> onPick) {
		super(label, owner, owner.section());
		this.owner = owner;
		this.label = label;
		this.values = values;
		this.current = current;
		this.format = format;
		this.onPick = onPick;
	}

	@Override
	protected String crumb() {
		return owner.crumb() + " › " + label;
	}

	@Override
	protected void initContent() {
		cardW = Math.min(CARD_W, contentW());
		boolean searchable = values.size() >= SEARCH_FROM;
		int header = 26 + (searchable ? 26 : 0);
		int wanted = values.size() * ROW_H + 8;
		listH = Math.max(ROW_H * 3, Math.min(wanted, contentH() - header - 12));
		cardH = header + listH + 8;
		cardX = (width - cardW) / 2;
		cardY = contentY() + Math.max(0, (contentH() - cardH) / 2);
		int y = cardY + 24;

		if (searchable) {
			search = new UiField(font, cardX + 8, y, cardW - 16, 20, "Search…", true);
			search.box.setValue(keepQuery);
			search.box.setResponder(s -> { keepQuery = s; fill(); });
			addRenderableOnly(search.frame());
			addRenderableWidget(search.box);
			setInitialFocus(search.box);
			y += 26;
		} else {
			search = null;
		}

		listY = y;
		list = new OptionList(minecraft, cardX + 2, listY, cardW - 4, listH);
		addRenderableWidget(list);
		fill();

		// open with the current choice in view
		int idx = values.indexOf(current);
		if (idx >= 0 && keepQuery.isEmpty()) list.setScrollAmount(Math.max(0, idx * ROW_H - listH / 2 + ROW_H));
	}

	private void fill() {
		if (list == null) return;
		list.clear();
		String q = keepQuery.trim().toLowerCase(Locale.ROOT);
		for (T v : values) {
			String text = format.apply(v);
			if (!q.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(new OptionList.Option(text, v.equals(current), () -> pick(v)));
		}
		list.setScrollAmount(0);
	}

	private void pick(T value) {
		onPick.accept(value);
		minecraft.setScreenAndShow(owner);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		// Enter picks the only / first match while searching
		if ((event.key() == 257 || event.key() == 335) && list != null && list.first() != null) {
			list.first().onClick.run();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Draw.shadow(g, cardX, cardY, cardW, cardH);
		Draw.card(g, cardX, cardY, cardW, cardH, Theme.PANEL, Theme.LINE);
		g.text(font, ("Pick " + label).toUpperCase(Locale.ROOT), cardX + 10, cardY + 9, Theme.FAINT, false);
		Draw.right(g, font, values.size() + " options", cardX + cardW - 10, cardY + 9, Theme.FAINT);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (list != null && list.size() == 0) {
			Draw.centered(g, font, "Nothing matches that", cardX + cardW / 2, listY + listH / 2 - 4, Theme.MUTED);
		}
	}

	/** Plain one-line rows; the current value gets a lime bar and text. */
	private static final class OptionList extends AbstractSelectionList<OptionList.Option> {

		OptionList(Minecraft client, int x, int y, int w, int h) {
			super(client, w, h, y, ROW_H);
			updateSizeAndPosition(w, h, x, y);
		}

		void clear() { clearEntries(); }
		void add(Option o) { addEntry(o); }
		int size() { return getItemCount(); }
		Option first() { return getItemCount() > 0 ? children().get(0) : null; }

		@Override
		public int getRowWidth() { return width - 14; }

		@Override
		protected int scrollBarX() { return getX() + width - 6; }

		@Override
		protected void extractListBackground(GuiGraphicsExtractor g) {}

		@Override
		protected void extractListSeparators(GuiGraphicsExtractor g) {}

		@Override
		protected void extractSelection(GuiGraphicsExtractor g, Option entry, int color) {}

		@Override
		protected void extractScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
			if (scrollable()) UiLists.scrollbar(g, scrollBarX(), getY(), getHeight(), scrollBarY(), scrollerHeight(), mouseX, mouseY);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {}

		static final class Option extends AbstractSelectionList.Entry<Option> {
			private final String text;
			private final boolean selected;
			private final Runnable onClick;

			Option(String text, boolean selected, Runnable onClick) {
				this.text = text;
				this.selected = selected;
				this.onClick = onClick;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				var font = Minecraft.getInstance().font;
				int x = getX(), y = getY(), w = getWidth(), h = getHeight();
				if (hovered || selected) Draw.round(g, x, y + 1, w, h - 2, selected ? Theme.ACCENT_TINT : Theme.PANEL_HI);
				if (selected) Draw.round(g, x + 2, y + 4, 2, h - 8, Theme.ACCENT);
				g.text(font, Draw.trim(font, text, w - 18), x + 9, y + (h - 8) / 2, selected ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.MUTED, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				onClick.run();
				return true;
			}
		}
	}
}
