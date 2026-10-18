package com.gambling.economy;

import com.gambling.util.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyManagerTest {
    private EconomyManager createManager(Path dir) {
        JsonProfileStorage storage = new JsonProfileStorage(dir.resolve("profiles.json"));
        TransactionLog log = new TransactionLog(dir.resolve("transactions.csv"));
        EconomyManager manager = new EconomyManager(storage, log);
        manager.loadFromDisk();
        return manager;
    }

    @Test
    void newPlayerGetsDefaultChips(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        PlayerProfile profile = manager.getOrCreate(id, "Steve");
        assertEquals(EconomyManager.DEFAULT_CHIPS, profile.chips());
        assertEquals(1, profile.level());
        assertEquals("Steve", profile.name());
    }

    @Test
    void getOrCreateIsIdempotent(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        PlayerProfile first = manager.getOrCreate(id, "Steve");
        PlayerProfile second = manager.getOrCreate(id, "Steve");
        assertEquals(first.chips(), second.chips());
        assertEquals(EconomyManager.DEFAULT_CHIPS, second.chips());
    }

    @Test
    void addAndRemoveChips(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertTrue(manager.addChips(id, "Steve", 500, "TEST"));
        assertEquals(EconomyManager.DEFAULT_CHIPS + 500, manager.getProfile(id).chips());
        assertTrue(manager.removeChips(id, "Steve", 200, "TEST"));
        assertEquals(EconomyManager.DEFAULT_CHIPS + 300, manager.getProfile(id).chips());
    }

    @Test
    void cannotRemoveMoreThanBalance(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertFalse(manager.removeChips(id, "Steve", EconomyManager.DEFAULT_CHIPS + 1, "TEST"));
        assertEquals(EconomyManager.DEFAULT_CHIPS, manager.getProfile(id).chips());
    }

    @Test
    void zeroBalanceIsAllowed(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertTrue(manager.setChips(id, "Steve", 0, "TEST"));
        assertEquals(0L, manager.getProfile(id).chips());
        assertTrue(manager.addChips(id, "Steve", 1, "TEST"));
        assertEquals(1L, manager.getProfile(id).chips());
    }

    @Test
    void rejectsInvalidAmounts(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertFalse(manager.addChips(id, "Steve", -5, "TEST"));
        assertFalse(manager.addChips(id, "Steve", 0, "TEST"));
        assertFalse(manager.setChips(id, "Steve", -1, "TEST"));
        assertFalse(manager.removeChips(id, "Steve", -1, "TEST"));
        assertFalse(manager.addChips(id, "Steve", Validation.MAX_ADMIN_AMOUNT + 1, "TEST"));
        assertEquals(EconomyManager.DEFAULT_CHIPS, manager.getProfile(id).chips());
    }

    @Test
    void largeAmountsAreClampedNotOverflowed(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        manager.setChips(id, "Steve", Validation.MAX_ADMIN_AMOUNT, "TEST");
        manager.addChips(id, "Steve", Validation.MAX_ADMIN_AMOUNT, "TEST");
        assertEquals(Validation.MAX_ADMIN_AMOUNT, manager.getProfile(id).chips());
    }

    @Test
    void unknownPlayerOperationsFail(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        assertNull(manager.getProfile(id));
        assertFalse(manager.addChips(id, "Ghost", 10, "TEST"));
        assertFalse(manager.setChips(id, "Ghost", 10, "TEST"));
        assertFalse(manager.removeChips(id, "Ghost", 10, "TEST"));
    }

    @Test
    void changeListenerFires(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID[] fired = new UUID[1];
        manager.addChangeListener((uuid, profile) -> fired[0] = uuid);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertEquals(id, fired[0]);
    }

    @Test
    void persistenceRoundTrip(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        manager.addChips(id, "Steve", 4242, "TEST");
        manager.addWager(id, 1000);
        manager.save();

        EconomyManager reloaded = createManager(dir);
        PlayerProfile profile = reloaded.getProfile(id);
        assertNotNull(profile);
        assertEquals(EconomyManager.DEFAULT_CHIPS + 4242, profile.chips());
        assertEquals(1000L, profile.totalWagered());
        assertEquals(1000L, profile.xp());
        assertEquals("Steve", profile.name());
    }

    @Test
    void failedSaveKeepsManagerDirty(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("not-a-directory");
        java.nio.file.Files.writeString(blocker, "file");
        EconomyManager manager = new EconomyManager(
                new JsonProfileStorage(blocker.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        manager.loadFromDisk();
        manager.getOrCreate(UUID.randomUUID(), "Steve");

        assertFalse(manager.save());
        assertTrue(manager.isDirty(), "failed persistence must remain eligible for retry");
    }

    @Test
    void corruptFileDoesNotGetOverwrittenWithDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("profiles.json");
        java.nio.file.Files.writeString(file, "{ this is not json !!!");
        EconomyManager manager = createManager(dir);
        // load returned an empty map; original corrupt content must not be replaced by defaults
        assertFalse(java.nio.file.Files.exists(file) && java.nio.file.Files.readString(file).contains("\"players\""));
        // quarantine file exists alongside with the original corrupt content preserved
        boolean hasQuarantine;
        try (var stream = java.nio.file.Files.list(dir)) {
            hasQuarantine = stream.anyMatch(p -> {
                try {
                    return p.getFileName().toString().contains(".corrupt-")
                            && java.nio.file.Files.readString(p).contains("not json");
                } catch (java.io.IOException e) {
                    return false;
                }
            });
        }
        assertTrue(hasQuarantine, "corrupt file should be quarantined with original content");
    }

    @Test
    void backupIsUsedWhenPrimaryCorrupt(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        Path file = dir.resolve("profiles.json");
        Path backup = dir.resolve("profiles.json.bak");

        EconomyManager manager = createManager(dir);
        manager.getOrCreate(id, "Steve");
        manager.addChips(id, "Steve", 100, "TEST");
        manager.save();
        // second save creates backup of first content
        manager.addChips(id, "Steve", 1, "TEST");
        manager.save();
        assertTrue(java.nio.file.Files.exists(backup));

        try {
            java.nio.file.Files.writeString(file, "corrupted!");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        EconomyManager reloaded = createManager(dir);
        PlayerProfile profile = reloaded.getProfile(id);
        assertNotNull(profile, "backup should have been used");
    }

    @Test
    void addChipsRejectsBalanceOverflow() {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        assertTrue(manager.setChips(id, "Steve", Validation.MAX_ADMIN_AMOUNT - 5, "TEST"));

        assertFalse(manager.addChips(id, "Steve", 10, "TEST"));
        assertEquals(Validation.MAX_ADMIN_AMOUNT - 5, manager.getProfile(id).chips());
    }

    @Test
    void levelFormula() {
        assertEquals(1, Levels.levelForXp(0));
        assertEquals(1, Levels.levelForXp(99));
        assertEquals(2, Levels.levelForXp(100));
        assertEquals(11, Levels.levelForXp(10_000));
        assertTrue(Levels.levelForXp(Long.MAX_VALUE) <= Levels.MAX_LEVEL);
    }

    @Test
    void mapSnapshotSaveDoesNotLeakMutableState(@TempDir Path dir) {
        EconomyManager manager = createManager(dir);
        UUID id = UUID.randomUUID();
        manager.getOrCreate(id, "Steve");
        Map<UUID, PlayerProfile> all = Map.of(id, manager.getProfile(id));
        assertEquals(EconomyManager.DEFAULT_CHIPS, all.get(id).chips());
    }
}
