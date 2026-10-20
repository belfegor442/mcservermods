package com.gambling.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player rate limit between casino rounds, protecting the server from
 * command-spam macros (each round writes the transaction log and session file).
 * A cooldown of zero disables the limiter.
 */
public final class RoundLimiter {
    private final long cooldownMillis;
    private final Map<UUID, Long> lastRoundAt = new HashMap<>();

    public RoundLimiter(long cooldownMillis) {
        this.cooldownMillis = Math.max(0L, cooldownMillis);
    }

    /**
     * Returns 0 when a new round may start, otherwise the remaining cooldown
     * in milliseconds.
     */
    public synchronized long check(UUID playerId, long nowMillis) {
        if (cooldownMillis <= 0) {
            return 0;
        }
        Long last = lastRoundAt.get(playerId);
        if (last == null) {
            return 0;
        }
        long elapsed = nowMillis - last;
        return elapsed >= cooldownMillis ? 0 : cooldownMillis - elapsed;
    }

    public synchronized void record(UUID playerId, long nowMillis) {
        if (cooldownMillis <= 0) {
            return;
        }
        lastRoundAt.put(playerId, nowMillis);
    }

    public synchronized void clear(UUID playerId) {
        lastRoundAt.remove(playerId);
    }
}
