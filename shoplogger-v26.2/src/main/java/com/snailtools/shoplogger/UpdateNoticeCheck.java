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
 * If so, print the configured message to chat. Doesn't depend on world
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

		MutableComponent msg = Component.literal("[ShopLogger] ").withStyle(ChatFormat.PREFIX)
				.append(Component.literal("Update available: ").withStyle(ChatFormat.SUCCESS))
				.append(parseLinkedMessage(notice.message));

		client.player.sendSystemMessage(msg);
	}

	// [text](https://example.com) anywhere in the message becomes a clickable, underlined
	// link that opens that URL — everything else renders as plain text, same as before.
	// This is parsed entirely client-side from whatever an admin typed into the admin panel's
	// "Chat message" box, so a link can be added to the notice without shipping a new mod build.
	private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)");

	private static Component parseLinkedMessage(String message) {
		MutableComponent out = Component.literal("");
		Matcher m = LINK_PATTERN.matcher(message);
		int last = 0;
		while (m.find()) {
			if (m.start() > last) out.append(Component.literal(message.substring(last, m.start())).withStyle(ChatFormat.RESULT));
			String text = m.group(1), url = m.group(2);
			try {
				out.append(Component.literal(text).setStyle(Style.EMPTY
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))));
			} catch (IllegalArgumentException e) {
				// malformed URL (typo in the admin panel) — fall back to the raw text rather than breaking the notice
				out.append(Component.literal(m.group(0)).withStyle(ChatFormat.RESULT));
			}
			last = m.end();
		}
		if (last < message.length()) out.append(Component.literal(message.substring(last)).withStyle(ChatFormat.RESULT));
		return out;
	}

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
