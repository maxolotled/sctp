package com.snailtools.shoplogger.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Landing screen for the in-game Item Library (default hotkey: X). Mirrors
 * the website's role as an entry point — from here you can browse either
 * catalog or open settings.
 */
public class HomeScreen extends Screen {

	public HomeScreen() {
		super(Component.literal("Snailcraft Trading Post"));
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int w = 220;
		int y = height / 2 - 72;

		addRenderableWidget(Button.builder(Component.literal("Browse Listings"), btn ->
				minecraft.setScreenAndShow(new ListingsScreen(this, null)))
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Vanilla Items"), btn ->
				minecraft.setScreenAndShow(new ItemLibraryScreen(this, ItemLibraryScreen.Catalog.VANILLA)))
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Rare Items"), btn ->
				minecraft.setScreenAndShow(new ItemLibraryScreen(this, ItemLibraryScreen.Catalog.RARE)))
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Watchlist"), btn ->
				minecraft.setScreenAndShow(new WatchlistScreen(this)))
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Settings"), btn ->
				minecraft.setScreenAndShow(new SettingsScreen(this)))
				.bounds(centerX - w / 2, y, w, 20).build());
		y += 32;

		addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
				.bounds(centerX - w / 2, y, w, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, height / 2 - 102, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		RemoteTextureCache.clear(Minecraft.getInstance());
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
