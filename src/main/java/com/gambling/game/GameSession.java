package com.gambling.game;

import java.util.UUID;

public record GameSession(UUID playerId, String gameType, long bet, PlayerState state, String result) {
    public GameSession withState(PlayerState newState, String newResult) {
        return new GameSession(playerId, gameType, bet, newState, newResult);
    }
}
