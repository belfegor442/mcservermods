package com.gambling.game;

public enum PlayerState {
    AVAILABLE("available"),
    IN_GAME("in_game"),
    BETTING("betting"),
    FINALIZING("finalizing");

    private final String id;

    PlayerState(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PlayerState fromId(String id) {
        for (PlayerState state : values()) {
            if (state.id.equals(id)) {
                return state;
            }
        }
        return AVAILABLE;
    }
}
