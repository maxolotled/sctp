package com.snailtools.shoplogger.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Landing screen for the in-game Item Library (default hotkey: X). Mirrors
 * the website's role as an entry point — from here you can browse either
 * catalog or open settings.
 */
public class HomeScreen extends Screen {

	public HomeScreen() {
		super(Text.literal("Snailcraft Trading Post"));
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int w = 220;
		int y = height / 2 - 72;

		addDrawableChild(ButtonWidget.builder(Text.literal("Browse Listings"), btn ->
				client.setScreen(new ListingsScreen(this, null)))
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Vanilla Items"), btn ->
				client.setScreen(new ItemLibraryScreen(this, ItemLibraryScreen.Catalog.VANILLA)))
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Rare Items"), btn ->
				client.setScreen(new ItemLibraryScreen(this, ItemLibraryScreen.Catalog.RARE)))
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Watchlist"), btn ->
				client.setScreen(new WatchlistScreen(this)))
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), btn ->
				client.setScreen(new SettingsScreen(this)))
				.dimensions(centerX - w / 2, y, w, 20).build());
		y += 32;

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> close())
				.dimensions(centerX - w / 2, y, w, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 102, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		RemoteTextureCache.clear(MinecraftClient.getInstance());
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
