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

    @Test
    void recordsNeutralizeControlCharsQuotesAndFormulas(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("transactions.csv");
        TransactionLog log = new TransactionLog(file, TransactionLog.DEFAULT_MAX_BYTES);
        log.record(UUID.randomUUID(), "=cmd|'/C calc'!A1", "+TRIM(B1)", 1, 1);
        log.record(UUID.randomUUID(), "a\u0000b\"c", "BET", 2, 2);
        String content = Files.readString(file);

        assertTrue(content.contains("_cmd|'/C calc'!A1"),
                "a leading formula marker must be neutralized: " + content);
        assertTrue(content.contains("_TRIM(B1)"),
                "a leading + in any field must be neutralized: " + content);
        assertTrue(content.contains("a_b_c"),
                "control characters and double quotes must be replaced: " + content);
        assertTrue(!content.contains("\""), "double quotes must not break the CSV");
        assertTrue(!content.contains("\u0000"), "control characters must be stripped");
        assertEquals(2, content.lines().count(), "each record must stay on one line");
    }
}
