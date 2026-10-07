package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;

/** Whether /search opens the in-game Listings GUI (default) or prints results to chat. */
public final class SearchPreferences {

	private static final String CONFIG_GUI_SEARCH = "search/useGui";
	private static final String CONFIG_HIDE_DISPLAY = "search/hideDisplayListings";

	private SearchPreferences() {}

	/** Hide [DISPLAY] (showcase, not for sale) listings everywhere listings are shown in-game, like the website's checkbox. */
	public static boolean hideDisplayListings() {
		return Config.getOrCreate(CONFIG_HIDE_DISPLAY, Boolean.class, false);
	}

	public static void setHideDisplayListings(boolean value) {
		Config.update(CONFIG_HIDE_DISPLAY, value);
	}

	public static boolean isGuiSearch() {
		return Config.getOrCreate(CONFIG_GUI_SEARCH, Boolean.class, true);
	}

	public static void setGuiSearch(boolean value) {
		Config.update(CONFIG_GUI_SEARCH, value);
	}
}
