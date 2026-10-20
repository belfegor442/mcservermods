package com.gambling.game;

import com.gambling.economy.EconomyManager;
import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.TransactionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionManagerTest {
    @TempDir
    Path dir;

    private EconomyManager economy;
    private GameSessionManager sessions;
    private UUID player;

    @BeforeEach
    void setUp() {
        economy = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        economy.loadFromDisk();
        sessions = new GameSessionManager(economy);
        player = UUID.randomUUID();
        economy.getOrCreate(player, "Steve");
    }

    @Test
    void startSessionTransitionsToInGame() {
        GameSessionManager.Result result = sessions.startSession(player, "coinflip", 100);
        assertTrue(result.ok());
        GameSession session = sessions.getSession(player);
        assertNotNull(session);
        assertEquals(PlayerState.IN_GAME, session.state());
        assertEquals("coinflip", session.gameType());
        assertEquals(100L, session.bet());
    }

    @Test
    void cannotStartTwoSessions() {
        assertTrue(sessions.startSession(player, "coinflip", 100).ok());
        GameSessionManager.Result second = sessions.startSession(player, "slots", 50);
        assertFalse(second.ok());
        assertEquals("key.gambling.error.already_in_game", second.messageKey());
    }

    @Test
    void invalidGameTypeRejected() {
        assertFalse(sessions.startSession(player, "bad type!", 100).ok());
        assertFalse(sessions.startSession(player, "", 100).ok());
        assertFalse(sessions.startSession(player, "x".repeat(64), 100).ok());
        assertFalse(sessions.startSession(player, null, 100).ok());
        assertNull(sessions.getSession(player));
    }

    @Test
    void invalidBetRejected() {
        assertFalse(sessions.startSession(player, "coinflip", 0).ok());
        assertFalse(sessions.startSession(player, "coinflip", -10).ok());
        assertFalse(sessions.startSession(player, "coinflip", com.gambling.util.Validation.MAX_BET + 1).ok());
    }

    @Test
    void cannotStartWithoutFunds() {
        economy.setChips(player, "Steve", 10, "TEST");
        GameSessionManager.Result result = sessions.startSession(player, "coinflip", 100);
        assertFalse(result.ok());
        assertEquals("key.gambling.error.insufficient_funds", result.messageKey());
    }

    @Test
    void commitBetDeductsAndMovesToBetting() {
        sessions.startSession(player, "coinflip", 100);
        long before = economy.getProfile(player).chips();
        GameSessionManager.Result result = sessions.commitBet(player, 100);
        assertTrue(result.ok());
        assertEquals(before - 100, economy.getProfile(player).chips());
        assertEquals(PlayerState.BETTING, sessions.getSession(player).state());
        assertEquals(0L, economy.getProfile(player).totalWagered(), "an unresolved bet must not count as completed wagering");
    }

    @Test
    void commitBetWithMismatchedAmountFails() {
        sessions.startSession(player, "coinflip", 100);
        GameSessionManager.Result result = sessions.commitBet(player, 999);
        assertFalse(result.ok());
        assertEquals("key.gambling.error.bet_mismatch", result.messageKey());
        assertEquals(PlayerState.IN_GAME, sessions.getSession(player).state());
    }

    @Test
    void doubleCommitFails() {
        sessions.startSession(player, "coinflip", 100);
        assertTrue(sessions.commitBet(player, 100).ok());
        GameSessionManager.Result second = sessions.commitBet(player, 100);
        assertFalse(second.ok());
        assertEquals("key.gambling.error.invalid_state", second.messageKey());
    }

    @Test
    void resolveWinPaysDouble() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        long afterBet = economy.getProfile(player).chips();
        GameSessionManager.Result result = sessions.resolveRound(player, true, "heads");
        assertTrue(result.ok());
        assertEquals(afterBet + 200, economy.getProfile(player).chips());
        assertEquals(PlayerState.FINALIZING, sessions.getSession(player).state());
        assertEquals("heads", sessions.getSession(player).result());
        assertEquals(100L, economy.getProfile(player).totalWagered());
    }

    @Test
    void failedPayoutRefundsStakeAndEndsSession() {
        economy.setChips(player, "Steve", com.gambling.util.Validation.MAX_ADMIN_AMOUNT, "TEST");
        assertTrue(sessions.startSession(player, "coinflip", 100).ok());
        assertTrue(sessions.commitBet(player, 100).ok());
        long afterStake = economy.getProfile(player).chips();

        GameSessionManager.Result result = sessions.resolveRound(player, true, "heads");

        assertFalse(result.ok());
        assertEquals("key.gambling.error.payout_refunded", result.messageKey());
        assertEquals(com.gambling.util.Validation.MAX_ADMIN_AMOUNT, economy.getProfile(player).chips(),
                "the stake must be refunded when the payout exceeds the cap");
        assertNull(sessions.getSession(player), "the session must not leave the player stuck in BETTING");
        assertEquals(0L, economy.getProfile(player).totalWagered());
        assertTrue(afterStake < economy.getProfile(player).chips());
    }

    @Test
    void rouletteGreenPayoutReturnsThirtySixTimesStake() {
        assertTrue(sessions.startSession(player, "roulette", 10).ok());
        assertTrue(sessions.commitBet(player, 10).ok());
        long afterBet = economy.getProfile(player).chips();

        GameSessionManager.Result result = sessions.resolveRound(player, true, "0 GREEN", 36);

        assertTrue(result.ok());
        assertEquals(afterBet + 360, economy.getProfile(player).chips());
        assertEquals(PlayerState.FINALIZING, sessions.getSession(player).state());
    }

    @Test
    void slotsJackpotPayoutReturnsTwentyTimesStake() {
        assertTrue(sessions.startSession(player, "slots", 10).ok());
        assertTrue(sessions.commitBet(player, 10).ok());
        long afterBet = economy.getProfile(player).chips();

        GameSessionManager.Result result = sessions.resolveRound(player, true, "SEVEN | SEVEN | SEVEN", 20);

        assertTrue(result.ok());
        assertEquals(afterBet + 200, economy.getProfile(player).chips());
    }

    @Test
    void invalidPayoutMultiplierCannotResolveRound() {
        assertTrue(sessions.startSession(player, "roulette", 10).ok());
        assertTrue(sessions.commitBet(player, 10).ok());

        GameSessionManager.Result result = sessions.resolveRound(player, true, "0 GREEN", 37);

        assertFalse(result.ok());
        assertEquals(PlayerState.BETTING, sessions.getSession(player).state());
    }

    @Test
    void resolveLosePaysNothing() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        long afterBet = economy.getProfile(player).chips();
        GameSessionManager.Result result = sessions.resolveRound(player, false, null);
        assertTrue(result.ok());
        assertEquals(afterBet, economy.getProfile(player).chips());
        assertEquals("LOSS", sessions.getSession(player).result());
    }

    @Test
    void resolveWithoutBetFails() {
        sessions.startSession(player, "coinflip", 100);
        GameSessionManager.Result result = sessions.resolveRound(player, true, null);
        assertFalse(result.ok());
    }

    @Test
    void finishClearsSession() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        sessions.resolveRound(player, true, null);
        assertTrue(sessions.finishSession(player).ok());
        assertNull(sessions.getSession(player));
    }

    @Test
    void finishBeforeResolveFails() {
        sessions.startSession(player, "coinflip", 100);
        assertFalse(sessions.finishSession(player).ok());
    }

    @Test
    void cancelOnlyBeforeBet() {
        sessions.startSession(player, "coinflip", 100);
        assertTrue(sessions.cancelSession(player).ok());
        assertNull(sessions.getSession(player));

        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        assertFalse(sessions.cancelSession(player).ok());
    }

    @Test
    void resolveResultStaysVisibleUntilNextStart() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        sessions.resolveRound(player, true, "heads");

        GameSession lingering = sessions.getSession(player);
        assertNotNull(lingering, "a resolved round must linger so the HUD can show its result");
        assertEquals(PlayerState.FINALIZING, lingering.state());
        assertEquals("heads", lingering.result());
    }

    @Test
    void startSessionReplacesResolvedFinalizingSession() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        sessions.resolveRound(player, true, "heads");

        GameSessionManager.Result started = sessions.startSession(player, "slots", 50);

        assertTrue(started.ok());
        GameSession session = sessions.getSession(player);
        assertEquals("slots", session.gameType());
        assertEquals(PlayerState.IN_GAME, session.state());
        assertEquals("", session.result());
    }

    @Test
    void startSessionRejectsWhileBettingUnresolved() {
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);

        GameSessionManager.Result started = sessions.startSession(player, "slots", 50);

        assertFalse(started.ok());
        assertEquals("key.gambling.error.already_in_game", started.messageKey());
        assertEquals(PlayerState.BETTING, sessions.getSession(player).state());
    }

    @Test
    void gameSessionNormalizesNullFields() {
        GameSession session = new GameSession(player, null, 10, PlayerState.IN_GAME, null);
        assertEquals("", session.gameType());
        assertEquals("", session.result());
    }

    @Test
    void restoreRefundsUnresolvedBettingStake() {
        long before = economy.getProfile(player).chips();
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);

        // Simulate a crash: a fresh manager restores the persisted BETTING session.
        GameSessionManager restored = new GameSessionManager(economy);
        restored.restoreSessions(Map.of(player, sessions.getSession(player)));

        assertNull(restored.getSession(player), "unresolved betting sessions must be dropped");
        assertEquals(before, economy.getProfile(player).chips(), "the stake must be refunded");
        assertEquals(0L, economy.getProfile(player).totalWagered());
    }

    @Test
    void restoreKeepsInProgressAndResolvedSessions() {
        sessions.startSession(player, "coinflip", 100);
        GameSession inGame = sessions.getSession(player);

        UUID other = UUID.randomUUID();
        GameSession resolved = new GameSession(other, "roulette", 50, PlayerState.FINALIZING, "0 GREEN — WIN");
        GameSessionManager restored = new GameSessionManager(economy);
        restored.restoreSessions(Map.of(player, inGame, other, resolved));

        assertEquals(PlayerState.IN_GAME, restored.getSession(player).state());
        assertEquals(PlayerState.FINALIZING, restored.getSession(other).state());
        assertEquals(2, restored.snapshot().size());
    }

    @Test
    void restoreDropsEntriesWithInvalidGameType() {
        UUID corrupt = UUID.randomUUID();
        GameSession bad = new GameSession(corrupt, "not a valid type!", 100, PlayerState.IN_GAME, "");
        GameSessionManager restored = new GameSessionManager(economy);
        restored.restoreSessions(Map.of(corrupt, bad));
        assertNull(restored.getSession(corrupt));
    }

    @Test
    void cancelDoesNotDeduct() {
        long before = economy.getProfile(player).chips();
        sessions.startSession(player, "coinflip", 100);
        sessions.cancelSession(player);
        assertEquals(before, economy.getProfile(player).chips());
    }

    @Test
    void disconnectClearsSession() {
        sessions.startSession(player, "coinflip", 100);
        sessions.handleDisconnect(player);
        assertNull(sessions.getSession(player));
    }

    @Test
    void disconnectDuringUnresolvedBetRefundsStake() {
        long before = economy.getProfile(player).chips();
        sessions.startSession(player, "coinflip", 100);
        assertTrue(sessions.commitBet(player, 100).ok());
        assertEquals(before - 100, economy.getProfile(player).chips());

        sessions.handleDisconnect(player);

        assertEquals(before, economy.getProfile(player).chips());
        assertNull(sessions.getSession(player));
        assertEquals(0L, economy.getProfile(player).totalWagered());
    }

    @Test
    void betWithoutSessionFails() {
        GameSessionManager.Result result = sessions.commitBet(player, 100);
        assertFalse(result.ok());
        assertEquals("key.gambling.error.no_session", result.messageKey());
    }

    @Test
    void changeListenerTracksLifecycle() {
        int[] updates = new int[1];
        sessions.addChangeListener((uuid, session) -> updates[0]++);
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        sessions.resolveRound(player, true, null);
        sessions.finishSession(player);
        assertEquals(4, updates[0]);
    }

    @Test
    void playerNeverGoesNegativeThroughGameFlow() {
        economy.setChips(player, "Steve", 150, "TEST");
        sessions.startSession(player, "coinflip", 100);
        sessions.commitBet(player, 100);
        sessions.resolveRound(player, false, null);
        sessions.finishSession(player);
        assertEquals(50L, economy.getProfile(player).chips());
    }

    @Test
    void restoreFromRefundsStaleBettingStakeExactlyOnce() {
        JsonSessionStorage storage = new JsonSessionStorage(dir.resolve("sessions.json"));
        UUID victim = UUID.randomUUID();
        economy.getOrCreate(victim, "Victim");
        // Crash scenario: sessions.json reached disk with BETTING but the
        // economy never autosaved, so the on-disk balance still holds the stake.
        assertTrue(storage.save(Map.of(victim,
                new GameSession(victim, "slots", 250, PlayerState.BETTING, ""))));

        sessions.restoreFrom(storage);

        assertEquals(EconomyManager.DEFAULT_CHIPS + 250,
                economy.getProfile(victim).chips(), "the stake must be refunded on recovery");
        assertNull(sessions.getSession(victim));
        assertTrue(storage.load().isEmpty(),
                "recovered sessions must be persisted immediately so a second crash cannot re-refund");

        EconomyManager reloaded = new EconomyManager(
                new JsonProfileStorage(dir.resolve("profiles.json")),
                new TransactionLog(dir.resolve("transactions.csv")));
        reloaded.loadFromDisk();
        assertEquals(EconomyManager.DEFAULT_CHIPS + 250, reloaded.getProfile(victim).chips(),
                "the refunded balance must be on disk right after recovery");

        sessions.restoreFrom(storage);
        assertEquals(EconomyManager.DEFAULT_CHIPS + 250,
                economy.getProfile(victim).chips(), "recovery must be idempotent");
    }

    @Test
    void restoreDropsSessionWithUnusableState() {
        JsonSessionStorage storage = new JsonSessionStorage(dir.resolve("sessions.json"));
        UUID ghost = UUID.randomUUID();
        economy.getOrCreate(ghost, "Ghost");
        assertTrue(storage.save(Map.of(ghost,
                new GameSession(ghost, "slots", 100, PlayerState.AVAILABLE, ""))));

        sessions.restoreFrom(storage);

        assertNull(sessions.getSession(ghost),
                "an AVAILABLE session cannot be cleared by any command and must not be restored");
        assertEquals(EconomyManager.DEFAULT_CHIPS, economy.getProfile(ghost).chips(),
                "a non-BETTING entry must never be refunded");
    }
}
