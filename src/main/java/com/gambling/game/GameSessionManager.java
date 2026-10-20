package com.gambling.game;

import com.gambling.economy.EconomyManager;
import com.gambling.util.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class GameSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-sessions");

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

    public synchronized Map<UUID, GameSession> snapshot() {
        return Map.copyOf(sessions);
    }

    /**
     * Restores sessions persisted by a previous server run (crash recovery).
     * Unresolved BETTING stakes are refunded and dropped: their outcome no
     * longer exists, so keeping them would silently destroy the stake.
     */
    public synchronized void restoreSessions(Map<UUID, GameSession> stored) {
        for (Map.Entry<UUID, GameSession> entry : stored.entrySet()) {
            GameSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            if (session.state() == PlayerState.BETTING) {
                // Refund regardless of the stored metadata: the stake is real.
                if (Validation.isValidBet(session.bet())
                        && !economy.addChips(entry.getKey(), null, session.bet(), "CRASH_RECOVERY_REFUND")) {
                    LOGGER.error("Could not refund {} CHIPS for {} after restore.",
                            session.bet(), entry.getKey());
                }
                continue;
            }
            if (Validation.sanitizeGameType(session.gameType()) == null) {
                continue;
            }
            sessions.put(entry.getKey(), session);
        }
    }

    public synchronized Result startSession(UUID playerId, String rawGameType, long bet) {
        String gameType = Validation.sanitizeGameType(rawGameType);
        if (gameType == null || !Validation.isValidBet(bet)) {
            return Result.error("key.gambling.error.invalid_bet");
        }
        GameSession existing = sessions.get(playerId);
        if (existing != null) {
            if (existing.state() != PlayerState.FINALIZING) {
                return Result.error("key.gambling.error.already_in_game");
            }
            // The previous round is settled and only lingering so the player can
            // see its result. Close it silently before opening the new session.
            sessions.remove(playerId);
            fireChanged(playerId, null);
        }
        var profile = economy.getProfile(playerId);
        if (profile == null || profile.chips() < bet) {
            return Result.error("key.gambling.error.insufficient_funds");
        }
        GameSession session = new GameSession(playerId, gameType, bet, PlayerState.IN_GAME, "");
        sessions.put(playerId, session);
        fireChanged(playerId, session);
        return Result.success("key.gambling.cmd.started", gameType, Long.toString(bet));
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
        return resolveRound(playerId, win, rawResult, 2L);
    }

    public synchronized Result resolveRound(UUID playerId, boolean win, String rawResult, long payoutMultiplier) {
        if (payoutMultiplier < 1L || payoutMultiplier > 36L) {
            return Result.error("key.gambling.error.invalid_bet");
        }
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return Result.error("key.gambling.error.no_session");
        }
        if (session.state() != PlayerState.BETTING) {
            return Result.error("key.gambling.error.invalid_state");
        }
        long payout = win ? session.bet() * payoutMultiplier : 0L;
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
        return Result.success("key.gambling.cmd.cancelled");
    }

    public synchronized void handleDisconnect(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        // A confirmed bet has already been deducted. If the player leaves before
        // the round is resolved, return the stake rather than silently destroying it.
        if (session.state() == PlayerState.BETTING
                && !economy.addChips(playerId, null, session.bet(), "DISCONNECT_REFUND")) {
            LOGGER.error("Could not refund {} CHIPS to {} on disconnect.",
                    session.bet(), playerId);
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
