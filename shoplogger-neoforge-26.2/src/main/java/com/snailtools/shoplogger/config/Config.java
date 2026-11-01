package com.snailtools.shoplogger.config;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

public final class Config {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("sctp/sctp.json");

    private static JsonObject root = new JsonObject();

    /** The folder sctp.json lives in (config/sctp/); My storage keeps its files in storage/ under it. */
    public static Path dir() {
        return CONFIG_PATH.getParent();
    }

    /** Whether sctp.json was already on disk the moment load() ran — the difference between
     *  a genuinely fresh install and an existing player whose config just doesn't have some
     *  particular key yet (see OnboardingLinkCheck, which needs to tell those apart). */
    private static boolean existedAtLoad = false;

    private Config() {
    }

    //called on init to load config.
    public static synchronized void load() {
        existedAtLoad = Files.exists(CONFIG_PATH);
        if (!existedAtLoad) {
            root = new JsonObject();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(
                CONFIG_PATH,
                StandardCharsets.UTF_8
        )) {
            JsonElement parsed = JsonParser.parseReader(reader);

            if (parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            } else {
                handleInvalidConfig(
                        "Config root was not a JSON object."
                );
            }
        } catch (IOException | JsonParseException exception) {
            System.err.println("[ShopLogger] Failed to load config:");
            exception.printStackTrace();
            handleInvalidConfig("Config could not be parsed.");
        }
    }

    //tries to save the file
    public static synchronized void save() {
        Path temporaryPath = CONFIG_PATH.resolveSibling(
                CONFIG_PATH.getFileName() + ".tmp"
        );

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(
                    temporaryPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                GSON.toJson(root, writer);
            }

            try {
                Files.move(
                        temporaryPath,
                        CONFIG_PATH,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporaryPath,
                        CONFIG_PATH,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            System.err.println("[ShopLogger] Failed to save config:");
            exception.printStackTrace();
        }
    }

    //used to update an in memory config variable and write change to file in real time to avoid crash loss
    public static synchronized void update(String path, Object value) {
        Objects.requireNonNull(value, "Config value cannot be null");

        PathResult result = findParent(path, true);
        result.parent().add(result.key(), GSON.toJsonTree(value));

        save();
    }

    //used to remove an in memory config variable and write change to file in real time to avoid crash loss
    public static synchronized boolean remove(String path) {
        PathResult result = findParent(path, false);

        if (result == null) {
            return false;
        }

        boolean existed = result.parent().remove(result.key()) != null;

        if (existed) {
            save();
        }

        return existed;
    }

    //rough check to see if a path exists in the in memory config.
    public static synchronized boolean has(String path) {
        return getElement(path) != null;
    }

    /** See the existedAtLoad field's own doc comment. */
    public static synchronized boolean existedBeforeLoad() {
        return existedAtLoad;
    }

    /*
        most generic getting, the lameo
        eg. Config.get("Garden/scale", Float.class);
        returns null if nothing found so it's lame but there.
     */
    public static synchronized <T> T get(String path, Class<T> type) {
        JsonElement element = getElement(path);

        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            return GSON.fromJson(element, type);
        } catch (JsonParseException | IllegalStateException exception) {
            System.err.printf(
                    "[ShopLogger] Config value '%s' could not be read as %s%n",
                    path,
                    type.getSimpleName()
            );

            return null;
        }
    }

    /*
        peak evolution of generic getter, returns a default value if the basic getter returns null
        eg. Config.get("Garden/scale", Float.class, 1.0f).
        Does not set the variable if it's missing, just swings by to check before defaulting.
     */
    public static synchronized <T> T getOrDefault(
            String path,
            Class<T> type,
            T defaultValue
    ) {
        T value = get(path, type);
        return value != null ? value : defaultValue;
    }

    /*
        peak evolution of generic getter, returns a default value if the basic getter returns null
        eg. Config.get("Garden/scale", Float.class, 1.0f).
        Does set the variable if it's missing and returns default.
     */
    public static synchronized <T> T getOrCreate(
            String path,
            Class<T> type,
            T defaultValue
    ) {
        T value = get(path, type);

        if (value != null) {
            return value;
        }

        update(path, defaultValue);
        return defaultValue;
    }

    public static synchronized JsonObject section(String path) {
        JsonElement element = getElement(path);

        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject().deepCopy();
        }

        return new JsonObject();
    }

    private static JsonElement getElement(String path) {
        String[] parts = splitPath(path);
        JsonElement current = root;

        for (String part : parts) {
            if (!current.isJsonObject()) {
                return null;
            }

            JsonObject object = current.getAsJsonObject();

            if (!object.has(part)) {
                return null;
            }

            current = object.get(part);
        }

        return current;
    }

    private static PathResult findParent(String path, boolean createMissing) {
        String[] parts = splitPath(path);
        JsonObject current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            JsonElement child = current.get(part);

            if (child == null || child.isJsonNull()) {
                if (!createMissing) {
                    return null;
                }

                JsonObject newObject = new JsonObject();
                current.add(part, newObject);
                current = newObject;
                continue;
            }

            if (!child.isJsonObject()) {
                if (!createMissing) {
                    return null;
                }

                JsonObject replacement = new JsonObject();
                current.add(part, replacement);
                current = replacement;
                continue;
            }

            current = child.getAsJsonObject();
        }

        return new PathResult(current, parts[parts.length - 1]);
    }

    //no sane reason either of those should ever throw but we'll see.
    private static String[] splitPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Config path cannot be blank");
        }

        String normalized = path
                .trim()
                .replace('\\', '/')
                .replace('.', '/');

        String[] parts = normalized.split("/+");

        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Invalid config path: " + path);
            }
        }

        return parts;
    }

    /*
        god forbid someone who doesn't know json formatting tries to manually edit the file for some reason
        start with blank and backup borked
     */
    private static void handleInvalidConfig(String reason) {
        root = new JsonObject();
        Path backupPath = CONFIG_PATH.resolveSibling(
                CONFIG_PATH.getFileName() + ".invalid-" + System.currentTimeMillis() + ".bak"
        );

        try {
            Files.copy(CONFIG_PATH, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            System.err.printf(
                    "[ShopLogger] %s Starting with empty config. Invalid config was preserved at %s%n",
                    reason,
                    backupPath
            );
        } catch (IOException backupException) {
            System.err.printf(
                    "[ShopLogger] %s Starting with empty config, but failed to preserve invalid config at %s%n",
                    reason,
                    backupPath
            );
        }
    }

    private record PathResult(JsonObject parent, String key) {
    }
}