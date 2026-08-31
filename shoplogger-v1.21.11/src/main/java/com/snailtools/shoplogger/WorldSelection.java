package com.snailtools.shoplogger;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The player's chosen world (set via /setworld), persisted across game
 * restarts. Shops can't be logged until this is set — sellers can operate
 * on both worlds, so every logged entry needs to know which one it's from.
 */
public final class WorldSelection {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("shoplogger-world.txt");
	private static final long REMINDER_COOLDOWN_MS = 30_000L;
	// WorldDetector's silent /help round-trip needs a moment to complete after
	// each join/reconfigure — without this, ensureSet() (called every tick by
	// e.g. ShopAutoScanner) nags the player before detection ever gets a
	// chance to finish, even though it's about to succeed on its own.
	private static final long DETECTION_GRACE_MS = 8_000L;

	private static ShopWorld current = load();
	private static long lastReminderAt = 0L;
	private static long detectionGraceUntil = 0L;

	private WorldSelection() {}

	public static ShopWorld get() {
		return current;
	}

	public static void set(ShopWorld world) {
		current = world;
		try {
			Files.writeString(FILE, world.name());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** Call whenever WorldDetector starts a fresh detection attempt (join/reconfigure) — mutes ensureSet()'s nag while it works. */
	public static void startDetectionGrace() {
		detectionGraceUntil = System.currentTimeMillis() + DETECTION_GRACE_MS;
	}

	/**
	 * Returns true if a world is set. Otherwise, nags the player (rate-limited
	 * so it doesn't spam chat while the auto-scanner keeps retrying) and
	 * returns false so the caller can refuse to log. Stays quiet during the
	 * grace window right after a join/reconfigure — see startDetectionGrace().
	 */
	public static boolean ensureSet(MinecraftClient client) {
		if (current != null) return true;

		long now = System.currentTimeMillis();
		if (now < detectionGraceUntil) return false;
		if (client.player != null && now - lastReminderAt >= REMINDER_COOLDOWN_MS) {
			lastReminderAt = now;
			ChatFormat.send(client, ChatFormat.INFO,
					"Set your world before shops can be logged — /setworld firefly or /setworld honeybee");
		}
		return false;
	}

	private static ShopWorld load() {
		try {
			if (Files.exists(FILE)) {
				return ShopWorld.fromString(Files.readString(FILE).trim());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
