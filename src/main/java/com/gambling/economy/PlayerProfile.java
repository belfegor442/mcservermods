package com.gambling.economy;

import java.util.UUID;

public final class PlayerProfile {
    private final UUID uuid;
    private String name;
    private long chips;
    private long xp;
    private long totalWagered;

    public PlayerProfile(UUID uuid, String name, long chips, long xp, long totalWagered) {
        this.uuid = uuid;
        this.name = name;
        this.chips = Math.max(0L, chips);
        this.xp = Math.max(0L, xp);
        this.totalWagered = Math.max(0L, totalWagered);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long chips() {
        return chips;
    }

    public long xp() {
        return xp;
    }

    public long totalWagered() {
        return totalWagered;
    }

    public int level() {
        return Levels.levelForXp(xp);
    }

    void setName(String name) {
        this.name = name;
    }

    void setChips(long chips) {
        this.chips = Math.max(0L, chips);
    }

    void addChips(long amount) {
        long sum = com.gambling.util.Validation.safeAdd(this.chips, amount);
        this.chips = Math.min(sum, com.gambling.util.Validation.MAX_ADMIN_AMOUNT);
    }

    boolean subtractChips(long amount) {
        if (amount < 0 || this.chips < amount) {
            return false;
        }
        this.chips -= amount;
        return true;
    }

    void addWager(long wagered) {
        this.totalWagered = com.gambling.util.Validation.safeAdd(this.totalWagered, wagered);
        this.xp = com.gambling.util.Validation.safeAdd(this.xp, wagered);
    }

    public PlayerProfile copy() {
        return new PlayerProfile(uuid, name, chips, xp, totalWagered);
    }
}
