package com.gambling.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundLimiterTest {
    private final UUID player = UUID.randomUUID();

    @Test
    void firstRoundIsAlwaysAllowed() {
        RoundLimiter limiter = new RoundLimiter(1000);
        assertEquals(0, limiter.check(player, 10_000));
    }

    @Test
    void roundWithinCooldownReportsRemainingTime() {
        RoundLimiter limiter = new RoundLimiter(1000);
        limiter.record(player, 10_000);
        assertEquals(400, limiter.check(player, 10_600));
        assertTrue(limiter.check(player, 10_601) > 0);
    }

    @Test
    void roundAfterCooldownIsAllowed() {
        RoundLimiter limiter = new RoundLimiter(1000);
        limiter.record(player, 10_000);
        assertEquals(0, limiter.check(player, 11_000));
    }

    @Test
    void zeroCooldownDisablesTheLimiter() {
        RoundLimiter limiter = new RoundLimiter(0);
        limiter.record(player, 10_000);
        assertEquals(0, limiter.check(player, 10_001));
    }

    @Test
    void playersAreIndependentAndClearOnDisconnect() {
        RoundLimiter limiter = new RoundLimiter(1000);
        UUID other = UUID.randomUUID();
        limiter.record(player, 10_000);
        assertEquals(0, limiter.check(other, 10_100), "another player must not be rate-limited");
        limiter.clear(player);
        assertEquals(0, limiter.check(player, 10_200), "clearing removes the stored timestamp");
    }

    @Test
    void negativeCooldownIsTreatedAsDisabled() {
        RoundLimiter limiter = new RoundLimiter(-50);
        limiter.record(player, 10_000);
        assertEquals(0, limiter.check(player, 10_001));
    }
}
