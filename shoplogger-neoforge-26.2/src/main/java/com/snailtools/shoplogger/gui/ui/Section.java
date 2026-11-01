package com.snailtools.shoplogger.gui.ui;

import com.snailtools.shoplogger.gui.AuctionsScreen;
import com.snailtools.shoplogger.gui.HomeScreen;
import com.snailtools.shoplogger.gui.ItemLibraryScreen;
import com.snailtools.shoplogger.gui.ListingsScreen;
import com.snailtools.shoplogger.gui.MarketplaceScreen;
import com.snailtools.shoplogger.gui.RaredleScreen;
import com.snailtools.shoplogger.gui.SettingsScreen;
import com.snailtools.shoplogger.gui.WatchlistScreen;
import com.snailtools.shoplogger.mapart.MapartPreviewScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Function;

/** The mod's main sections, in the order the top-bar tabs show them. */
public enum Section {
	HOME("Home", "Dashboard", Items.COMPASS, p -> new HomeScreen()),
	LISTINGS("Listings", "Browse every shop listing", Items.CHEST, p -> new ListingsScreen(p, null)),
	VANILLA("Items", "Vanilla item library", Items.GRASS_BLOCK, p -> new ItemLibraryScreen(p, ItemLibraryScreen.Catalog.VANILLA)),
	RARES("Rares", "Rare item library", Items.NETHER_STAR, p -> new ItemLibraryScreen(p, ItemLibraryScreen.Catalog.RARE)),
	RAREDLE("Rare-dle", "Guess today's secret rare", Items.AMETHYST_SHARD, RaredleScreen::new),
	WATCHLIST("Watchlist", "Items you're watching", Items.SPYGLASS, WatchlistScreen::new),
	STORAGE("My storage", "Everything in your own chests", Items.BARREL, com.snailtools.shoplogger.gui.StorageScreen::new),
	MARKET("Marketplace", "Player marketplace", Items.EMERALD, MarketplaceScreen::new),
	MAPART("Mapart", "Mapart scanner", Items.FILLED_MAP, MapartPreviewScreen::new),
	AUCTIONS("Auctions", "Run rare auctions in-game", Items.BELL, AuctionsScreen::new),
	HUNT("Hunt", "The Golden Shell", Items.NAUTILUS_SHELL, com.snailtools.shoplogger.gui.HuntScreen::new),
	SETTINGS("Settings", "Settings", Items.COMPARATOR, SettingsScreen::new);

	public final String label;
	public final String description;
	private final net.minecraft.world.item.Item icon;
	private final Function<Screen, Screen> factory;

	Section(String label, String description, net.minecraft.world.item.Item icon, Function<Screen, Screen> factory) {
		this.label = label;
		this.description = description;
		this.icon = icon;
		this.factory = factory;
	}

	public ItemStack icon() {
		return Draw.stack(icon);
	}

	/** A fresh screen for this section; its Back goes to the dashboard. */
	public Screen open() {
		if (this == HOME) return new HomeScreen();
		return factory.apply(new HomeScreen());
	}

	public Screen open(Screen parent) {
		return factory.apply(parent);
	}

	/** The sections in the top bar: Auctions only when it's turned on in Settings › Advanced, Hunt only for a paired hunter. */
	public static java.util.List<Section> nav() {
		java.util.List<Section> out = new java.util.ArrayList<>();
		for (Section s : values()) {
			if (s == AUCTIONS && !com.snailtools.shoplogger.auction.AuctionHost.isShown()) continue;
			if (s == HUNT && !com.snailtools.shoplogger.hunt.HuntClient.paired()) continue;
			if (s == STORAGE && !com.snailtools.shoplogger.storage.StorageSettings.isEnabled()) continue;
			out.add(s);
		}
		return out;
	}
}
