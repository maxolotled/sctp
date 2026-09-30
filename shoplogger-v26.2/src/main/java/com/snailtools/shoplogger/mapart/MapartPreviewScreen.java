package com.snailtools.shoplogger.mapart;

import com.mojang.blaze3d.platform.NativeImage;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.SettingRow;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game view of the mapart scanner: the on/off switch, what it has found
 * nearby, and whether each piece is uploading / uploaded / already known.
 * The scanner never prints to chat — this screen is the only place it talks.
 */
public class MapartPreviewScreen extends UiScreen {

	private static final int TILE_W = 108;
	private static final int TILE_H = 106;
	private static final int TEXTURES_PER_FRAME = 2;

	private int page = 0;
	private UiButton prevButton;
	private UiButton nextButton;
	private int gridTop;
	private int gridBottom;

	/** piece key -> registered texture, and the thumbnail bytes it was made from (to spot a re-render). */
	private final Map<String, Identifier> textures = new HashMap<>();
	private final Map<String, byte[]> textureSource = new HashMap<>();
	private int nextTextureId = 0;

	public static final String CAPTURE_NOTICE = "Enabling this feature will capture all maps in item frames visible to you.";

	public MapartPreviewScreen(Screen parent) {
		super("Mapart scanner", parent, Section.MAPART);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();
		int btnW = 130;
		SettingRow toggle = SettingRow.toggle("Mapart scanning", "Find item-frame mapart near you and upload it",
				() -> MapartScanner.getInstance().isEnabled(), v -> MapartScanner.getInstance().setEnabled(v));
		toggle.setX(x);
		toggle.setY(y);
		toggle.setWidth(w - btnW - 6);
		addRenderableWidget(toggle);
		addRenderableWidget(new UiButton(x + w - btnW, y + 4, btnW, 22, "Re-send nearby", UiButton.Style.SECONDARY,
				() -> MapartScanner.getInstance().resendAll()).tooltip("Upload every piece in range again"));

		gridTop = y + SettingRow.HEIGHT + 36;
		gridBottom = contentBottom() - 24;

		int by = contentBottom() - 18;
		prevButton = addRenderableWidget(new UiButton(width / 2 - 90, by, 60, 18, "‹ Prev", UiButton.Style.SECONDARY, () -> { if (page > 0) page--; }));
		nextButton = addRenderableWidget(new UiButton(width / 2 + 30, by, 60, 18, "Next ›", UiButton.Style.SECONDARY, () -> page++));
	}

	private int columns() { return Math.max(1, (contentW() + 6) / TILE_W); }

	private int rows() { return Math.max(1, (gridBottom - gridTop + 6) / TILE_H); }

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapartScanner scanner = MapartScanner.getInstance();
		List<MapartScanner.Piece> pieces = scanner.snapshot();
		int perPage = columns() * rows();
		int pages = Math.max(1, (pieces.size() + perPage - 1) / perPage);
		if (page >= pages) page = pages - 1;
		prevButton.active = page > 0;
		nextButton.active = page < pages - 1;

		// stats
		int sx = contentX();
		// Required notice (CurseForge): always visible, whether scanning is on or off.
		int ny = contentY() + SettingRow.HEIGHT + 3;
		for (var line : font.split(net.minecraft.network.chat.Component.literal(CAPTURE_NOTICE), contentW())) {
			g.text(font, line, contentX(), ny, Theme.WARN, false);
			ny += 10;
		}
		int sy = Math.max(contentY() + SettingRow.HEIGHT + 17, ny + 2);
		if (scanner.isEnabled()) {
			sx += Draw.dotPill(g, font, scanner.lastFrameCount() + " framed maps in range", sx, sy, Theme.TEAL, Theme.PANEL_ALT, Theme.TEXT) + 4;
			sx += Draw.dotPill(g, font, scanner.queuedCount() + " uploading", sx, sy, Theme.WARN, Theme.PANEL_ALT, Theme.TEXT) + 4;
			Draw.dotPill(g, font, pieces.size() + " found", sx, sy, Theme.ACCENT, Theme.PANEL_ALT, Theme.TEXT);
		} else {
			Draw.dotPill(g, font, "Scanning is off — nothing is being uploaded", sx, sy, Theme.WARN, Theme.PANEL_ALT, Theme.TEXT);
		}

