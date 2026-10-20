package com.gambling.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crash-recovery storage for active game sessions. Sessions are transient by
 * nature, so a missing or corrupt file simply restores to "no sessions".
 */
public final class JsonSessionStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-session-storage");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public JsonSessionStorage(Path file) {
        this.file = file;
    }

    public Map<UUID, GameSession> load() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            Map<UUID, GameSession> sessions = new HashMap<>();
            if (players != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        JsonObject p = entry.getValue().getAsJsonObject();
                        String gameType = p.get("game").getAsString();
                        long bet = p.get("bet").getAsLong();
                        PlayerState state = PlayerState.fromId(p.get("state").getAsString());
                        String result = p.has("result") ? p.get("result").getAsString() : "";
                        sessions.put(uuid, new GameSession(uuid, gameType, bet, state, result));
                    } catch (Exception e) {
                        LOGGER.warn("Skipping corrupt session entry '{}': {}", entry.getKey(), e.toString());
                    }
                }
            }
            return sessions;
        } catch (Exception e) {
            LOGGER.error("Failed to load sessions from {}: {}", file, e.toString());
            return new HashMap<>();
        }
    }

    public synchronized boolean save(Map<UUID, GameSession> sessions) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject players = new JsonObject();
            for (Map.Entry<UUID, GameSession> entry : sessions.entrySet()) {
                GameSession s = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("game", s.gameType());
                obj.addProperty("bet", s.bet());
                obj.addProperty("state", s.state().id());
                obj.addProperty("result", s.result());
                players.add(entry.getKey().toString(), obj);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("players", players);

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save sessions to {}: {}", file, e.toString());
            return false;
        }
    }
}
