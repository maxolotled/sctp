package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * TEMPORARY (see CHANGELOG 2.2): shows the auto-scanner's current adaptive
 * wait time (ShopAutoScanner#getCurrentWaitMs) in the top-right corner,
 * always on screen, so the 2.2 accuracy check has a direct, visible way to
 * confirm the adaptive cooldown is actually behaving instead of just trusting
 * it. Toggle lives in Advanced Settings, off by default. Delete this file and
 * its call site (QolHookManager#onHudRender) once 2.2's accuracy is
 * confirmed and this is no longer needed.
 */
public final class TempScanWaitOverlay {

	private static final String CONFIG_ENABLED = "scanning/showWaitOverlayTEMP";
	private static final int TEXT_COLOR = 0xFFFFFF00;
	private static final int MARGIN = 6;

	private TempScanWaitOverlay() {}

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, false);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	public static void onHudRender(GuiGraphicsExtractor graphics) {
		if (!isEnabled()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.font == null || client.getWindow() == null) return;

		String text = "scan wait: " + ShopAutoScanner.getInstance().getCurrentWaitMs() + "ms";
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int textWidth = client.font.width(text);
		graphics.text(client.font, text, screenWidth - textWidth - MARGIN, MARGIN, TEXT_COLOR, true);
	}
}
