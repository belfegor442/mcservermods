package com.gambling.client;

import com.gambling.game.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientGamblingStateTest {
    @Test
    void updateProfileSetsFields() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("Steve", 5000, 7, 10000, 4000);
        assertTrue(state.hasReceivedProfile());
        assertEquals("Steve", state.playerName());
        assertEquals(5000L, state.chips());
        assertEquals(7, state.level());
        assertEquals(10000L, state.xp());
        assertEquals(4000L, state.totalWagered());
    }

    @Test
    void negativeValuesAreClamped() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("Steve", -5, -3, -10, -20);
        assertEquals(0L, state.chips());
        assertEquals(1, state.level());
        assertEquals(0L, state.xp());
        assertEquals(0L, state.totalWagered());
    }

    @Test
    void controlCharactersAreStripped() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        String dirty = "St" + (char) 7 + "eve" + (char) 0 + "X";
        state.updateProfile(dirty, 0, 1, 0, 0);
        assertEquals("SteveX", state.playerName());
    }

    @Test
    void gameUpdateParsesState() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("Steve", 100, 1, 0, 0);
        state.updateGame(true, "coinflip", 50, "betting", "heads");
        assertTrue(state.hasSession());
        assertEquals("coinflip", state.gameType());
        assertEquals(50L, state.bet());
        assertEquals(PlayerState.BETTING, state.playerState());
        assertEquals("heads", state.result());
    }

    @Test
    void unknownStateIdFallsBackToAvailable() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("Steve", 100, 1, 0, 0);
        state.updateGame(true, "coinflip", 50, "hacked_state", "");
        assertEquals(PlayerState.AVAILABLE, state.playerState());
    }

    @Test
    void noSessionMeansAvailable() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("Steve", 100, 1, 0, 0);
        state.updateGame(false, "", 0, "betting", "x");
        assertFalse(state.hasSession());
        assertEquals(PlayerState.AVAILABLE, state.playerState());
    }

    @Test
    void clearResetsEverything() {
        ClientGamblingState state = ClientGamblingState.get();
        state.updateProfile("Steve", 5000, 7, 10000, 4000);
        state.updateGame(true, "coinflip", 50, "betting", "heads");
        state.clear();
        assertFalse(state.hasReceivedProfile());
        assertFalse(state.hasSession());
        assertEquals("", state.playerName());
        assertEquals(0L, state.chips());
        assertEquals(1, state.level());
        assertEquals(PlayerState.AVAILABLE, state.playerState());
        assertEquals("", state.result());
    }

    @Test
    void oversizedStringsAreTruncated() {
        ClientGamblingState state = ClientGamblingState.get();
        state.clear();
        state.updateProfile("N".repeat(500), 0, 1, 0, 0);
        assertEquals(32, state.playerName().length());
        state.updateGame(true, "G".repeat(500), 0, "in_game", "R".repeat(500));
        assertEquals(32, state.gameType().length());
        assertEquals(64, state.result().length());
    }
}
