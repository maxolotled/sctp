package com.snailtools.shoplogger.hunt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The Golden Shell (the treasure hunt): everything the mod says to the Worker's
 * /hunt endpoints. Stays silent until the hunt is switched on in the admin panel:
 * until then /hunt/status says {enabled:false} and nothing shows anywhere.
 *
 * Pairing: the spiral on the home screen asks for a code, the player redeems it
 * on sctp.nl with their verified account, and this mod gets a hunt token (kept
 * in the config, per Minecraft name). The token is what proves who's playing.
 *
 * Notes: some puzzles show a line on a screen (the item page, a shop chest's
 * hologram, the Butterfly or Sefi exchange, quest papers, backpack/ender chest).
 * The mod fetches this player's notes for the whorls they've opened and looks
 * them up by context; nothing about the puzzles is inside the mod itself.
 */
public final class HuntClient {

	private HuntClient() {}

	private static final String API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();
	private static final long STATUS_EVERY_MS = 5 * 60_000L;
	private static final long NOTES_EVERY_MS = 2 * 60_000L;
	private static final long POLL_EVERY_MS = 3_000L;
	private static final String CONFIG_TOKENS = "hunt/tokens/";
	private static final String CONFIG_SEEN = "hunt/seen";

	public static final int GOLD = 0xFFE8B93A;

	private static volatile boolean enabled;
	private static volatile Map<String, String> notes = Map.of();
	private static volatile String ledger;
	private static volatile long statusAt, notesAt, pollAt;
	private static volatile boolean busyStatus, busyNotes, busyPoll;
	private static volatile Pairing pairing;
	/** Where the prize is hidden for this hunter's finishing place (null until they finish). */
	private static volatile Finish finish;

	// ---- response shapes (fields match the Worker's JSON) ----

	public static final class Status {
		public boolean enabled, signupOpen;
		public Message broadcast;
		public List<Message> milestones;
	}
	public static final class Message { public String id, text; }

	public static final class Pairing {
		public String code, pollKey, expiresAt, error;
		public volatile String status = "waiting"; // waiting | done | expired | failed
		public volatile String ledger;
	}

	public static final class State {
		public String error;
		public boolean enabled;
		public Player player;
		public List<Whorl> whorls;
		public Apex apex;
		public Finish finish;
		public Shell shell;
	}
	/** place always; x, y, z and the password once the Shellkeeper set this place out (mod only). */
	public static final class Finish {
		public int place;
		public Integer x, y, z;
		public String password;
		public boolean ready;
		public boolean located() { return x != null && y != null && z != null; }
	}
	public static final class Player { public String ledger, mcUsername; }
	public static final class Whorl {
		public int whorl;
		public String title, intro, answerKind, puzzle;
		public boolean unlocked, solved, puzzleOpen, inMod;
		public List<Chapter> chapters;
		public Square square, target;
		public Hint nudge;
	}
	public static final class Square { public int x, z, size; }
	public static final class Chapter {
		public String id, title, clue, answerKind, fragment;
		public boolean solved;
		public List<Block> content;
		public List<Hint> hints;
	}
	public static final class Block {
		public String t, v, key, href, alt;
		public List<String> items;
		public Boolean ordered;
	}
	public static final class Hint {
		public int tier;
		public String label, availableAt, text;
		public boolean available, revealed;
	}
	public static final class Apex {
		public String title, riddle;
		public boolean solved, vaultSolved, claimed;
	}
	public static final class Shell { public boolean found; public String by; }
	public static final class AnswerResult { public String error, retryAt; public boolean correct; public State state; }

	/** Thrown (inside the future) with a message that's fine to show the player. */
	public static final class HuntException extends RuntimeException {
		public HuntException(String message) { super(message); }
	}

	public static String messageOf(Throwable t) {
		Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return c instanceof HuntException ? c.getMessage() : "Couldn't reach sctp.nl. Try again in a moment.";
	}

	// ---- what the rest of the mod asks ----

	/** The hunt is switched on (as of the last status check). */
	public static boolean enabled() { return enabled; }

	/** This player's mod is paired with the hunt. */
	public static boolean paired() { return enabled && token() != null; }

	public static String ledger() { return ledger; }

	/** The gold block to mark (see WorldHighlights), or null. */
	public static Finish finish() { return paired() ? finish : null; }

