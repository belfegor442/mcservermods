package com.gambling.config;

import com.gambling.util.Validation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server configuration stored at config/gambling/gambling.json. Invalid or
 * missing values always fall back to safe defaults; the file is created on
 * first run so administrators can discover and edit it.
 */
public final class GamblingConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final long DEFAULT_CHIPS = 1_000L;
    public static final long DEFAULT_MAX_BET = Validation.MAX_BET;
    public static final int DEFAULT_AUTOSAVE_SECONDS = 60;
    public static final long DEFAULT_ROUND_COOLDOWN_MILLIS = 1_000L;
    public static final int MIN_AUTOSAVE_SECONDS = 10;
    public static final int MAX_AUTOSAVE_SECONDS = 36_000;
    public static final long MAX_ROUND_COOLDOWN_MILLIS = 60_000L;

    private static volatile GamblingConfig instance =
            new GamblingConfig(DEFAULT_CHIPS, DEFAULT_MAX_BET, DEFAULT_AUTOSAVE_SECONDS,
                    DEFAULT_ROUND_COOLDOWN_MILLIS);

    private final long defaultChips;
    private final long maxBet;
    private final int autosaveSeconds;
    private final long roundCooldownMillis;

    public GamblingConfig(long defaultChips, long maxBet, int autosaveSeconds, long roundCooldownMillis) {
        this.defaultChips = inRange(defaultChips, 0L, Validation.MAX_ADMIN_AMOUNT)
                ? defaultChips : DEFAULT_CHIPS;
        this.maxBet = inRange(maxBet, 1L, Validation.MAX_BET)
                ? maxBet : DEFAULT_MAX_BET;
        this.autosaveSeconds = (autosaveSeconds >= MIN_AUTOSAVE_SECONDS
                && autosaveSeconds <= MAX_AUTOSAVE_SECONDS)
                ? autosaveSeconds : DEFAULT_AUTOSAVE_SECONDS;
        this.roundCooldownMillis = inRange(roundCooldownMillis, 0L, MAX_ROUND_COOLDOWN_MILLIS)
                ? roundCooldownMillis : DEFAULT_ROUND_COOLDOWN_MILLIS;
    }

    public static GamblingConfig get() {
        return instance;
    }

    public static void load(Path file) {
        instance = fromFile(file);
    }

    static GamblingConfig fromFile(Path file) {
        try {
            if (!Files.exists(file)) {
                GamblingConfig defaults = new GamblingConfig(DEFAULT_CHIPS, DEFAULT_MAX_BET,
                        DEFAULT_AUTOSAVE_SECONDS, DEFAULT_ROUND_COOLDOWN_MILLIS);
                saveQuietly(file, defaults);
                return defaults;
            }
            JsonObject root;
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            long defaultChips = readLong(root, "defaultChips", DEFAULT_CHIPS, 0L,
                    Validation.MAX_ADMIN_AMOUNT, file);
            long maxBet = readLong(root, "maxBet", DEFAULT_MAX_BET, 1L, Validation.MAX_BET, file);
            int autosaveSeconds = (int) readLong(root, "autosaveSeconds", DEFAULT_AUTOSAVE_SECONDS,
                    MIN_AUTOSAVE_SECONDS, MAX_AUTOSAVE_SECONDS, file);
            long cooldown = readLong(root, "roundCooldownMillis", DEFAULT_ROUND_COOLDOWN_MILLIS,
                    0L, MAX_ROUND_COOLDOWN_MILLIS, file);
            return new GamblingConfig(defaultChips, maxBet, autosaveSeconds, cooldown);
        } catch (Exception e) {
            LOGGER.error("Failed to load config from {}: {}", file, e.toString());
            return new GamblingConfig(DEFAULT_CHIPS, DEFAULT_MAX_BET, DEFAULT_AUTOSAVE_SECONDS,
                    DEFAULT_ROUND_COOLDOWN_MILLIS);
        }
    }

    private static long readLong(JsonObject root, String key, long def, long min, long max, Path file) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return def;
        }
        long value;
        try {
            value = root.get(key).getAsLong();
        } catch (Exception e) {
            LOGGER.warn("Config {} in {} is not a number; using default {}.", key, file, def);
            return def;
        }
        if (value < min || value > max) {
            LOGGER.warn("Config {} = {} in {} is outside [{}, {}]; using default {}.",
                    key, value, file, min, max, def);
            return def;
        }
        return value;
    }

    private static void saveQuietly(Path file, GamblingConfig config) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("defaultChips", config.defaultChips);
            root.addProperty("maxBet", config.maxBet);
            root.addProperty("autosaveSeconds", config.autosaveSeconds);
            root.addProperty("roundCooldownMillis", config.roundCooldownMillis);
            Files.writeString(file, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            LOGGER.info("Wrote default configuration to {}.", file);
        } catch (Exception e) {
            LOGGER.warn("Could not write default config {}: {}", file, e.toString());
        }
    }

    private static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    public long defaultChips() {
        return defaultChips;
    }

    public long maxBet() {
        return maxBet;
    }

    public int autosaveSeconds() {
        return autosaveSeconds;
    }

    public long roundCooldownMillis() {
        return roundCooldownMillis;
    }
}
