package com.snailtools.shoplogger;

import com.mojang.blaze3d.platform.InputConstants;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The mod's keys live in Settings › Keys, not in Options › Controls: each loader
 * makes its KeyMappings and hands them here instead of registering them with
 * the game's options. A KeyMapping still works without that (it joins the
 * game's key lookup when it's made); we only save and load its key ourselves,
 * in sctp.json. Keys changed in Controls before this moved are taken over once
 * from options.txt.
 */
public final class ModKeys {

	private ModKeys() {}

	private static final List<KeyMapping> KEYS = new ArrayList<>();
	private static boolean loaded;

	/** Called by each loader with every key it made; returns it for convenience. */
	public static KeyMapping add(KeyMapping key) {
		KEYS.add(key);
		return key;
	}

	public static List<KeyMapping> all() { return KEYS; }

	/** Every client tick (QolHookManager): the saved keys are put back once the game is up. */
	public static void tick() {
		if (loaded || KEYS.isEmpty()) return;
		loaded = true;
		List<String> options = optionsTxt();
		for (KeyMapping k : KEYS) {
			String saved = Config.get(configKey(k), String.class);
			if (saved == null) {
				// set in Controls before it moved here: take that over, once
				String prefix = "key_" + k.getName() + ":";
				for (String line : options) if (line.startsWith(prefix)) { saved = line.substring(prefix.length()).trim(); break; }
				if (saved != null) Config.update(configKey(k), saved);
			}
			if (saved != null) {
				try { k.setKey(InputConstants.getKey(saved)); } catch (Exception e) { /* an unknown key name: keep the default */ }
			}
		}
		KeyMapping.resetMapping();
	}

	/** Gives a key a new binding (InputConstants.UNKNOWN unbinds it) and saves it. */
	public static void set(KeyMapping k, InputConstants.Key key) {
		k.setKey(key);
		Config.update(configKey(k), k.saveString());
		KeyMapping.resetMapping();
	}

	public static void resetAll() {
		for (KeyMapping k : KEYS) set(k, k.getDefaultKey());
	}

	/** "Open Item Library" etc., as the key list names it. */
	public static String label(KeyMapping k) {
		String s = Component.translatable(k.getName()).getString();
		int cut = s.indexOf(" (");
		return cut > 0 ? s.substring(0, cut) : s;
	}

	/** The part in brackets of a key's name, if any ("hold Open Item Library + this, default X+I"). */
	public static String hint(KeyMapping k) {
		String s = Component.translatable(k.getName()).getString();
		int cut = s.indexOf(" (");
		return cut > 0 && s.endsWith(")") ? s.substring(cut + 2, s.length() - 1) : "";
	}

	/** The key it's on now, e.g. "X", or "Not set". */
	public static String keyText(KeyMapping k) {
		return k.isUnbound() ? "Not set" : k.getTranslatedKeyMessage().getString();
	}

	private static String configKey(KeyMapping k) { return "keys/" + k.getName(); }

	private static List<String> optionsTxt() {
		try {
			Path f = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("options.txt");
			return Files.exists(f) ? Files.readAllLines(f, StandardCharsets.UTF_8) : List.of();
		} catch (Exception e) {
			return List.of();
		}
	}
}
