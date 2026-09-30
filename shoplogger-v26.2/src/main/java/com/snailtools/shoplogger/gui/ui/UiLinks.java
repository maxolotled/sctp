package com.snailtools.shoplogger.gui.ui;

import net.minecraft.util.Util;

import java.net.URI;

/**
 * Opens a web page in the player's browser. The one piece of UI code that
 * differs between Minecraft versions (26.3 moved this to Blaze3D.openUri),
 * kept here so every screen can stay identical across the builds.
 */
public final class UiLinks {

	private UiLinks() {}

	public static void open(String url) {
		Util.getPlatform().openUri(URI.create(url));
	}
}
