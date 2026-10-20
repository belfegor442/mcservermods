package com.gambling.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonProfileStorageTest {
    @Test
    void missingFilesLoadEmpty(@TempDir Path dir) {
        JsonProfileStorage storage = new JsonProfileStorage(dir.resolve("profiles.json"));
        assertTrue(storage.load().isEmpty());
    }

    @Test
    void roundTripPreservesProfiles(@TempDir Path dir) {
        JsonProfileStorage storage = new JsonProfileStorage(dir.resolve("profiles.json"));
        UUID id = UUID.randomUUID();
        assertTrue(storage.save(Map.of(id, new PlayerProfile(id, "Steve", 1234, 56, 789))));

        PlayerProfile loaded = storage.load().get(id);
        assertNotNull(loaded);
        assertEquals("Steve", loaded.name());
        assertEquals(1234L, loaded.chips());
        assertEquals(56L, loaded.xp());
        assertEquals(789L, loaded.totalWagered());
    }

    @Test
    void corruptPrimaryFallsBackToBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profiles.json");
        JsonProfileStorage storage = new JsonProfileStorage(file);
        UUID id = UUID.randomUUID();
        assertTrue(storage.save(Map.of(id, new PlayerProfile(id, "Steve", 100, 0, 0))));
        assertTrue(storage.save(Map.of(id, new PlayerProfile(id, "Steve", 200, 0, 0))));
        assertTrue(Files.exists(dir.resolve("profiles.json.bak")),
                "saving over an existing file must keep the previous copy");

        Files.writeString(file, "{{{ broken");
        PlayerProfile loaded = storage.load().get(id);
        assertNotNull(loaded, "the backup must preserve the balance");
        assertEquals(100L, loaded.chips());
    }

    @Test
    void missingPrimaryFallsBackToBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profiles.json");
        JsonProfileStorage storage = new JsonProfileStorage(file);
        UUID id = UUID.randomUUID();
        assertTrue(storage.save(Map.of(id, new PlayerProfile(id, "Steve", 100, 0, 0))));
        assertTrue(storage.save(Map.of(id, new PlayerProfile(id, "Steve", 200, 0, 0))));
        Files.delete(file);

        assertEquals(100L, storage.load().get(id).chips());
    }

    @Test
    void corruptFileWithoutBackupIsQuarantined(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profiles.json");
        Files.writeString(file, "not json");
        JsonProfileStorage storage = new JsonProfileStorage(file);
        assertTrue(storage.load().isEmpty());

        try (var entries = Files.list(dir)) {
            boolean quarantined = entries.anyMatch(p ->
                    p.getFileName().toString().startsWith("profiles.json.corrupt-"));
            assertTrue(quarantined, "the corrupt file must be preserved for inspection");
        }
        assertTrue(!Files.exists(file),
                "the corrupt file must not be retried on the next load");
    }

    @Test
    void partialFileKeepsValidEntriesAndDropsCorruptOnes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profiles.json");
        UUID good = UUID.randomUUID();
        Files.writeString(file, """
                {"version":1,"players":{
                  "%s":{"name":"Steve","chips":42,"xp":1,"totalWagered":2},
                  "not-a-uuid":{"name":"Ghost","chips":999999}
                }}
                """.formatted(good));
        JsonProfileStorage storage = new JsonProfileStorage(file);

        Map<UUID, PlayerProfile> loaded = storage.load();
        assertEquals(1, loaded.size());
        assertEquals(42L, loaded.get(good).chips());
    }
}
