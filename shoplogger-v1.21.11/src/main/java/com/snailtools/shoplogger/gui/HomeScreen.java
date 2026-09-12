package com.snailtools.shoplogger.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

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

		int halfW = (w - 6) / 2;
		addDrawableChild(ButtonWidget.builder(Text.literal("Suggest a feature"), btn ->
				openUrl("https://sctp.nl/suggest/"))
				.dimensions(centerX - w / 2, y, halfW, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Report a bug"), btn ->
				openUrl("https://sctp.nl/bug/"))
				.dimensions(centerX - w / 2 + halfW + 6, y, halfW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> close())
				.dimensions(centerX - w / 2, y, w, 20).build());
	}

	private void openUrl(String url) {
		Util.getOperatingSystem().open(URI.create(url));
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
