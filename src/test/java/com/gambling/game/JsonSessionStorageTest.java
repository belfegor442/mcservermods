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

    @Test
    void corruptPrimaryFallsBackToBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        JsonSessionStorage storage = new JsonSessionStorage(file);
        UUID player = UUID.randomUUID();
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "slots", 25, PlayerState.BETTING, ""))));
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "slots", 999, PlayerState.BETTING, ""))));
        assertTrue(java.nio.file.Files.exists(dir.resolve("sessions.json.bak")),
                "saving over an existing file must keep the previous copy");

        java.nio.file.Files.writeString(file, "{{{ broken");
        GameSession restored = storage.load().get(player);
        assertNotNull(restored, "the backup must restore the committed stake");
        assertEquals(25L, restored.bet());
        assertEquals(PlayerState.BETTING, restored.state());
    }

    @Test
    void corruptPrimaryIsQuarantinedSoNextSaveKeepsBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        JsonSessionStorage storage = new JsonSessionStorage(file);
        UUID player = UUID.randomUUID();
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "slots", 25, PlayerState.BETTING, ""))));
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "slots", 999, PlayerState.BETTING, ""))));

        java.nio.file.Files.writeString(file, "{{{ broken");
        assertEquals(25L, storage.load().get(player).bet());
        assertTrue(!java.nio.file.Files.exists(file),
                "the corrupt primary must not survive the fallback load");

        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "slots", 40, PlayerState.BETTING, ""))));
        java.nio.file.Files.delete(file);
        GameSession fromBackup = storage.load().get(player);
        assertNotNull(fromBackup,
                "the next save must not have copied the corrupt primary over the backup");
        assertEquals(25L, fromBackup.bet());
    }

    @Test
    void nullResultEntryIsKeptInsteadOfDroppingTheSession(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        UUID player = UUID.randomUUID();
        java.nio.file.Files.writeString(file, """
                {"version":1,"players":{
                  "%s":{"game":"coinflip","bet":50,"state":"betting","result":null}
                }}
                """.formatted(player));

        GameSession loaded = new JsonSessionStorage(file).load().get(player);
        assertNotNull(loaded, "a null result must not drop a committed stake on restore");
        assertEquals(50L, loaded.bet());
        assertEquals(PlayerState.BETTING, loaded.state());
        assertEquals("", loaded.result());
    }

    @Test
    void missingPrimaryFallsBackToBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        JsonSessionStorage storage = new JsonSessionStorage(file);
        UUID player = UUID.randomUUID();
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "coinflip", 40, PlayerState.IN_GAME, ""))));
        assertTrue(storage.save(Map.of(player,
                new GameSession(player, "coinflip", 60, PlayerState.IN_GAME, ""))));
        java.nio.file.Files.delete(file);

        assertEquals(40L, storage.load().get(player).bet());
    }

    @Test
    void corruptFileWithoutBackupIsQuarantined(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sessions.json");
        java.nio.file.Files.writeString(file, "not json");
        JsonSessionStorage storage = new JsonSessionStorage(file);
        assertTrue(storage.load().isEmpty());

        try (var entries = java.nio.file.Files.list(dir)) {
            boolean quarantined = entries.anyMatch(p ->
                    p.getFileName().toString().startsWith("sessions.json.corrupt-"));
            assertTrue(quarantined, "the corrupt file must be preserved for inspection");
        }
        assertTrue(!java.nio.file.Files.exists(file),
                "the corrupt file must not be retried on the next load");
    }
}
