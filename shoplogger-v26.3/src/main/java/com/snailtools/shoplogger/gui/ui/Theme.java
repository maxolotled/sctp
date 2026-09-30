package com.snailtools.shoplogger.gui.ui;

import com.snailtools.shoplogger.config.Config;

/**
 * The mod's UI palette. "Snail" (the default) uses the same colours as the
 * website (sctp.nl); the other palettes swap the grounds, text and accent
 * while keeping status and world colours fixed, so red still means "bad" and
 * Firefly/Honeybee match the website's charts whatever theme you pick.
 * Colours are read every frame, so switching themes applies instantly. ARGB.
 */
public final class Theme {

	private Theme() {}

	/** A colour theme: grounds (darkest to lightest), lines, text, and the accent. RGB. */
	public enum Palette {
		SNAIL("Snail (default)", 0x0C140F, 0x101B14, 0x142019, 0x1B2A20, 0x22332A, 0x2A3D31, 0x0F1813, 0x33453A, 0x26362C, 0xEAEFE7, 0x8FA593, 0x5F7565, 0xD9C89A, 0xB7E23D),
		OCEAN("Ocean", 0x0A1218, 0x0E1822, 0x12202C, 0x172A38, 0x1D3344, 0x243D51, 0x0B141C, 0x2E4A60, 0x223849, 0xE6EFF5, 0x8BA6B8, 0x5A7486, 0xBFD8E6, 0x4FC3F7),
		AMETHYST("Amethyst", 0x110C18, 0x161020, 0x1B1428, 0x231A33, 0x2B213E, 0x34284A, 0x0F0B16, 0x44365C, 0x322846, 0xEFE9F5, 0xA596B8, 0x6E6084, 0xD9C8EE, 0xB388FF),
		SAKURA("Sakura", 0x170C11, 0x1E1016, 0x26141C, 0x2F1A24, 0x38202C, 0x432735, 0x150B10, 0x553342, 0x402634, 0xF7EAF0, 0xC29AAB, 0x86606F, 0xF2CAD8, 0xFF8FB8),
		HONEY("Honey", 0x150F07, 0x1B140A, 0x22190D, 0x2B2012, 0x342717, 0x3E2F1C, 0x130D06, 0x4E3C23, 0x3A2D1A, 0xF5EEE2, 0xB8A485, 0x7F6D52, 0xEED9A8, 0xFFC23D),
		PUMPKIN("Pumpkin", 0x140C08, 0x1A100A, 0x21140C, 0x2A1A10, 0x332014, 0x3D2719, 0x120A06, 0x50331F, 0x3B2617, 0xF5EBE3, 0xBA9A85, 0x806453, 0xF0CFA8, 0xFF8A2B),
		CRIMSON("Crimson", 0x150A0B, 0x1C0E10, 0x231214, 0x2C171A, 0x351C20, 0x402227, 0x130809, 0x552D32, 0x3F2226, 0xF5E8E9, 0xBB9296, 0x825E62, 0xF0C9C4, 0xFF5A5F),
		FROST("Frost", 0x0D1116, 0x12171E, 0x161C25, 0x1C232E, 0x232B38, 0x2B3544, 0x0B0F14, 0x38455A, 0x29333F, 0xEEF3F8, 0x9AA8BA, 0x667488, 0xD0DCEB, 0x9FD8FF),
		FIREFLY("Firefly", 0x08120F, 0x0C1916, 0x10201C, 0x152A25, 0x1A332D, 0x213D36, 0x07100D, 0x2B4A42, 0x203831, 0xE8F2EE, 0x8FB0A6, 0x5E7F75, 0xF0E2A8, 0xFFE066),
		GRAPHITE("Graphite", 0x0E0E0F, 0x141415, 0x19191B, 0x202022, 0x28282B, 0x313135, 0x0C0C0D, 0x3E3E43, 0x2C2C30, 0xEDEDED, 0x9E9EA4, 0x6A6A70, 0xD6D6D6, 0xF2F2F2);

