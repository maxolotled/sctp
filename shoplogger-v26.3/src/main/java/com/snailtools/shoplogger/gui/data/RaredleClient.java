package com.snailtools.shoplogger.gui.data;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * In-game Rare-dle: the same daily game as sctp.nl/rare-dle, on the same
 * account, so it counts for streaks and the leaderboard.
 *
 * Signing in needs no password: the Worker's POST /mod/login matches the
 * player's Minecraft name to the sctp.nl account linked to it, and returns a
 * session that only works for Rare-dle. The token stays in memory only.
 */
public final class RaredleClient {

	private static final String API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();

	private static volatile String token;

	private RaredleClient() {}

	// ---- response shapes (fields match the Worker's JSON) ----

	public static final class Cell { public String k, v, s, d; }

	public static final class Guess {
		public String itemId, name, texture, note;
		public List<Cell> feedback;
	}

	public static final class PixelHint { public int size; public List<String> cells; }

	public static final class Stats { public int played, wins, winRate, points, streak, bestStreak; public Double avgGuesses; }

	public static final class Answer { public String id, name, texture, effect, category, releaseDate, obtainedFrom; }

	public static final class State {
		public String date, status, error;
		public int maxGuesses, score;
		public List<Guess> guesses;
		public PixelHint pixelHint;
		public Stats stats;
		public Answer answer;
	}

	/** Thrown (inside the future) with a message that's fine to show the player as-is. */
	public static final class RaredleException extends RuntimeException {
		public RaredleException(String message) { super(message); }
	}

	public static CompletableFuture<State> state() {
		return withLogin(() -> send(HttpRequest.newBuilder(URI.create(API_BASE + "/raredle/state")).GET()));
	}

	public static CompletableFuture<State> guess(String itemId) {
		String body = GSON.toJson(Map.of("itemId", itemId));
		return withLogin(() -> send(HttpRequest.newBuilder(URI.create(API_BASE + "/raredle/guess"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))));
	}

	/** Runs a call with a session, signing in first if needed — and once more if the session turned out to be expired. */
	private static CompletableFuture<State> withLogin(java.util.function.Supplier<CompletableFuture<HttpResponse<String>>> call) {
		CompletableFuture<Void> ready = token != null ? CompletableFuture.completedFuture(null) : login();
		return ready.thenCompose(v -> call.get()).thenCompose(res -> {
			if (res.statusCode() != 401) return CompletableFuture.completedFuture(res);
			token = null;
			return login().thenCompose(v -> call.get());
		}).thenApply(RaredleClient::parse);
	}

	private static CompletableFuture<HttpResponse<String>> send(HttpRequest.Builder b) {
		HttpRequest req = b.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(15)).build();
		return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
	}

	private static State parse(HttpResponse<String> res) {
		State s;
		try {
			s = GSON.fromJson(res.body(), State.class);
		} catch (Exception e) {
			throw new RaredleException("Rare-dle sent back something unexpected. Try again in a minute.");
		}
		if (s == null) throw new RaredleException("Rare-dle didn't answer. Try again in a minute.");
		if (s.error != null) throw new RaredleException(s.error);
		return s;
	}

	private static CompletableFuture<Void> login() {
		return CompletableFuture.runAsync(() -> {
			String body = GSON.toJson(Map.of("username", Minecraft.getInstance().getUser().getName()));
			HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + "/mod/login"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.timeout(Duration.ofSeconds(15))
					.build();
			HttpResponse<String> res;
			try {
				res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				throw new RaredleException("Couldn't reach sctp.nl. Check your connection and try again.");
			}
			Map<?, ?> out = GSON.fromJson(res.body(), Map.class);
			if (res.statusCode() != 200 || out == null || out.get("token") == null) {
				Object err = out != null ? out.get("error") : null;
				throw new RaredleException(err != null ? err.toString() : "Couldn't sign in to sctp.nl (HTTP " + res.statusCode() + ").");
			}
			token = out.get("token").toString();
		});
	}

	/** The message to show for a failed call. */
	public static String messageOf(Throwable t) {
		Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return c instanceof RaredleException ? c.getMessage() : "Couldn't reach Rare-dle. Try again in a minute.";
	}
}
