package com.gambling.game;

import com.gambling.economy.EconomyManager;
import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.TransactionLog;
import com.gambling.util.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionFlowPropertyTest {
    private static final String[] GAME_TYPES = {"slots", "roulette", "coinflip"};

    @Test
    void randomSessionFlowsPreserveEconomyInvariants(@TempDir Path dir) {
        EconomyManager economy = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        economy.loadFromDisk();
        GameSessionManager sessions = new GameSessionManager(economy);
        Random random = new Random(1234567L);

        UUID player = UUID.randomUUID();
        economy.getOrCreate(player, "Steve");
        long startingBalance = economy.getProfile(player).chips();
        assertTrue(startingBalance > 0);

        int rounds = 0;
        int cancelled = 0;
        int disconnected = 0;

        for (int i = 0; i < 2_000; i++) {
            GameSession session = sessions.getSession(player);
            long balanceBefore = economy.getProfile(player).chips();
            assertTrue(balanceBefore >= 0 && balanceBefore <= Validation.MAX_ADMIN_AMOUNT,
                    "balance out of bounds before step " + i + ": " + balanceBefore);

            if (session == null || session.state() == PlayerState.FINALIZING) {
                if (session != null && random.nextInt(100) < 25) {
                    long balance = economy.getProfile(player).chips();
                    GameSessionManager.Result finished = sessions.finishSession(player);
                    assertTrue(finished.ok(), "a resolved round must be finishable");
                    assertNull(sessions.getSession(player));
                    assertEquals(balance, economy.getProfile(player).chips(),
                            "finish must not move funds");
                } else if (random.nextInt(100) < 50) {
                    String game = GAME_TYPES[random.nextInt(GAME_TYPES.length)];
                    long bet = 1 + random.nextInt(1_000);
                    GameSessionManager.Result started = sessions.startSession(player, game, bet);
                    if (!started.ok()) {
                        assertEquals("key.gambling.error.insufficient_funds",
                                started.messageKey(),
                                "the only expected start failure is a too-high bet");
                        assertEquals(balanceBefore, economy.getProfile(player).chips(),
                                "a failed start must not move funds");
                        assertNull(sessions.getSession(player));
                        continue;
                    }
                    assertEquals(balanceBefore, economy.getProfile(player).chips(),
                            "start must not move funds");

                    if (random.nextInt(100) < 15) {
                        GameSessionManager.Result cancelledResult = sessions.cancelSession(player);
                        assertTrue(cancelledResult.ok(),
                                "cancelling a pending (uncommitted) bet must work");
                        assertNull(sessions.getSession(player));
                        assertEquals(balanceBefore, economy.getProfile(player).chips(),
                                "cancel before commit must not move funds");
                        cancelled++;
                        continue;
                    }

                    GameSessionManager.Result committed = sessions.commitBet(player, bet);
                    assertTrue(committed.ok(), "commit right after start must succeed");
                    assertEquals(balanceBefore - bet, economy.getProfile(player).chips(),
                            "stake must be deducted exactly on commit");
                    rounds++;

                    if (random.nextInt(100) < 80) {
                        boolean win = random.nextBoolean();
                        GameSessionManager.Result resolved =
                                sessions.resolveRound(player, win, win ? "WIN" : "LOSS");
                        assertTrue(resolved.ok(), "resolve after commit must succeed");
                        GameSession resolvedSession = sessions.getSession(player);
                        assertEquals(PlayerState.FINALIZING, resolvedSession.state(),
                                "a resolved round lingers in FINALIZING for the HUD");
                        assertEquals(balanceBefore - bet + (win ? bet * 2 : 0),
                                economy.getProfile(player).chips(),
                                "payout must be bet*2 on win and 0 on loss");
                    } else {
                        sessions.handleDisconnect(player);
                        assertNull(sessions.getSession(player));
                        assertEquals(balanceBefore, economy.getProfile(player).chips(),
                                "disconnect refund must restore the balance exactly");
                        disconnected++;
                    }
                } else {
                    long balance = economy.getProfile(player).chips();
                    if (random.nextBoolean()) {
                        assertTrue(economy.addChips(player, "Steve", 50, "PROPERTY"));
                    } else if (balance >= 50) {
                        assertTrue(economy.removeChips(player, "Steve", 50, "PROPERTY"));
                    } else {
                        assertFalse(economy.removeChips(player, "Steve", 50, "PROPERTY"),
                                "removing more than the balance must fail cleanly");
                    }
                }
            } else if (session.state() == PlayerState.IN_GAME) {
                GameSessionManager.Result cancelledResult = sessions.cancelSession(player);
                assertTrue(cancelledResult.ok(), "cancelling a pending (uncommitted) bet must work");
                assertNull(sessions.getSession(player));
                assertEquals(balanceBefore, economy.getProfile(player).chips(),
                        "cancel before commit must not move funds");
                cancelled++;
            } else {
                GameSessionManager.Result resolved = sessions.resolveRound(player, true, "WIN");
                assertTrue(resolved.ok(), "a committed bet must be resolvable");
                rounds++;
            }

            long balanceAfter = economy.getProfile(player).chips();
            assertTrue(balanceAfter >= 0 && balanceAfter <= Validation.MAX_ADMIN_AMOUNT,
                    "balance out of bounds after step " + i + ": " + balanceAfter);
        }

        GameSession leftover = sessions.getSession(player);
        if (leftover != null && leftover.state() == PlayerState.IN_GAME) {
            assertTrue(sessions.cancelSession(player).ok());
        }
        assertTrue(rounds > 0, "the seeded run must resolve rounds");
        assertTrue(cancelled > 0, "the seeded run must exercise cancellation");
        assertTrue(disconnected > 0, "the seeded run must exercise disconnect refunds");
        assertTrue(economy.getProfile(player).chips() >= 0);
    }

    @Test
    void oversizedBetIsRejectedWithoutMovingFunds(@TempDir Path dir) {
        EconomyManager economy = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        economy.loadFromDisk();
        GameSessionManager sessions = new GameSessionManager(economy);
        UUID player = UUID.randomUUID();
        economy.getOrCreate(player, "Steve");
        long before = economy.getProfile(player).chips();

        GameSessionManager.Result result =
                sessions.startSession(player, "slots", Validation.MAX_BET + 1);
        assertFalse(result.ok());
        assertEquals(before, economy.getProfile(player).chips());
        assertNull(sessions.getSession(player));
    }

    @Test
    void insufficientFundsRoundIsRejectedWithoutSideEffects(@TempDir Path dir) {
        EconomyManager economy = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        economy.loadFromDisk();
        GameSessionManager sessions = new GameSessionManager(economy);
        UUID player = UUID.randomUUID();
        economy.getOrCreate(player, "Steve");
        economy.removeChips(player, "Steve", EconomyManager.DEFAULT_CHIPS - 5, "BROKE");
        long before = economy.getProfile(player).chips();

        assertFalse(sessions.startSession(player, "roulette", before + 100).ok());
        assertFalse(sessions.startSession(player, "roulette", before + 1).ok());
        assertEquals(before, economy.getProfile(player).chips());
        assertNull(sessions.getSession(player));
    }

    @Test
    void payoutBeyondCapRefundsAndClosesSession(@TempDir Path dir) {
        EconomyManager economy = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        economy.loadFromDisk();
        GameSessionManager sessions = new GameSessionManager(economy);
        UUID player = UUID.randomUUID();
        economy.getOrCreate(player, "Steve");
        assertTrue(economy.addChips(player, "Steve",
                Validation.MAX_ADMIN_AMOUNT - EconomyManager.DEFAULT_CHIPS, "RICH"));
        long before = economy.getProfile(player).chips();

        assertTrue(sessions.startSession(player, "slots", 1_000).ok());
        assertTrue(sessions.commitBet(player, 1_000).ok());
        GameSessionManager.Result resolved = sessions.resolveRound(player, true, "JACKPOT");

        assertFalse(resolved.ok(), "payout beyond the cap must fail");
        assertEquals("key.gambling.error.payout_refunded", resolved.messageKey());
        assertEquals(before, economy.getProfile(player).chips(),
                "the stake must be refunded and no payout applied");
        assertNull(sessions.getSession(player),
                "a failed payout must close the session");
    }
}
