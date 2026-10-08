package com.snailtools.shoplogger;

import com.snailtools.shoplogger.gui.NoticeScreen;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Queue for the mod's on-screen popups (welcome, updated, update available —
 * see OnboardingLinkCheck and UpdateNoticeCheck). Shown one at a time, and only
 * while you're in the world with no other screen open, so a popup never
 * interrupts a menu, a chest or chat. A short delay after joining lets the
 * world finish loading first.
 */
public final class NoticePopups {

	private NoticePopups() {}

	private record Notice(String heading, String message, String buttonLabel, String url) {}

	private static final Deque<Notice> QUEUE = new ArrayDeque<>();
	private static final int SETTLE_TICKS = 40; // 2s in the world before the first popup
	private static int calmTicks = 0;
	private static boolean showing = false;

	public static void show(String heading, String message, String buttonLabel, String url) {
		QUEUE.add(new Notice(heading, message, buttonLabel, url));
	}

	/** Call every client tick. */
	public static void tick(Minecraft client) {
		if (showing || QUEUE.isEmpty()) return;
		if (client.player == null || client.level == null || client.gui.screen() != null) {
			calmTicks = 0;
			return;
		}
		if (++calmTicks < SETTLE_TICKS) return;
		Notice n = QUEUE.poll();
		showing = true;
		calmTicks = 0;
		client.setScreenAndShow(new NoticeScreen(n.heading(), n.message(), n.buttonLabel(), n.url(), () -> showing = false));
	}
}
