package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Stacks repeated chat (Settings › Look & tools, on by default): when a message is
 * exactly the same as one shown in the last 2 minutes, that older line is removed
 * and the new one is added at the bottom with "×2", "×3"... after it. Other
 * messages in between don't matter, so a repeat never shows twice.
 * ChatComponentMixin calls this.
 */
public final class ChatStacker {

	private ChatStacker() {}

	private static final String CONFIG_ENABLED = "chat/stackRepeatsV3"; // on by default (a new key, so installs that saved "off" before start on too)
	private static final long WINDOW_MS = 120_000L;

	/** A chat line we may stack onto: the line itself, its text without "×N", and how many it stands for. */
	private record Seen(GuiMessage line, String text, int count, long at) {}

	private static final List<Seen> recent = new ArrayList<>();
	private static String pendingText; // the message being added right now (see added)
	private static int pendingCount;

	/** What to show, and whether an older line was taken out of chat (the caller then redraws chat). */
	public record Result(Component message, boolean removed) {}

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
		if (!value) recent.clear();
	}

	/** Called before a message is added, with chat's own list of lines (newest first). */
	public static Result onMessage(Component message, List<GuiMessage> allMessages) {
		String text = message.getString().trim();
		long now = System.currentTimeMillis();
		recent.removeIf(s -> now - s.at() > WINDOW_MS);
		pendingText = text;
		pendingCount = 1;
		if (!isEnabled() || text.isEmpty()) return new Result(message, false);

		// the newest earlier copy that's still in chat
		Seen match = null;
		for (int i = recent.size() - 1; i >= 0 && match == null; i--) {
			Seen s = recent.get(i);
			if (s.text().equals(text) && containsSame(allMessages, s.line())) match = s;
		}
		if (match == null) return new Result(message, false);

		removeSame(allMessages, match.line());
		recent.remove(match);
		pendingCount = match.count() + 1;
		return new Result(message.copy().append(Component.literal(" ×" + pendingCount).withStyle(ChatFormatting.GRAY)), true);
	}

	/** Called after a message was added, with the newest chat line. */
	public static void added(GuiMessage newest) {
		if (newest == null || pendingText == null) return;
		if (isEnabled()) recent.add(new Seen(newest, pendingText, pendingCount, System.currentTimeMillis()));
		pendingText = null;
		if (recent.size() > 100) recent.removeFirst(); // chat keeps 100 lines anyway
	}

	private static boolean containsSame(List<GuiMessage> list, GuiMessage line) {
		for (GuiMessage m : list) if (m == line) return true;
		return false;
	}

	private static void removeSame(List<GuiMessage> list, GuiMessage line) {
		for (Iterator<GuiMessage> it = list.iterator(); it.hasNext(); ) if (it.next() == line) { it.remove(); return; }
	}
}
