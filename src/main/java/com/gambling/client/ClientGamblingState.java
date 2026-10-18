package com.gambling.client;

import com.gambling.game.PlayerState;

public final class ClientGamblingState {
    private static final ClientGamblingState INSTANCE = new ClientGamblingState();

    public static ClientGamblingState get() {
        return INSTANCE;
    }

    private volatile boolean receivedProfile;
    private volatile String playerName = "";
    private volatile long chips;
    private volatile int level = 1;
    private volatile long xp;
    private volatile long totalWagered;

    private volatile boolean hasSession;
    private volatile String gameType = "";
    private volatile long bet;
    private volatile PlayerState playerState = PlayerState.AVAILABLE;
    private volatile String result = "";

    private ClientGamblingState() {
    }

    public void updateProfile(String playerName, long chips, int level, long xp, long totalWagered) {
        this.playerName = sanitize(playerName, 32);
        this.chips = Math.max(0L, chips);
        this.level = Math.max(1, level);
        this.xp = Math.max(0L, xp);
        this.totalWagered = Math.max(0L, totalWagered);
        this.receivedProfile = true;
    }

    public void updateGame(boolean hasSession, String gameType, long bet, String stateId, String result) {
        this.hasSession = hasSession;
        this.gameType = sanitize(gameType, 32);
        this.bet = Math.max(0L, bet);
        this.playerState = hasSession ? PlayerState.fromId(stateId) : PlayerState.AVAILABLE;
        this.result = sanitize(result, 64);
    }

    public void clear() {
        receivedProfile = false;
        playerName = "";
        chips = 0;
        level = 1;
        xp = 0;
        totalWagered = 0;
        hasSession = false;
        gameType = "";
        bet = 0;
        playerState = PlayerState.AVAILABLE;
        result = "";
    }

    public boolean hasReceivedProfile() {
        return receivedProfile;
    }

    public String playerName() {
        return playerName;
    }

    public long chips() {
        return chips;
    }

    public int level() {
        return level;
    }

    public long xp() {
        return xp;
    }

    public long totalWagered() {
        return totalWagered;
    }

    public boolean hasSession() {
        return hasSession;
    }

    public String gameType() {
        return gameType;
    }

    public long bet() {
        return bet;
    }

    public PlayerState playerState() {
        return playerState;
    }

    public String result() {
        return result;
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && sb.length() < maxLength; i++) {
            char c = value.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
