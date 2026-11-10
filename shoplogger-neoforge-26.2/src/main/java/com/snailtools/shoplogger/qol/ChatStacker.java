package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stacks repeated chat (Settings › Look & tools, on by default): when a message is
 * exactly the same as one shown in the last 2 minutes, that older line is removed
 * and the new one is added at the bottom with "×2", "×3"... after it. Other
 * messages in between don't matter, so a repeat never shows twice.
 *
 * A message with a linked item ("[Shulker Box]") can read the same while the
 * items differ, so those get a ▸ after the count: clicking it (/sctpunfold)
 * puts every original message back in that line's place, each with its own item.
 * ChatComponentMixin calls this.
 */
public final class ChatStacker {

	private ChatStacker() {}

	private static final String CONFIG_ENABLED = "chat/stackRepeatsV3"; // on by default (a new key, so installs that saved "off" before start on too)
	private static final long WINDOW_MS = 120_000L;
	/** How many stacked lines with linked items can still be unfolded. */
	private static final int UNFOLD_KEEP = 100;

	/** What the chat component exposes to us (ChatComponentMixin implements it). */
	public interface ChatAccess {
		List<GuiMessage> shoplogger$allMessages();
		void shoplogger$refresh();
	}

	/** A chat line we may stack onto: the line itself, its text without "×N", how many it stands for, and (linked items) the originals. */
	private record Seen(GuiMessage line, String text, int count, long at, int id, List<GuiMessage> originals) {}

	private static final List<Seen> recent = new ArrayList<>();
	/** Stacked lines with linked items, by id, oldest first: what /sctpunfold puts back. */
	private static final Map<Integer, Seen> foldable = new LinkedHashMap<>();
	private static int nextId = 1;
	private static ChatAccess chat;

	// the message being added right now (see added)
	private static String pendingText;
	private static int pendingCount;
	private static Component pendingRaw;
	private static int pendingId;
	private static List<GuiMessage> pendingOriginals;

	/** What to show, and whether an older line was taken out of chat (the caller then redraws chat). */
	public record Result(Component message, boolean removed) {}

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
		if (!value) recent.clear();
	}

	/** A message with a linked item: these can look alike while the items differ. */
	private static boolean hasLink(String text) {
		int open = text.indexOf('[');
		return open >= 0 && text.indexOf(']', open) > open;
	}

	/** Called before a message is added, with chat's own list of lines (newest first). */
	public static Result onMessage(ChatAccess from, Component message, List<GuiMessage> allMessages) {
		chat = from;
		String text = message.getString().trim();
		long now = System.currentTimeMillis();
		recent.removeIf(s -> now - s.at() > WINDOW_MS);
		pendingText = text;
		pendingCount = 1;
		pendingRaw = message;
		pendingId = 0;
		pendingOriginals = null;
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
		net.minecraft.network.chat.MutableComponent shown = message.copy().append(Component.literal(" ×" + pendingCount).withStyle(ChatFormatting.GRAY));
		if (match.originals() != null) {
			foldable.remove(match.id());
			pendingId = nextId++;
			pendingOriginals = new ArrayList<>(match.originals());
			shown.append(Component.literal(" ▸").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.RunCommand("/sctpunfold " + pendingId))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show all " + pendingCount + " messages: the linked items may differ")))));
		}
		return new Result(shown, true);
	}

	/** Called after a message was added, with the newest chat line. */
	public static void added(GuiMessage newest) {
		if (newest == null || pendingText == null) return;
		if (isEnabled()) {
			List<GuiMessage> originals = null;
			int id = pendingId;
			if (hasLink(pendingText)) {
				// the message as it came in (the line shown may carry "×N ▸")
				originals = pendingOriginals == null ? new ArrayList<>() : pendingOriginals;
				originals.add(new GuiMessage(newest.addedTime(), pendingRaw, newest.signature(), newest.source(), newest.tag()));
				if (id == 0) id = nextId++;
			}
			Seen seen = new Seen(newest, pendingText, pendingCount, System.currentTimeMillis(), id, originals);
			recent.add(seen);
			if (originals != null && originals.size() > 1) {
				foldable.put(id, seen);
				while (foldable.size() > UNFOLD_KEEP) foldable.remove(foldable.keySet().iterator().next());
			}
		}
		pendingText = null;
		pendingRaw = null;
		pendingOriginals = null;
		if (recent.size() > 100) recent.removeFirst(); // chat keeps 100 lines anyway
	}

	/** /sctpunfold: puts a stacked line's original messages back where it is. False when it's no longer in chat. */
	public static boolean unfold(int id) {
		Seen s = foldable.remove(id);
		if (s == null || chat == null) return false;
		List<GuiMessage> all = chat.shoplogger$allMessages();
		int at = -1;
		for (int i = 0; i < all.size(); i++) if (all.get(i) == s.line()) { at = i; break; }
		if (at < 0) return false;
		all.remove(at);
		// newest first in chat: putting the oldest in first leaves the newest on top
		for (GuiMessage m : s.originals()) all.add(at, m);
		recent.remove(s);
		chat.shoplogger$refresh();
		return true;
	}

	private static boolean containsSame(List<GuiMessage> list, GuiMessage line) {
		for (GuiMessage m : list) if (m == line) return true;
		return false;
	}

	private static void removeSame(List<GuiMessage> list, GuiMessage line) {
		for (Iterator<GuiMessage> it = list.iterator(); it.hasNext(); ) if (it.next() == line) { it.remove(); return; }
	}
}
