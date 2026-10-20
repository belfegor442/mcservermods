package com.gambling.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSessionStorageTest {
    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) {
        JsonSessionStorage storage = new JsonSessionStorage(dir.resolve("sessions.json"));
        assertTrue(storage.load().isEmpty());
    }

    @Test
    void corruptFileLoadsEmptyWithoutThrowing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        java.nio.file.Files.writeString(file, "not json at all");
        JsonSessionStorage storage = new JsonSessionStorage(file);
        assertTrue(storage.load().isEmpty());
    }

    @Test
    void roundTripPreservesSessions(@TempDir Path dir) {
        JsonSessionStorage storage = new JsonSessionStorage(dir.resolve("sessions.json"));
        UUID player = UUID.randomUUID();
        GameSession session = new GameSession(player, "coinflip", 100, PlayerState.IN_GAME, "");
        assertTrue(storage.save(Map.of(player, session)));

        GameSession loaded = storage.load().get(player);
        assertNotNull(loaded);
        assertEquals("coinflip", loaded.gameType());
        assertEquals(100L, loaded.bet());
        assertEquals(PlayerState.IN_GAME, loaded.state());
        assertEquals("", loaded.result());
    }

    @Test
    void roundTripPreservesResolvedResult(@TempDir Path dir) {
        JsonSessionStorage storage = new JsonSessionStorage(dir.resolve("sessions.json"));
        UUID player = UUID.randomUUID();
        GameSession session = new GameSession(player, "roulette", 50, PlayerState.FINALIZING, "17 RED — WIN");
        assertTrue(storage.save(Map.of(player, session)));

        GameSession loaded = storage.load().get(player);
        assertEquals(PlayerState.FINALIZING, loaded.state());
        assertEquals("17 RED — WIN", loaded.result());
    }
}
