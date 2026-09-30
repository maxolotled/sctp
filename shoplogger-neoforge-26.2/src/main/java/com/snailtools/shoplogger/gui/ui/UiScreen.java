package com.snailtools.shoplogger.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base for every Shop Logger screen: the site-style background, and a top
 * bar with Back, the logo, a "Trading Post › Section" breadcrumb and icon
 * tabs to jump straight to any other section. Subclasses build their
 * content in initContent() inside the content box (contentX/Y/W/H).
 */
public abstract class UiScreen extends Screen {

	public static final int TOP_H = 28;
	protected static final int PAD = 10;
	private static final int NAV_W = 20;

	protected final Screen parent;
	private final Section section;

	private int titleLeft;
	private int titleRight;

	protected UiScreen(String title, Screen parent, Section section) {
		super(Component.literal(title));
		this.parent = parent;
		this.section = section;
	}

	// ---- layout ----------------------------------------------------------

	protected int contentX() { return PAD; }
	protected int contentY() { return TOP_H + 8; }
	protected int contentW() { return width - PAD * 2; }
	protected int contentBottom() { return height - 8; }
	protected int contentH() { return contentBottom() - contentY(); }

	/** The section tab this screen lights up. */
	public Section section() { return section; }

	/** Page title shown in the breadcrumb; defaults to the screen title. */
	protected String crumb() { return title.getString(); }

	/** Whether to show the section tabs (hidden on very narrow windows). */
	private boolean showNav() { return width >= 300; }

	@Override
	protected final void init() {
		int x = 6;
		if (parent != null) {
			addRenderableWidget(new UiButton(x, 5, 18, 18, "‹", UiButton.Style.GHOST, this::onClose).tooltip("Back (Esc)"));
			x += 22;
		}
		titleLeft = x + 19;

		int navCount = Section.values().length;
		int navStart = width - 6 - navCount * NAV_W;
		if (showNav()) {
			int nx = navStart;
			for (Section s : Section.values()) {
				Section target = s;
				addRenderableWidget(new UiButton(nx, 4, NAV_W - 2, 20, "", UiButton.Style.NAV, () -> go(target))
						.icon(s.icon())
						.tooltip(s.label + " — " + s.description)
						.selected(() -> section == target));
				nx += NAV_W;
			}
			titleRight = navStart - 8;
		} else {
			titleRight = width - 8;
		}
		initContent();
	}

	protected abstract void initContent();

	private void go(Section target) {
		if (target == section) return;
		minecraft.setScreenAndShow(target.open());
	}

	/** Rebuilds all widgets (e.g. after switching a tab inside the page). */
	protected void rebuild() {
		clearWidgets();
		init();
	}

	// ---- drawing ---------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Draw.gradient(g, 0, 0, width, height, 0xE6101B14, 0xF20A120D);
		// soft lime glow top-right, like the website header
		Draw.gradient(g, width / 2, 0, width / 2, height / 3, 0x14B7E23D, 0x00B7E23D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// top bar
		Draw.gradient(g, 0, 0, width, TOP_H, 0xF21A281F, 0xF2142019);
		Draw.hline(g, 0, TOP_H, width, Theme.LINE_SOFT);
		Draw.logo(g, titleLeft - 19, 6);

		var font = this.font;
		int y = (TOP_H - 8) / 2;
		String home = "Trading Post";
		String here = crumb();
		int room = titleRight - titleLeft;
		if (room > 30) {
			int homeW = font.width(home);
			if (section == Section.HOME || homeW + 14 + font.width(here) > room) {
				String t = Draw.trim(font, section == Section.HOME ? "Snailcraft Trading Post" : here, room);
				Draw.textShadow(g, font, t, titleLeft, y, Theme.TEXT);
			} else {
				Draw.text(g, font, home, titleLeft, y, Theme.MUTED);
				Draw.text(g, font, "›", titleLeft + homeW + 4, y, Theme.FAINT);
				Draw.textShadow(g, font, Draw.trim(font, here, room - homeW - 14), titleLeft + homeW + 12, y, Theme.TEXT);
			}
		}

		extractPage(g, mouseX, mouseY, delta);
		super.extractRenderState(g, mouseX, mouseY, delta);
		extractOverlay(g, mouseX, mouseY, delta);
	}

	/** Draw page content that sits *under* the widgets (cards, headers). */
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

	/** Draw things that sit *over* the widgets (empty states, status text). */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// clicking the logo or "Trading Post" goes to the dashboard (no widgets live there)
		int homeRight = titleLeft + font.width("Trading Post");
		if (section != Section.HOME && event.y() >= 4 && event.y() < TOP_H - 4
				&& event.x() >= titleLeft - 19 && event.x() < Math.min(homeRight, titleRight)) {
			minecraft.setScreenAndShow(Section.HOME.open());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- shared bits -----------------------------------------------------

	/** Standard "Loading / failed / empty" message for a list area. */
	protected void listState(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean loading, boolean failed, boolean empty, String emptyTitle, String emptyHint) {
		if (loading) {
			Draw.centered(g, font, "Loading…", x + w / 2, y + h / 2 - 8, Theme.MUTED);
			Draw.spinner(g, x + w / 2, y + h / 2 + 4, Theme.ACCENT);
		} else if (failed) {
			Draw.emptyState(g, font, x, y, w, h, "Couldn't load this from sctp.nl", "Check your connection and reopen this screen.", Theme.WARN);
		} else if (empty) {
			Draw.emptyState(g, font, x, y, w, h, emptyTitle, emptyHint, Theme.MUTED);
		}
	}

	protected static String fmtNum(int n) {
		return String.format(java.util.Locale.US, "%,d", n);
	}
}