		if (pieces.isEmpty()) {
			Draw.emptyState(g, font, contentX(), gridTop, contentW(), gridBottom - gridTop,
					scanner.isEnabled() ? "No mapart found yet" : "Turn scanning on to find mapart around you",
					scanner.isEnabled() ? "Walk near item-frame mapart and it shows up here." : null, Theme.MUTED);
			return;
		}

		int cols = columns();
		int gridW = cols * TILE_W - 6;
		int startX = contentX() + (contentW() - gridW) / 2;
		int from = page * perPage;
		int created = 0;
		for (int i = from; i < Math.min(pieces.size(), from + perPage); i++) {
			MapartScanner.Piece p = pieces.get(i);
			int idx = i - from;
			int x = startX + (idx % cols) * TILE_W;
			int y = gridTop + (idx / cols) * TILE_H;
			int w = TILE_W - 6, h = TILE_H - 6;

			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			Draw.card(g, x, y, w, h, hover ? Theme.PANEL_HI : Theme.PANEL, hover ? Theme.LINE : Theme.LINE_SOFT);
			int thumbX = x + (w - MapartScanner.THUMB) / 2;
			Draw.round(g, thumbX - 2, y + 4, MapartScanner.THUMB + 4, MapartScanner.THUMB + 4, Theme.SLOT);

			byte[] thumb = p.thumbPng;
			if (thumb != null && textureSource.get(p.key) != thumb && created < TEXTURES_PER_FRAME) {
				created += makeTexture(p.key, thumb) ? 1 : 0;
			}
			Identifier tex = textures.get(p.key);
			if (tex != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, tex, thumbX, y + 6, 0, 0,
						MapartScanner.THUMB, MapartScanner.THUMB, MapartScanner.THUMB, MapartScanner.THUMB);
			} else {
				Draw.spinner(g, x + w / 2, y + 6 + MapartScanner.THUMB / 2, Theme.MUTED);
			}
			int ty = y + 10 + MapartScanner.THUMB;
			Draw.centered(g, font, Draw.trim(font, p.title, w - 8), x + w / 2, ty, Theme.TEXT);
			String size = p.width + "×" + p.height;
			String status = statusText(p.status);
			int pw = font.width(size) + 4 + Draw.pillWidth(font, status);
			int px = x + (w - pw) / 2;
			g.text(font, size, px, ty + 12, Theme.MUTED, false);
			int c = statusColor(p.status);
			Draw.pill(g, font, status, px + font.width(size) + 4, ty + 10, Theme.alpha(c, 0x33), c);
		}
		Draw.centered(g, font, "Page " + (page + 1) + " of " + pages, width / 2, contentBottom() - 13, Theme.MUTED);
	}

	private boolean makeTexture(String key, byte[] png) {
		try {
			NativeImage image = NativeImage.read(png);
			Identifier old = textures.remove(key);
			if (old != null) minecraft.getTextureManager().release(old);
			Identifier id = Identifier.fromNamespaceAndPath("shoplogger", "mapart/" + (nextTextureId++));
			minecraft.getTextureManager().register(id, new DynamicTexture(() -> key, image));
			textures.put(key, id);
			textureSource.put(key, png);
			return true;
		} catch (Exception e) {
			textureSource.put(key, png); // don't retry a broken thumbnail every frame
			return false;
		}
	}

	private static String statusText(MapartScanner.Status s) {
		return switch (s) {
			case QUEUED -> "uploading";
			case UPLOADED -> "uploaded";
			case UNCHANGED -> "known";
			case FAILED -> "failed";
		};
	}

	private static int statusColor(MapartScanner.Status s) {
		return switch (s) {
			case QUEUED -> Theme.WARN;
			case UPLOADED -> Theme.ACCENT;
			case UNCHANGED -> Theme.MUTED;
			case FAILED -> Theme.BAD;
		};
	}

	@Override
	public void onClose() {
		Minecraft client = Minecraft.getInstance();
		for (Identifier id : textures.values()) client.getTextureManager().release(id);
		textures.clear();
		textureSource.clear();
		super.onClose();
	}
}
