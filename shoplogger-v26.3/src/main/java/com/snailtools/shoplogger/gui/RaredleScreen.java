package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RaredleClient;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Today's Rare-dle, in-game. Same daily puzzle, same account and same
 * leaderboard as sctp.nl/rare-dle (see RaredleClient for how it signs in).
 * Type a rare's name, pick it, and each guess shows how close it is per
 * attribute: green right, yellow close, red wrong, grey unknown.
 */
public class RaredleScreen extends UiScreen {

	private static final int KEY_ENTER = 257;
	private static final int KEY_KP_ENTER = 335;
	private static final int MAX_SUGGESTIONS = 6;
	private static final int ROW_H = 20;
	private static final int SUGGEST_H = 14;
	private static final String[] KEYS = { "category", "released", "obtained", "slot", "dyeable", "particles" };
	private static final Map<String, String> LABELS = Map.of(
			"category", "Category", "released", "Released", "obtained", "Obtained from",
			"slot", "Type / slot", "dyeable", "Dyeable", "particles", "Particles");
	private static final int CORRECT = 0xFF2F7D4E, CLOSE = 0xFFA97E22, WRONG = 0xFF7E3329, UNKNOWN = 0xFF3B403D;

	private UiField search;
	private List<RareItem> rares = List.of();
	private final List<RareItem> suggestions = new ArrayList<>();
	private RaredleClient.State state;
	private boolean loading = true, busy = false;
	private String error;
	private String keepQuery = "";
	private int suggestX, suggestY, suggestW;
	private int tableY;

	public RaredleScreen(Screen parent) {
		super("Rare-dle", parent, Section.RAREDLE);
		WebDataClient.fetchRareCatalog().thenAccept(list -> minecraft().execute(() -> { rares = list; updateSuggestions(); }));
		RaredleClient.state().whenComplete((s, ex) -> minecraft().execute(() -> {
			loading = false;
			if (ex != null) error = RaredleClient.messageOf(ex);
			else state = s;
			rebuild();
		}));
	}

	private static net.minecraft.client.Minecraft minecraft() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	private boolean playing() {
		return state != null && "playing".equals(state.status);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY() + 30, w = contentW();
		if (playing()) {
			int btnW = 56;
			search = new UiField(font, x, y, w - btnW - 6, 20, "Type a rare's name…", true);
			search.box.setValue(keepQuery);
			search.box.setResponder(s -> { keepQuery = s; updateSuggestions(); });
			addRenderableOnly(search.frame());
			addRenderableWidget(search.box);
			addRenderableWidget(new UiButton(x + w - btnW, y, btnW, 20, "Guess", UiButton.Style.PRIMARY, () -> pick(0)));
			suggestX = x;
			suggestY = y + 22;
			suggestW = w - btnW - 6;
			setInitialFocus(search.box);
			y += 28;
		} else {
			search = null;
			int bw = 150;
			addRenderableWidget(new UiButton(x + w - bw, contentY(), bw, 18, "Leaderboard on sctp.nl ↗", UiButton.Style.SECONDARY,
					() -> UiLinks.open("https://sctp.nl/rare-dle/")));
		}
		tableY = y;
		updateSuggestions();
	}

	private void updateSuggestions() {
		suggestions.clear();
		if (search == null || state == null) return;
		String q = keepQuery.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return;
		Set<String> guessed = new HashSet<>();
		for (RaredleClient.Guess g : state.guesses) guessed.add(g.itemId);
		List<RareItem> starts = new ArrayList<>(), contains = new ArrayList<>();
		for (RareItem r : rares) {
			if (r.id == null || r.name == null || guessed.contains(r.id)) continue;
			String n = r.name.toLowerCase(Locale.ROOT);
			if (n.startsWith(q)) starts.add(r);
			else if (n.contains(q)) contains.add(r);
		}
		starts.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		contains.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		starts.addAll(contains);
		for (int i = 0; i < Math.min(MAX_SUGGESTIONS, starts.size()); i++) suggestions.add(starts.get(i));
	}

