package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.data.UpdateNotice;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time check, run right after join: is this mod version below whatever
 * minimum an admin has configured (see admin.html's "Update notice" section)?
 * If so, show the configured message as an on-screen popup (see NoticePopups). Doesn't depend on world
 * detection at all (unlike WatchlistJoinCheck) — just needs the player to exist.
 */
public final class UpdateNoticeCheck {

	private static boolean pending = false;

	private UpdateNoticeCheck() {}

	/** Call from ClientPlayConnectionEvents.JOIN / ClientConfigurationConnectionEvents.COMPLETE. */
	public static void requestCheck() {
		pending = true;
	}

	/** Call every tick — runs the check exactly once per join. */
	public static void tick(Minecraft client) {
		if (!pending || client.player == null) return;
		pending = false;

		WebDataClient.fetchUpdateNotice()
				.thenAccept(notice -> client.execute(() -> report(client, notice)))
				.exceptionally(ex -> null);
	}

	private static void report(Minecraft client, UpdateNotice notice) {
		if (notice == null || !notice.enabled) return;
		if (notice.message == null || notice.message.isEmpty()) return;
		if (isVersionAtLeast(modVersion(), notice.minVersion)) return; // already up to date

		// The admin's message may contain [text](url) links: the first one becomes
		// the popup's button, and every link shows as plain text in the message.
		Matcher m = LINK_PATTERN.matcher(notice.message);
		String button = "Get the update", url = "https://modrinth.com/mod/sc-shoplogger";
		if (m.find()) { button = m.group(1); url = m.group(2); }
		String text = LINK_PATTERN.matcher(notice.message).replaceAll("$1");
		NoticePopups.show("Shop Logger update available", text, button, url);
	}

	// [text](url) in the admin's message: the first link becomes the popup's button.
	private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)");

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer("shoplogger")
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("0");
	}

	/** Mirrors worker.js's isVersionAtLeast — missing/unparseable segments count as 0. */
	private static boolean isVersionAtLeast(String version, String min) {
		String[] a = String.valueOf(version).split("\\.");
		String[] b = String.valueOf(min).split("\\.");
		int len = Math.max(a.length, b.length);
		for (int i = 0; i < len; i++) {
			int av = parseSegment(a, i);
			int bv = parseSegment(b, i);
			if (av != bv) return av > bv;
		}
		return true;
	}

	private static int parseSegment(String[] parts, int i) {
		if (i >= parts.length) return 0;
		try {
			return Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