	/** A fresh state from the hunt screen: picks up the finish right away. */
	public static void remember(State s) {
		if (s != null && s.finish != null && s.finish.located()) finish = s.finish;
	}

	/** This player's note for a screen, or null. */
	public static String note(String context) {
		return paired() ? notes.get(context) : null;
	}

	public static Pairing pairing() { return pairing; }

	private static String me() { return Minecraft.getInstance().getUser().getName(); }

	private static String token() {
		return Config.get(CONFIG_TOKENS + me(), String.class);
	}

	// ---- every client tick ----

	public static void tick(Minecraft mc) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		if (!busyStatus && now - statusAt > STATUS_EVERY_MS) refreshStatus();
		if (enabled && token() != null && !busyNotes && now - notesAt > NOTES_EVERY_MS) refreshNotes();
		Pairing p = pairing;
		if (p != null && "waiting".equals(p.status) && !busyPoll && now - pollAt > POLL_EVERY_MS) poll(p);
	}

	/** Re-checks right away (opening the dashboard or the hunt screen). */
	public static void refreshSoon() {
		statusAt = 0;
		notesAt = 0;
	}

	private static void refreshStatus() {
		busyStatus = true;
		statusAt = System.currentTimeMillis();
		// the name lets a tester see the hunt while it's off for everyone else
		send(get("/hunt/status?mc=" + URLEncoder.encode(me(), StandardCharsets.UTF_8), false)).whenComplete((res, ex) -> {
			busyStatus = false;
			if (ex != null || res.statusCode() != 200) return;
			Status s;
			try { s = GSON.fromJson(res.body(), Status.class); } catch (Exception e) { return; }
			if (s == null) return;
			boolean was = enabled;
			enabled = s.enabled;
			if (enabled && !was) notesAt = 0;
			if (enabled && token() != null) announce(s);
		});
	}

	/** Broadcasts and community milestones: once each, in chat. */
	private static void announce(Status s) {
		List<String> seen = new ArrayList<>(Arrays.asList(Config.getOrDefault(CONFIG_SEEN, String[].class, new String[0])));
		List<String> lines = new ArrayList<>();
		if (s.broadcast != null && s.broadcast.id != null && !seen.contains(s.broadcast.id)) {
			seen.add(s.broadcast.id);
			lines.add(s.broadcast.text);
		}
		if (s.milestones != null) for (Message m : s.milestones) {
			if (m.id == null || seen.contains(m.id)) continue;
			seen.add(m.id);
			lines.add(m.text);
		}
		if (lines.isEmpty()) return;
		Config.update(CONFIG_SEEN, seen.subList(Math.max(0, seen.size() - 200), seen.size()).toArray(new String[0]));
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> { for (String l : lines) ChatFormat.send(mc, ChatFormat.INFO, "The Shellkeeper: " + l); });
	}

	private static void refreshNotes() {
		String token = token();
		if (token == null) return;
		busyNotes = true;
		notesAt = System.currentTimeMillis();
		send(get("/hunt/notes", true)).whenComplete((res, ex) -> {
			busyNotes = false;
			if (ex != null) return;
			if (res.statusCode() == 401) { forgetToken(); return; }
			if (res.statusCode() != 200) return;
			try {
				JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
				Map<String, String> out = new HashMap<>();
				if (o.has("notes")) o.getAsJsonObject("notes").entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
				notes = out;
				if (o.has("ledger") && !o.get("ledger").isJsonNull()) ledger = o.get("ledger").getAsString();
				Finish f = o.has("finish") && o.get("finish").isJsonObject() ? GSON.fromJson(o.get("finish"), Finish.class) : null;
				finish = f != null && f.located() ? f : null;
			} catch (Exception ignored) {
				// keep the old notes
			}
		});
	}

	private static void forgetToken() {
		Config.remove(CONFIG_TOKENS + me());
		notes = Map.of();
		ledger = null;
		finish = null;
	}

	// ---- pairing (the trailhead) ----

	/** Asks for a code to bring to sctp.nl; the tick polls until it's redeemed. */
	public static CompletableFuture<Pairing> startPairing() {
		JsonObject body = new JsonObject();
		body.addProperty("mcUsername", me());
		return send(post("/mod/hunt/pair/start", body, false)).thenApply(res -> {
			Pairing p = parse(res, Pairing.class);
			p.status = "waiting";
			pairing = p;
			pollAt = System.currentTimeMillis();
			return p;
		});
	}

	private static void poll(Pairing p) {
		busyPoll = true;
		pollAt = System.currentTimeMillis();
		send(get("/mod/hunt/pair/poll?key=" + URLEncoder.encode(p.pollKey, StandardCharsets.UTF_8), false)).whenComplete((res, ex) -> {
			busyPoll = false;
			if (ex != null || res.statusCode() != 200) return;
			try {
				JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
				String status = o.get("status").getAsString();
				if ("done".equals(status)) {
					Config.update(CONFIG_TOKENS + me(), o.get("token").getAsString());
					if (o.has("ledger") && !o.get("ledger").isJsonNull()) { ledger = o.get("ledger").getAsString(); p.ledger = ledger; }
					notesAt = 0;
					p.status = "done";
				} else if ("expired".equals(status)) {
					p.status = "expired";
				}
			} catch (Exception ignored) {
				// try again next poll
			}
		});
	}

	// ---- playing ----

	public static CompletableFuture<State> state() {
		return send(get("/hunt/state", true)).thenApply(res -> parse(res, State.class));
	}

	/** answer is the typed text (chapters, the key, the Apex, the lectern word). */
	public static CompletableFuture<AnswerResult> answer(String ref, String answer) {
		JsonObject body = new JsonObject();
		body.addProperty("ref", ref);
		body.addProperty("answer", answer);
		return send(post("/hunt/answer", body, true)).thenApply(res -> {
			AnswerResult r = parse(res, AnswerResult.class);
			notesAt = 0; // a solved whorl opens new notes
			return r;
		});
	}

	public static CompletableFuture<State> hint(String chapterId, int tier) {
		JsonObject body = new JsonObject();
		body.addProperty("chapterId", chapterId);
		body.addProperty("tier", tier);
		return send(post("/hunt/hint", body, true)).thenApply(res -> parse(res, State.class));
	}

	// ---- plumbing ----

	private static HttpRequest.Builder get(String path, boolean auth) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path)).GET();
		return auth ? withToken(b) : b;
	}

	private static HttpRequest.Builder post(String path, JsonObject body, boolean auth) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		return auth ? withToken(b) : b;
	}

	private static HttpRequest.Builder withToken(HttpRequest.Builder b) {
		String token = token();
		return token != null ? b.header("Authorization", "Bearer " + token) : b;
	}

	private static CompletableFuture<HttpResponse<String>> send(HttpRequest.Builder b) {
		return HTTP.sendAsync(b.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString())
				.exceptionally(ex -> { throw new HuntException("Couldn't reach sctp.nl. Check your connection and try again."); });
	}

	private static <T> T parse(HttpResponse<String> res, Class<T> type) {
		if (res.statusCode() == 401) {
			if (token() != null) forgetToken();
			throw new HuntException("This game isn't paired with the hunt any more. Click the spiral again.");
		}
		JsonObject o;
		try {
			o = GSON.fromJson(res.body(), JsonObject.class);
		} catch (Exception e) {
			throw new HuntException("sctp.nl sent back something unexpected (HTTP " + res.statusCode() + ").");
		}
		if (o == null) throw new HuntException("sctp.nl didn't answer. Try again in a moment.");
		if (res.statusCode() >= 400 && o.has("error")) {
			String msg = o.get("error").getAsString();
			if (o.has("retryAt") && !o.get("retryAt").isJsonNull()) msg += " (" + until(o.get("retryAt").getAsString()) + ")";
			throw new HuntException(msg);
		}
		return GSON.fromJson(o, type);
	}

	/** "in 2 h 5 min" until an ISO time, for hints and retry waits. */
	public static String until(String iso) {
		try {
			long ms = java.time.Instant.parse(iso).toEpochMilli() - System.currentTimeMillis();
			if (ms <= 0) return "now";
			long h = ms / 3_600_000L, m = (ms % 3_600_000L + 59_999L) / 60_000L;
			if (h >= 48) return "in " + Math.round(h / 24.0) + " days";
			if (h >= 1) return "in " + h + " h " + m + " min";
			return "in " + m + " min";
		} catch (Exception e) {
			return "later";
		}
	}
}
