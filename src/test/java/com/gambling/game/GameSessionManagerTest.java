package com.gambling.game;

import com.gambling.economy.EconomyManager;
import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.TransactionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
}
