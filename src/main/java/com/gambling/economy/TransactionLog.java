package com.gambling.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public final class TransactionLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-transactions");

    private final Path file;

    public TransactionLog(Path file) {
        this.file = file;
    }

    public synchronized void record(UUID playerId, String playerName, String operation, long amount, long balanceAfter) {
        String line = Instant.now() + "," + playerId + "," + sanitize(playerName) + "," + sanitize(operation)
                + "," + amount + "," + balanceAfter + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to append transaction log {}: {}", file, e.toString());
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', '_').replace('\n', '_').replace('\r', '_');
    }
}