	private void pick(int index) {
		if (busy || index >= suggestions.size()) return;
		RareItem r = suggestions.get(index);
		busy = true;
		error = null;
		RaredleClient.guess(r.id).whenComplete((s, ex) -> minecraft().execute(() -> {
			busy = false;
			if (ex != null) error = RaredleClient.messageOf(ex);
			else { state = s; keepQuery = ""; }
			rebuild();
		}));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.box.isFocused() && (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER)) {
			pick(0);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (search != null && !suggestions.isEmpty() && event.x() >= suggestX && event.x() < suggestX + suggestW) {
			int i = (int) ((event.y() - suggestY) / SUGGEST_H);
			if (event.y() >= suggestY && i >= 0 && i < suggestions.size()) {
				pick(i);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- drawing ---------------------------------------------------------

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY(), w = contentW();
		if (state == null) return;

		String head;
		int headColor = Theme.TEXT;
		int used = state.guesses == null ? 0 : state.guesses.size();
		if (playing()) head = "Guess today's secret rare · " + used + " of " + state.maxGuesses + " guesses";
		else if ("won".equals(state.status)) { head = "Solved in " + used + "! +" + state.score + " points"; headColor = Theme.ACCENT; }
		else { head = "Out of guesses. Back tomorrow for a new one!"; headColor = Theme.WARN; }
		Draw.textShadow(g, font, head, x, y + 2, headColor);
		if (state.stats != null) {
			String stats = "Streak " + state.stats.streak + " · Best " + state.stats.bestStreak + " · " + fmtNum(state.stats.points) + " points";
			Draw.text(g, font, Draw.trim(font, stats, w - (playing() ? 60 : 160)), x, y + 14, Theme.MUTED);
		}

		drawTable(g, x, tableY, w, mouseX, mouseY);
	}

	private void drawTable(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
		int hintSize = state.pixelHint != null && state.pixelHint.cells != null ? 48 : 0;
		int tableW = w - (hintSize > 0 ? hintSize + 10 : 0);
		int nameW = Math.min(130, tableW / 4);
		int cellsX = x + 22 + nameW;
		int cellW = (tableW - 22 - nameW) / KEYS.length;

		// column headers
		for (int i = 0; i < KEYS.length; i++) {
			Draw.text(g, font, Draw.trim(font, LABELS.get(KEYS[i]), cellW - 4), cellsX + i * cellW + 2, y, Theme.FAINT);
		}
		int ry = y + 11;
		if (state.guesses != null) {
			for (RaredleClient.Guess gs : state.guesses) {
				Draw.remoteTexture(g, gs.texture, x, ry + 2, 16);
				Draw.text(g, font, Draw.trim(font, gs.name, nameW - 4), x + 20, ry + 6, Theme.TEXT);
				for (int i = 0; i < KEYS.length; i++) {
					RaredleClient.Cell c = cell(gs, KEYS[i]);
					int cx = cellsX + i * cellW;
					Draw.round(g, cx, ry, cellW - 3, ROW_H - 2, color(c));
					String v = c == null || c.v == null ? "?" : c.v;
					if (c != null && "up".equals(c.d)) v = "↑ " + v;
					else if (c != null && "down".equals(c.d)) v = "↓ " + v;
					Draw.text(g, font, Draw.trim(font, v, cellW - 8), cx + 3, ry + 5, 0xFFFFFFFF);
					if (c != null && mouseX >= cx && mouseX < cx + cellW - 3 && mouseY >= ry && mouseY < ry + ROW_H - 2) {
						g.setTooltipForNextFrame(font, net.minecraft.network.chat.Component.literal(LABELS.get(KEYS[i]) + ": " + v), mouseX, mouseY);
					}
				}
				ry += ROW_H;
			}
		}

		// the pixel hint: the secret rare's icon, blurred, sharpening as you guess
		if (hintSize > 0) {
			int hx = x + w - hintSize, n = Math.max(1, state.pixelHint.size), px = hintSize / n;
			Draw.text(g, font, "Hint", hx, y, Theme.FAINT);
			Draw.round(g, hx - 2, y + 9, px * n + 4, px * n + 4, Theme.SLOT);
			for (int i = 0; i < state.pixelHint.cells.size() && i < n * n; i++) {
				String hex = state.pixelHint.cells.get(i);
				if (hex == null || hex.isEmpty()) continue;
				int rgb = Integer.parseInt(hex.substring(1), 16);
				Draw.rect(g, hx + (i % n) * px, y + 11 + (i / n) * px, px, px, 0xFF000000 | rgb);
			}
		}

		// the answer, once the game is over
		if (state.answer != null) {
			ry += 6;
			Draw.text(g, font, "today's rare", x, ry, Theme.FAINT);
			Draw.remoteTexture(g, state.answer.texture, x, ry + 11, 16);
			Draw.textShadow(g, font, state.answer.name, x + 20, ry + 15, Theme.ACCENT);
			if (state.answer.effect != null && !state.answer.effect.isBlank()) {
				Draw.text(g, font, Draw.trim(font, state.answer.effect, w - 20), x + 20, ry + 27, Theme.MUTED);
			}
		}
	}

	private static RaredleClient.Cell cell(RaredleClient.Guess g, String key) {
		if (g.feedback == null) return null;
		for (RaredleClient.Cell c : g.feedback) if (key.equals(c.k)) return c;
		return null;
	}

	private static int color(RaredleClient.Cell c) {
		if (c == null || c.s == null) return UNKNOWN;
		return switch (c.s) {
			case "correct" -> CORRECT;
			case "close" -> CLOSE;
			case "wrong" -> WRONG;
			default -> UNKNOWN;
		};
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), w = contentW();
		if (loading || (state == null && error == null)) {
			listState(g, x, contentY(), w, contentH(), true, false, false, null, null);
			return;
		}
		if (state == null) {
			Draw.emptyState(g, font, x, contentY(), w, contentH(), "Rare-dle couldn't start", error, Theme.WARN);
			return;
		}
		if (error != null) Draw.right(g, font, Draw.trim(font, error, w / 2), x + w, contentY() + 14, Theme.WARN);
		if (busy) Draw.right(g, font, "Checking…", x + w, contentY() + 2, Theme.MUTED);

		// suggestion dropdown, over the table
		if (search != null && !suggestions.isEmpty()) {
			int h = suggestions.size() * SUGGEST_H + 2;
			Draw.card(g, suggestX, suggestY, suggestW, h, Theme.PANEL_HI, Theme.LINE);
			for (int i = 0; i < suggestions.size(); i++) {
				RareItem r = suggestions.get(i);
				int sy = suggestY + 1 + i * SUGGEST_H;
				boolean over = mouseX >= suggestX && mouseX < suggestX + suggestW && mouseY >= sy && mouseY < sy + SUGGEST_H;
				if (over || i == 0) Draw.rect(g, suggestX + 1, sy, suggestW - 2, SUGGEST_H, over ? Theme.ACCENT_TINT : Theme.PANEL_ALT);
				Draw.remoteTexture(g, r.texture, suggestX + 3, sy + 1, 12);
				Draw.text(g, font, Draw.trim(font, r.name, suggestW - 110), suggestX + 18, sy + 3, Theme.TEXT);
				if (r.category != null) Draw.right(g, font, Draw.trim(font, r.category, 90), suggestX + suggestW - 4, sy + 3, Theme.FAINT);
			}
		}
	}
}
