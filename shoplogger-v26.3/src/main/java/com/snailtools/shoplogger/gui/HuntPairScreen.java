package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.hunt.HuntClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * The trailhead: the Shellkeeper's first note, behind the spiral on the home
 * screen. Shows a code to bring to "the place that keeps the ledger" (sctp.nl/hunt)
 * and waits until it's redeemed there, which pairs this game with the hunt.
 */
public class HuntPairScreen extends UiScreen {

	private String error;
	private boolean loading;

	public HuntPairScreen(Screen parent) {
		super("The Shellkeeper", parent, Section.HOME);
		HuntClient.Pairing p = HuntClient.pairing();
		if (!HuntClient.paired() && (p == null || !"waiting".equals(p.status))) newCode();
	}

	@Override
	protected String crumb() {
		return "A note";
	}

	private void newCode() {
		loading = true;
		error = null;
		HuntClient.startPairing().whenComplete((p, ex) -> minecraft().execute(() -> {
			loading = false;
			if (ex != null) error = HuntClient.messageOf(ex);
			rebuild();
		}));
	}

	private static net.minecraft.client.Minecraft minecraft() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	@Override
	protected void initContent() {
		int cx = width / 2, y = contentY() + 120;
		HuntClient.Pairing p = HuntClient.pairing();
		if (HuntClient.paired()) {
			addRenderableWidget(new UiButton(cx - 60, y, 120, 20, "Open the hunt", UiButton.Style.PRIMARY,
					() -> minecraft().setScreenAndShow(new HuntScreen(parent))));
		} else if (error != null || (p != null && "expired".equals(p.status))) {
			addRenderableWidget(new UiButton(cx - 50, y, 100, 20, "New code", UiButton.Style.PRIMARY, this::newCode));
		}
	}

	@Override
	public void tick() {
		super.tick();
		HuntClient.Pairing p = HuntClient.pairing();
		// redraw the buttons once the code is redeemed or runs out
		if (p != null && !"waiting".equals(p.status) && !shownFinal) { shownFinal = true; rebuild(); }
	}
	private boolean shownFinal;

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int cx = width / 2, y = contentY() + 14;
		int w = Math.min(contentW(), 300), x = cx - w / 2;
		Draw.card(g, x, y, w, 132, Theme.PANEL, HuntClient.GOLD);
		Draw.glyph(g, Draw.GLYPH_SPIRAL, cx - 4, y + 10, HuntClient.GOLD);
		HuntClient.Pairing p = HuntClient.pairing();

		if (HuntClient.paired()) {
			Draw.centered(g, font, "Your name is in the ledger.", cx, y + 30, Theme.TEXT);
			String l = HuntClient.ledger();
			if (l != null) Draw.scaled(g, font, l, cx - (int) (font.width(l) * 1.5f) / 2, y + 48, HuntClient.GOLD, 1.5f, true);
			Draw.centered(g, font, "Every shell ends at its tip. Good luck.", cx, y + 72, Theme.MUTED);
			// fair play, right after signing up
			var rules = com.snailtools.shoplogger.hunt.HuntNotes.wrap(font, "Please don't discuss the answers to the hunts in public chats, though playing in a small group is encouraged; we can reset your progress if you share answers!", w - 20);
			for (int j = 0; j < rules.size(); j++) Draw.centered(g, font, rules.get(j), cx, y + 90 + j * 10, Theme.MUTED);
			return;
		}
		if (loading) {
			Draw.centered(g, font, "The Shellkeeper looks up…", cx, y + 40, Theme.MUTED);
			Draw.spinner(g, cx, y + 56, HuntClient.GOLD);
			return;
		}
		if (error != null) {
			var lines = com.snailtools.shoplogger.hunt.HuntNotes.wrap(font, error, w - 20);
			for (int j = 0; j < lines.size(); j++) Draw.centered(g, font, lines.get(j), cx, y + 36 + j * 10, Theme.WARN);
			return;
		}
		if (p == null) return;
		if ("expired".equals(p.status)) {
			Draw.centered(g, font, "The ink has dried. Ask for a new code.", cx, y + 40, Theme.MUTED);
			return;
		}
		Draw.centered(g, font, "Bring this to the place that keeps the ledger.", cx, y + 30, Theme.TEXT);
		String code = p.code == null ? "" : p.code;
		Draw.scaled(g, font, code, cx - (int) (font.width(code) * 2f) / 2, y + 50, HuntClient.GOLD, 2f, true);
		Draw.centered(g, font, "It fades " + HuntClient.until(p.expiresAt) + ".", cx, y + 80, Theme.FAINT);
		Draw.centered(g, font, "Waiting for the ledger…", cx, y + 104, Theme.MUTED);
		Draw.spinner(g, cx, y + 116, HuntClient.GOLD);
	}
}
