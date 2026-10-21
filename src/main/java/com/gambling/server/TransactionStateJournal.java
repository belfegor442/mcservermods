package com.gambling.server;

import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.game.JsonSessionStorage;
import com.gambling.game.PlayerState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
 * Write-ahead snapshot for changes that touch both profiles.json and
 * sessions.json. The journal remains authoritative until both files have been
 * written successfully, so a crash between those writes can be replayed safely.
 */
public final class TransactionStateJournal {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-state-journal");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public record Snapshot(Map<UUID, PlayerProfile> profiles, Map<UUID, GameSession> sessions) {
        public Snapshot {
            profiles = Map.copyOf(profiles);
            sessions = Map.copyOf(sessions);
        }
    }

    public TransactionStateJournal(Path file) {
        this.file = file;
    }

    public synchronized boolean save(Map<UUID, PlayerProfile> profiles,
                                     Map<UUID, GameSession> sessions) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);

            JsonObject profileEntries = new JsonObject();
            for (Map.Entry<UUID, PlayerProfile> entry : profiles.entrySet()) {
                PlayerProfile profile = entry.getValue();
                JsonObject value = new JsonObject();
                value.addProperty("name", profile.name());
                value.addProperty("chips", profile.chips());
                value.addProperty("xp", profile.xp());
                value.addProperty("totalWagered", profile.totalWagered());
                profileEntries.add(entry.getKey().toString(), value);
            }
            root.add("profiles", profileEntries);

            JsonObject sessionEntries = new JsonObject();
            for (Map.Entry<UUID, GameSession> entry : sessions.entrySet()) {
                GameSession session = entry.getValue();
                JsonObject value = new JsonObject();
                value.addProperty("game", session.gameType());
                value.addProperty("bet", session.bet());
                value.addProperty("state", session.state().id());
                value.addProperty("result", session.result());
                sessionEntries.add(entry.getKey().toString(), value);
            }
            root.add("sessions", sessionEntries);

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not write transaction state journal {}: {}", file, e.toString());
            return false;
        }
    }

    /**
     * Replays a pending snapshot before the economy or sessions are loaded.
     * Returning false is a hard startup blocker: loading only one side of a
     * pending transaction could otherwise create or destroy chips.
     */
    public synchronized boolean recover(JsonProfileStorage profiles, JsonSessionStorage sessions) {
        if (!Files.exists(file)) {
            return true;
        }

        Snapshot snapshot = load();
        if (snapshot == null) {
            LOGGER.error("Transaction journal {} is unreadable; refusing to start with potentially inconsistent balances.",
                    file);
            return false;
        }

        boolean profilesSaved = profiles.save(snapshot.profiles());
        boolean sessionsSaved = sessions.save(snapshot.sessions());
        if (!profilesSaved || !sessionsSaved) {
            LOGGER.error("Could not replay transaction journal {}; it will be retried next startup.", file);
            return false;
        }
        return clear();
    }

    public synchronized boolean clear() {
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not clear transaction state journal {}: {}", file, e.toString());
            return false;
        }
    }

    private Snapshot load() {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1) {
                throw new IOException("unsupported or missing journal version");
            }

            JsonObject profileEntries = root.getAsJsonObject("profiles");
            JsonObject sessionEntries = root.getAsJsonObject("sessions");
            if (profileEntries == null || sessionEntries == null) {
                throw new IOException("missing profiles or sessions object");
            }

            Map<UUID, PlayerProfile> profiles = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : profileEntries.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                String name = value.has("name") && !value.get("name").isJsonNull()
                        ? value.get("name").getAsString() : "Unknown";
                long chips = value.get("chips").getAsLong();
                long xp = value.has("xp") ? value.get("xp").getAsLong() : 0L;
                long wagered = value.has("totalWagered")
                        ? value.get("totalWagered").getAsLong() : 0L;
                PlayerProfile profile = new PlayerProfile(uuid, name, chips, xp, wagered);
                if (profile.chips() != chips) {
                    LOGGER.warn("Journal profile '{}' held {} CHIPS; clamped to the {} cap.",
                            name, chips, com.gambling.util.Validation.MAX_ADMIN_AMOUNT);
                }
                profiles.put(uuid, profile);
            }

            Map<UUID, GameSession> sessions = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : sessionEntries.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                String game = value.get("game").getAsString();
                long bet = value.get("bet").getAsLong();
                PlayerState state = PlayerState.fromId(value.get("state").getAsString());
                String result = value.has("result") && !value.get("result").isJsonNull()
                        ? value.get("result").getAsString() : "";
                sessions.put(uuid, new GameSession(uuid, game, bet, state, result));
            }

            return new Snapshot(profiles, sessions);
        } catch (Exception e) {
            LOGGER.error("Could not read transaction state journal {}: {}", file, e.toString());
            return null;
        }
    }
}
