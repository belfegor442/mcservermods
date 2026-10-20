package com.gambling.config;

import com.gambling.util.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamblingConfigTest {
    @Test
    void missingFileIsCreatedWithDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gambling").resolve("gambling.json");
        GamblingConfig config = GamblingConfig.fromFile(file);

        assertEquals(GamblingConfig.DEFAULT_CHIPS, config.defaultChips());
        assertEquals(GamblingConfig.DEFAULT_MAX_BET, config.maxBet());
        assertEquals(GamblingConfig.DEFAULT_AUTOSAVE_SECONDS, config.autosaveSeconds());
        assertEquals(GamblingConfig.DEFAULT_ROUND_COOLDOWN_MILLIS, config.roundCooldownMillis());
        assertTrue(Files.exists(file), "the defaults file must be written so admins can edit it");

        GamblingConfig reloaded = GamblingConfig.fromFile(file);
        assertEquals(config.maxBet(), reloaded.maxBet());
        assertEquals(config.defaultChips(), reloaded.defaultChips());
    }

    @Test
    void validValuesAreParsed(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gambling.json");
        Files.writeString(file, """
                {"defaultChips":500,"maxBet":10000,"autosaveSeconds":300,"roundCooldownMillis":2500}
                """);
        GamblingConfig config = GamblingConfig.fromFile(file);
        assertEquals(500L, config.defaultChips());
        assertEquals(10000L, config.maxBet());
        assertEquals(300, config.autosaveSeconds());
        assertEquals(2500L, config.roundCooldownMillis());
    }

    @Test
    void outOfRangeValuesFallBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gambling.json");
        Files.writeString(file, """
                {"defaultChips":-5,"maxBet":0,"autosaveSeconds":1,"roundCooldownMillis":999999999}
                """);
        GamblingConfig config = GamblingConfig.fromFile(file);
        assertEquals(GamblingConfig.DEFAULT_CHIPS, config.defaultChips());
        assertEquals(GamblingConfig.DEFAULT_MAX_BET, config.maxBet());
        assertEquals(GamblingConfig.DEFAULT_AUTOSAVE_SECONDS, config.autosaveSeconds());
        assertEquals(GamblingConfig.DEFAULT_ROUND_COOLDOWN_MILLIS, config.roundCooldownMillis());
    }

    @Test
    void garbageFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gambling.json");
        Files.writeString(file, "not a config {{{");
        GamblingConfig config = GamblingConfig.fromFile(file);
        assertEquals(GamblingConfig.DEFAULT_MAX_BET, config.maxBet());
        assertEquals(GamblingConfig.DEFAULT_CHIPS, config.defaultChips());
    }

    @Test
    void constructorClampsInvalidValues() {
        GamblingConfig config = new GamblingConfig(-1, Validation.MAX_BET + 5, -3, -7);
        assertEquals(GamblingConfig.DEFAULT_CHIPS, config.defaultChips());
        assertEquals(GamblingConfig.DEFAULT_MAX_BET, config.maxBet());
        assertEquals(GamblingConfig.DEFAULT_AUTOSAVE_SECONDS, config.autosaveSeconds());
        assertEquals(GamblingConfig.DEFAULT_ROUND_COOLDOWN_MILLIS, config.roundCooldownMillis());
    }
}