		public final String label;
		final int bgDeep, bg, bar, panel, panelAlt, panelHi, slot, line, lineSoft, text, muted, faint, shell, accent;

		Palette(String label, int bgDeep, int bg, int bar, int panel, int panelAlt, int panelHi, int slot,
				int line, int lineSoft, int text, int muted, int faint, int shell, int accent) {
			this.label = label;
			this.bgDeep = bgDeep; this.bg = bg; this.bar = bar; this.panel = panel; this.panelAlt = panelAlt;
			this.panelHi = panelHi; this.slot = slot; this.line = line; this.lineSoft = lineSoft;
			this.text = text; this.muted = muted; this.faint = faint; this.shell = shell; this.accent = accent;
		}
	}

	private static final String CONFIG_THEME = "ui/theme";

	// grounds, darkest to lightest
	public static int BG_DEEP, BG, BAR, PANEL, PANEL_ALT, PANEL_HI, SLOT;

	// lines
	public static int LINE, LINE_SOFT;

	// text
	public static int TEXT, MUTED, FAINT, SHELL;

	// accent and friends (HI/DIM/INK/TINT are derived from the accent)
	public static int ACCENT, ACCENT_HI, ACCENT_DIM, ACCENT_INK, ACCENT_TINT;

	// status: the same in every theme
	public static final int WARN = 0xFFE2A33D;
	public static final int BAD = 0xFFE2643D;
	public static final int BAD_DEEP = 0xFF3A1E19;
	public static final int INFO = 0xFF8C93E8;
	public static final int TEAL = 0xFF6FE3C8;
	public static final int GO = 0xFF2E6B45;

	// worlds (match the website's charts, in every theme)
	public static final int FIREFLY = 0xFFE2A33D;
	public static final int HONEYBEE = 0xFFB7E23D;

	private static Palette current = Palette.SNAIL;

	static {
		Palette saved = Palette.SNAIL;
		try {
			saved = Config.getOrCreate(CONFIG_THEME, Palette.class, Palette.SNAIL);
		} catch (Exception ignored) {
			// config not readable yet: stay on the default
		}
		apply(saved == null ? Palette.SNAIL : saved);
	}

	public static Palette get() {
		return current;
	}

	/** Switches every screen to this palette (and remembers it). */
	public static void set(Palette palette) {
		apply(palette);
		Config.update(CONFIG_THEME, palette);
	}

	private static void apply(Palette p) {
		current = p;
		BG_DEEP = opaque(p.bgDeep);
		BG = opaque(p.bg);
		BAR = opaque(p.bar);
		PANEL = opaque(p.panel);
		PANEL_ALT = opaque(p.panelAlt);
		PANEL_HI = opaque(p.panelHi);
		SLOT = opaque(p.slot);
		LINE = opaque(p.line);
		LINE_SOFT = opaque(p.lineSoft);
		TEXT = opaque(p.text);
		MUTED = opaque(p.muted);
		FAINT = opaque(p.faint);
		SHELL = opaque(p.shell);
		ACCENT = opaque(p.accent);
		ACCENT_HI = opaque(mix(p.accent, 0xFFFFFF, 0.22f));
		ACCENT_DIM = opaque(mix(p.accent, 0x000000, 0.26f));
		ACCENT_INK = opaque(mix(p.accent, 0x000000, 0.86f));
		ACCENT_TINT = alpha(p.accent, 0x33);
	}

	public static int worldColor(String world) {
		if (world == null) return MUTED;
		if ("Firefly".equalsIgnoreCase(world)) return FIREFLY;
		if ("Honeybee".equalsIgnoreCase(world)) return HONEYBEE;
		return MUTED;
	}

	/** Same colour with a different alpha (0-255). */
	public static int alpha(int argb, int a) {
		return (a & 0xFF) << 24 | (argb & 0x00FFFFFF);
	}

	private static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** Blends two RGB colours: t = 0 gives a, t = 1 gives b. */
	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}
}
