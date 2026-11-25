package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import com.snailtools.shoplogger.gui.data.ModPopup;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;

/**
 * Runs once right after the world becomes known following a join. It used to
 * post "someone is selling a watched item for X" and marketplace messages in
 * chat; those were removed at the request of the Snailcraft admins (the
 * watchlist's own alerts, when a scan of yours finds a watched item, still
 * work — see WatchlistAlert). What's left:
 *   - the join ping (GET /mod/join), which is how mod users are counted;
 *   - the popup an admin can set for mod users (admin.html, "Mod popup"),
 *     shown once per popup: the id it was shown for is remembered in Config.
 */
public final class WatchlistJoinCheck {

	private static final String SEEN_KEY = "notice/lastModPopup";
	private static boolean pending = false;

	private WatchlistJoinCheck() {}

	/** Call from ClientPlayConnectionEvents.JOIN / ClientConfigurationConnectionEvents.COMPLETE. */
	public static void requestCheck() {
		pending = true;
	}

	/** Call every tick — runs exactly once per join, as soon as the world is actually known. */
	public static void tick(Minecraft client) {
		if (!pending || client.player == null) return;
		if (WorldSelection.get() == null) return; // wait for WorldDetector to finish
		// only on Snailcraft itself (a real "Current World:" read on this connection),
		// never on singleplayer or another server
		if (!WorldDetector.getInstance().isConfirmedThisSession()) return;
		pending = false;

		String self = client.getUser().getName();
		if (self == null || self.isEmpty()) return;
		WebDataClient.fetchModJoin(self)
				.thenAccept(popup -> client.execute(() -> maybeShow(popup)))
				.exceptionally(ex -> null);
	}

	private static void maybeShow(ModPopup p) {
		if (p == null || !p.enabled || p.id == null || p.id.isEmpty()) return;
		if (p.message == null || p.message.isBlank()) return;
		if (p.id.equals(Config.get(SEEN_KEY, String.class))) return;
		Config.update(SEEN_KEY, p.id);
		NoticePopups.show(
				p.heading == null || p.heading.isBlank() ? "A message from SCTP" : p.heading,
				p.message,
				p.buttonLabel == null || p.buttonLabel.isBlank() ? "Read more" : p.buttonLabel,
				p.url);
	}
}
