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

	/** What the holograms on your containers say. */
	public enum HologramMode {
		SUPPLY("Main item"),        // what's mostly in it and how many
		NAME("Name + main item"),   // the name you gave it (or its anvil name) with the main item under it; unnamed: the main item
		NAME_ONLY("Name + item count"), // the name with how many items it holds under it; unnamed containers show nothing
		INDEXED("In My storage?"),  // on every container around: whether it's in My storage yet
		OFF("Off");

		public final String label;
		HologramMode(String label) { this.label = label; }
	}

	public static HologramMode hologramMode() {
		HologramMode m = Config.get("storage/hologramMode", HologramMode.class);
		if (m != null) return m;
		// from before there were modes: on or off
		return Config.getOrCreate("storage/holograms", Boolean.class, true) ? HologramMode.SUPPLY : HologramMode.OFF;
	}
	public static void setHologramMode(HologramMode m) { Config.update("storage/hologramMode", m); }
	public static boolean hologramsOn() { return hologramMode() != HologramMode.OFF; }


	public static HighlightStyle highlightStyle() { return Config.getOrCreate("storage/highlightStyle", HighlightStyle.class, HighlightStyle.GLOW); }
	public static void setHighlightStyle(HighlightStyle s) { Config.update("storage/highlightStyle", s); }

	/** What opening one of the lit-up containers does. */
	public enum OpenStops {
		THAT_ONE("Only that one stops"),
		ALL("They all stop");

		public final String label;
		OpenStops(String label) { this.label = label; }
	}

	public static OpenStops openStops() { return Config.getOrCreate("storage/openStops", OpenStops.class, OpenStops.THAT_ONE); }
	public static void setOpenStops(OpenStops v) { Config.update("storage/openStops", v); }
}
