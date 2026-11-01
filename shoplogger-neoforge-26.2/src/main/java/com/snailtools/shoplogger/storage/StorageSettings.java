package com.snailtools.shoplogger.storage;

import com.snailtools.shoplogger.config.Config;

/** Settings › My storage. */
public final class StorageSettings {

	private StorageSettings() {}

	public enum HighlightStyle {
		GLOW("White glow (through walls)"),
		BEAM("Beacon beam"),
		BOTH("Glow and beacon beam");

		public final String label;
		HighlightStyle(String label) { this.label = label; }
	}

	public static boolean isEnabled() { return Config.getOrCreate("storage/enabled", Boolean.class, true); }
	public static void setEnabled(boolean v) { Config.update("storage/enabled", v); if (!v) StorageHighlight.clear(); }

	public static boolean hologramsOn() { return Config.getOrCreate("storage/holograms", Boolean.class, true); }
	public static void setHolograms(boolean v) { Config.update("storage/holograms", v); }


	public static HighlightStyle highlightStyle() { return Config.getOrCreate("storage/highlightStyle", HighlightStyle.class, HighlightStyle.GLOW); }
	public static void setHighlightStyle(HighlightStyle s) { Config.update("storage/highlightStyle", s); }
}
