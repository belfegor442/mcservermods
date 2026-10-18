package com.gambling.game;

import com.gambling.economy.EconomyManager;
import com.gambling.util.Validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class GameSessionManager {
    public record Result(boolean ok, String messageKey, Object... args) {
        public static Result success(String key, Object... args) {
            return new Result(true, key, args);
        }

        public static Result error(String key, Object... args) {
            return new Result(false, key, args);
        }
    }

    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();
    private final EconomyManager economy;
    private final List<BiConsumer<UUID, GameSession>> changeListeners = new ArrayList<>();

    public GameSessionManager(EconomyManager economy) {
        this.economy = economy;
    }

    public void addChangeListener(BiConsumer<UUID, GameSession> listener) {
        changeListeners.add(listener);
    }

    public GameSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public synchronized Result startSession(UUID playerId, String rawGameType, long bet) {
        String gameType = Validation.sanitizeGameType(rawGameType);
        if (gameType == null || !Validation.isValidBet(bet)) {
            return Result.error("key.gambling.error.invalid_bet");
        }
        if (sessions.containsKey(playerId)) {
            return Result.error("key.gambling.error.already_in_game");
        }
        var profile = economy.getProfile(playerId);
        if (profile == null || profile.chips() < bet) {
            return Result.error("key.gambling.error.insufficient_funds");
        }
        GameSession session = new GameSession(playerId, gameType, bet, PlayerState.IN_GAME, "");
        sessions.put(playerId, session);
        fireChanged(playerId, session);
        return Result.success("key.gambling.cmd.started", gameType, Validation.sanitizeResult(Long.toString(bet)));
    }

    public synchronized Result commitBet(UUID playerId, long clientAmount) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return Result.error("key.gambling.error.no_session");
        }
        if (session.state() != PlayerState.IN_GAME) {
            return Result.error("key.gambling.error.invalid_state");
        }
        if (clientAmount != session.bet()) {
            return Result.error("key.gambling.error.bet_mismatch");
        }
        if (!economy.removeChips(playerId, null, session.bet(), "BET")) {
            return Result.error("key.gambling.error.insufficient_funds");
        }
        GameSession updated = session.withState(PlayerState.BETTING, "");
        sessions.put(playerId, updated);
        fireChanged(playerId, updated);
        return Result.success("key.gambling.cmd.committed", Long.toString(session.bet()));
    }

    public synchronized Result resolveRound(UUID playerId, boolean win, String rawResult) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return Result.error("key.gambling.error.no_session");
        }
        if (session.state() != PlayerState.BETTING) {
            return Result.error("key.gambling.error.invalid_state");
        }
        long payout = win ? Validation.safeAdd(session.bet(), session.bet()) : 0L;
        if (win && !economy.addChips(playerId, null, payout, "PAYOUT")) {
            return Result.error("key.gambling.error.payout_failed");
        }
        economy.addWager(playerId, session.bet());
        String resultText = Validation.sanitizeResult(rawResult);
        if (resultText.isEmpty()) {
            resultText = win ? "WIN" : "LOSS";
        }
        GameSession updated = session.withState(PlayerState.FINALIZING, resultText);
        sessions.put(playerId, updated);
        fireChanged(playerId, updated);
        return Result.success("key.gambling.cmd.resolved", resultText, Long.toString(payout));
    }

    public synchronized Result finishSession(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return Result.error("key.gambling.error.no_session");
        }
        if (session.state() != PlayerState.FINALIZING) {
            return Result.error("key.gambling.error.invalid_state");
        }
        sessions.remove(playerId);
        fireChanged(playerId, null);
        return Result.success("key.gambling.cmd.finished");
    }

    public synchronized Result cancelSession(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return Result.error("key.gambling.error.no_session");
        }
        if (session.state() != PlayerState.IN_GAME) {
            return Result.error("key.gambling.error.invalid_state");
        }
        sessions.remove(playerId);
        fireChanged(playerId, null);
        return Result.success("key.gambling.cmd.cancelled", "");
    }

    public synchronized void handleDisconnect(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        // A confirmed bet has already been deducted. If the player leaves before
        // the round is resolved, return the stake rather than silently destroying it.
        if (session.state() == PlayerState.BETTING) {
            economy.addChips(playerId, null, session.bet(), "DISCONNECT_REFUND");
        }
        sessions.remove(playerId);
        fireChanged(playerId, null);
    }

    private void fireChanged(UUID playerId, GameSession session) {
        for (BiConsumer<UUID, GameSession> listener : changeListeners) {
            try {
                listener.accept(playerId, session);
            } catch (Exception ignored) {
            }
        }
    }
}
