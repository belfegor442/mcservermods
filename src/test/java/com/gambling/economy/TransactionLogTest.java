package com.gambling.economy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionLogTest {
    @Test
    void rotatesWhenTheFileExceedsTheLimit(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("transactions.csv");
        TransactionLog log = new TransactionLog(file, 80);

        log.record(UUID.randomUUID(), "Steve", "JOIN_GRANT", 1000, 1000);
        assertTrue(Files.size(file) >= 80, "first record should exceed the tiny test limit");

        log.record(UUID.randomUUID(), "Steve", "BET", -100, 900);

        Path rotated = dir.resolve("transactions.csv.old");
        assertTrue(Files.exists(rotated), "the oversized log must be rotated before the next append");
        String current = Files.readString(file);
        assertEquals(1, current.lines().count(), "only the newest record should remain in the active log");
        assertTrue(current.contains("BET"));
        assertTrue(Files.readString(rotated).contains("JOIN_GRANT"));
    }

    @Test
    void recordsContainSanitizedCsvFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("transactions.csv");
        TransactionLog log = new TransactionLog(file, TransactionLog.DEFAULT_MAX_BYTES);
        log.record(UUID.randomUUID(), "evil,name\nwith newline", "ADMIN_SET", 5, 5);
        String content = Files.readString(file);
        assertTrue(content.contains("evil_name_with newline"),
                "commas and newlines in names must not break the CSV");
        assertEquals(1, content.lines().count(), "a record must stay on a single CSV line");
    }
}
