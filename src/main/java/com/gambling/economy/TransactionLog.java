package com.gambling.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public final class TransactionLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-transactions");
    static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    private final Path file;
    private final long maxBytes;

    public TransactionLog(Path file) {
        this(file, DEFAULT_MAX_BYTES);
    }

    TransactionLog(Path file, long maxBytes) {
        this.file = file;
        this.maxBytes = maxBytes;
    }

    public synchronized void record(UUID playerId, String playerName, String operation, long amount, long balanceAfter) {
        String line = Instant.now() + "," + playerId + "," + sanitize(playerName) + "," + sanitize(operation)
                + "," + amount + "," + balanceAfter + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to append transaction log {}: {}", file, e.toString());
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.exists(file) && Files.size(file) >= maxBytes) {
            Path rotated = file.resolveSibling(file.getFileName() + ".old");
            Files.move(file, rotated, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Rotated transaction log {} -> {}.", file, rotated);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', '_').replace('\n', '_').replace('\r', '_');
    }
}
