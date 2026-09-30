package com.snailtools.shoplogger.gui.ui;

/**
 * The mod's UI palette: the same colours as the website (sctp.nl), so the
 * in-game screens feel like part of the same product. ARGB.
 */
public final class Theme {

	private Theme() {}

	// grounds, darkest to lightest
	public static final int BG_DEEP = 0xFF0C140F;
	public static final int BG = 0xFF101B14;
	public static final int BAR = 0xFF142019;
	public static final int PANEL = 0xFF1B2A20;
	public static final int PANEL_ALT = 0xFF22332A;
	public static final int PANEL_HI = 0xFF2A3D31;
	public static final int SLOT = 0xFF0F1813;

	// lines
	public static final int LINE = 0xFF33453A;
	public static final int LINE_SOFT = 0xFF26362C;

	// text
	public static final int TEXT = 0xFFEAEFE7;
	public static final int MUTED = 0xFF8FA593;
	public static final int FAINT = 0xFF5F7565;
	public static final int SHELL = 0xFFD9C89A;

	// accent (lime) and friends
	public static final int ACCENT = 0xFFB7E23D;
	public static final int ACCENT_HI = 0xFFC9EE5E;
	public static final int ACCENT_DIM = 0xFF87AE29;
	public static final int ACCENT_INK = 0xFF16210F;
	public static final int ACCENT_TINT = 0x33B7E23D;

	// status
	public static final int WARN = 0xFFE2A33D;
	public static final int BAD = 0xFFE2643D;
	public static final int BAD_DEEP = 0xFF3A1E19;
	public static final int INFO = 0xFF8C93E8;
	public static final int TEAL = 0xFF6FE3C8;
	public static final int GO = 0xFF2E6B45;

	// worlds (match the website's charts)
	public static final int FIREFLY = 0xFFE2A33D;
	public static final int HONEYBEE = 0xFFB7E23D;

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
}
