package com.gambling.economy;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class JsonProfileStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-storage");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Path backupFile;

    public JsonProfileStorage(Path file) {
        this.file = file;
        this.backupFile = file.resolveSibling(file.getFileName() + ".bak");
    }

    public Map<UUID, PlayerProfile> load() {
        Map<UUID, PlayerProfile> result = loadFromFile(file);
        if (result != null) {
            return result;
        }
        if (Files.exists(file)) {
            LOGGER.warn("Primary profile file {} is corrupt; trying backup.", file);
            Map<UUID, PlayerProfile> backup = loadFromFile(backupFile);
            if (backup != null) {
                LOGGER.info("Restored profiles from backup {}.", backupFile);
                return backup;
            }
            quarantineCorruptFile();
            return new HashMap<>();
        }
        Map<UUID, PlayerProfile> backup = loadFromFile(backupFile);
        if (backup != null) {
            LOGGER.info("Primary profile file missing; restored from backup {}.", backupFile);
            return backup;
        }
        return new HashMap<>();
    }

    private Map<UUID, PlayerProfile> loadFromFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            Map<UUID, PlayerProfile> profiles = new HashMap<>();
            if (players != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        JsonObject p = entry.getValue().getAsJsonObject();
                        String name = p.has("name") && !p.get("name").isJsonNull()
                                ? p.get("name").getAsString()
                                : "Unknown";
                        long chips = p.get("chips").getAsLong();
                        long xp = p.has("xp") ? p.get("xp").getAsLong() : 0L;
                        long wagered = p.has("totalWagered") ? p.get("totalWagered").getAsLong() : 0L;
                        PlayerProfile profile = new PlayerProfile(uuid, name, chips, xp, wagered);
                        if (profile.chips() != chips) {
                            LOGGER.warn("Profile '{}' held {} CHIPS; clamped to the {} cap.",
                                    name, chips, com.gambling.util.Validation.MAX_ADMIN_AMOUNT);
                        }
                        profiles.put(uuid, profile);
                    } catch (Exception e) {
                        LOGGER.warn("Skipping corrupt profile entry '{}': {}", entry.getKey(), e.toString());
                    }
                }
            }
            return profiles;
        } catch (Exception e) {
            LOGGER.error("Failed to load profiles from {}: {}", path, e.toString());
            return null;
        }
    }

    private void quarantineCorruptFile() {
        Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Quarantined corrupt profile file to {}.", quarantine);
        } catch (IOException e) {
            LOGGER.error("Could not quarantine corrupt profile file {}: {}", file, e.toString());
        }
    }

    public synchronized boolean save(Map<UUID, PlayerProfile> profiles) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject players = new JsonObject();
            for (Map.Entry<UUID, PlayerProfile> entry : profiles.entrySet()) {
                PlayerProfile p = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("name", p.name());
                obj.addProperty("chips", p.chips());
                obj.addProperty("xp", p.xp());
                obj.addProperty("totalWagered", p.totalWagered());
                players.add(entry.getKey().toString(), obj);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("players", players);

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            if (Files.exists(file)) {
                Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save profiles to {}: {}", file, e.toString());
            return false;
        }
    }
}
