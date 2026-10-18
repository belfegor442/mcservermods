package com.gambling.economy;

import com.gambling.util.Validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class EconomyManager {
    public static final long DEFAULT_CHIPS = 1_000L;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final JsonProfileStorage storage;
    private final TransactionLog transactionLog;
    private final List<BiConsumer<UUID, PlayerProfile>> changeListeners = new ArrayList<>();
    private volatile boolean dirty;

    public EconomyManager(JsonProfileStorage storage, TransactionLog transactionLog) {
        this.storage = storage;
        this.transactionLog = transactionLog;
    }

    public void loadFromDisk() {
        profiles.clear();
        profiles.putAll(storage.load());
        dirty = false;
    }

    public void addChangeListener(BiConsumer<UUID, PlayerProfile> listener) {
        changeListeners.add(listener);
    }

    public PlayerProfile getProfile(UUID uuid) {
        PlayerProfile p = profiles.get(uuid);
        return p == null ? null : p.copy();
    }

    public synchronized PlayerProfile getOrCreate(UUID uuid, String name) {
        PlayerProfile existing = profiles.get(uuid);
        if (existing == null) {
            existing = new PlayerProfile(uuid, name, DEFAULT_CHIPS, 0L, 0L);
            profiles.put(uuid, existing);
            dirty = true;
            transactionLog.record(uuid, name, "JOIN_GRANT", DEFAULT_CHIPS, existing.chips());
            fireChanged(uuid, existing);
            return existing.copy();
        }
        if (name != null && !name.equals(existing.name())) {
            existing.setName(name);
            dirty = true;
            fireChanged(uuid, existing);
        }
        return existing.copy();
    }

    public synchronized boolean setChips(UUID uuid, String playerName, long amount, String reason) {
        if (!Validation.isValidAdminAmount(amount, true)) {
            return false;
        }
        PlayerProfile p = profiles.get(uuid);
        if (p == null) {
            return false;
        }
        p.setChips(amount);
        dirty = true;
        transactionLog.record(uuid, playerName != null ? playerName : p.name(), reason, amount, p.chips());
        fireChanged(uuid, p);
        return true;
    }

    public synchronized boolean addChips(UUID uuid, String playerName, long amount, String reason) {
        if (!Validation.isValidAdminAmount(amount, false)) {
            return false;
        }
        PlayerProfile p = profiles.get(uuid);
        if (p == null || amount > Validation.MAX_ADMIN_AMOUNT - p.chips()) {
            return false;
        }
        p.addChips(amount);
        dirty = true;
        transactionLog.record(uuid, playerName != null ? playerName : p.name(), reason, amount, p.chips());
        fireChanged(uuid, p);
        return true;
    }

    public synchronized boolean removeChips(UUID uuid, String playerName, long amount, String reason) {
        if (!Validation.isValidAdminAmount(amount, false)) {
            return false;
        }
        PlayerProfile p = profiles.get(uuid);
        if (p == null) {
            return false;
        }
        if (!p.subtractChips(amount)) {
            return false;
        }
        dirty = true;
        transactionLog.record(uuid, playerName != null ? playerName : p.name(), reason, -amount, p.chips());
        fireChanged(uuid, p);
        return true;
    }

    public synchronized void addWager(UUID uuid, long wagered) {
        PlayerProfile p = profiles.get(uuid);
        if (p == null || wagered <= 0) {
            return;
        }
        p.addWager(wagered);
        dirty = true;
        fireChanged(uuid, p);
    }

    public boolean isDirty() {
        return dirty;
    }

    public synchronized boolean save() {
        boolean saved = storage.save(Map.copyOf(profiles));
        if (saved) {
            dirty = false;
        }
        return saved;
    }

    private void fireChanged(UUID uuid, PlayerProfile profile) {
        for (BiConsumer<UUID, PlayerProfile> listener : changeListeners) {
            try {
                listener.accept(uuid, profile.copy());
            } catch (Exception ignored) {
            }
        }
    }
}
