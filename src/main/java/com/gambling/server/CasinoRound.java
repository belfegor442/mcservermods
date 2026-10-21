package com.gambling.server;

import com.gambling.game.GameSessionManager;
import com.gambling.game.Roulette;
import com.gambling.game.Slots;
import com.gambling.util.ServerTranslations;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/**
 * Shared casino round engine. Resolves one game, runs the transactional
 * session flow (cooldown limiter, stake commit, payout) and returns a
 * structured outcome. Both the chat commands and the server-driven chest GUI
 * call this single path, so every round passes the same economy guards.
 */
public final class CasinoRound {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ROULETTE_COLORS =
            Set.of(Roulette.RED, Roulette.BLACK, Roulette.GREEN);

    private CasinoRound() {
    }

    /** Structured result of one attempted round, for chat replies or GUI lore. */
    public record Outcome(boolean ok, boolean won, String resultText, String key, Object[] args) {
    }

    public static Outcome play(GamblingServerState state, ServerPlayer player,
                               String gameType, long bet, String option) {
        String chosen = option == null ? "" : option.toLowerCase(Locale.ROOT);
        return switch (gameType) {
            case "coinflip" -> playCoinFlip(state, player, bet, chosen);
            case "roulette" -> playRoulette(state, player, bet, chosen);
            case "slots" -> playSlots(state, player, bet);
            default -> new Outcome(false, false, "", "key.gambling.error.unknown_game",
                    new Object[]{gameType});
        };
    }

    private static Outcome playCoinFlip(GamblingServerState state, ServerPlayer player,
                                        long bet, String chosen) {
        if (!chosen.equals("heads") && !chosen.equals("tails")) {
            return new Outcome(false, false, "", "key.gambling.error.coinflip_side", new Object[0]);
        }
        String resultSide = RANDOM.nextBoolean() ? "heads" : "tails";
        boolean won = chosen.equals(resultSide);
        return resolve(state, player, "coinflip", bet, won, 2L,
                resultSide.toUpperCase(Locale.ROOT) + (won ? " — WIN" : " — LOSS"),
                won ? "key.gambling.casino.coinflip.win" : "key.gambling.casino.coinflip.loss",
                won ? new Object[]{resultSide, chosen, Long.toString(bet * 2L)}
                        : new Object[]{resultSide, chosen});
    }

    private static Outcome playRoulette(GamblingServerState state, ServerPlayer player,
                                        long bet, String chosen) {
        if (!ROULETTE_COLORS.contains(chosen)) {
            return new Outcome(false, false, "", "key.gambling.error.roulette_color", new Object[0]);
        }
        Roulette.Outcome outcome = Roulette.spin(chosen, RANDOM);
        return resolve(state, player, "roulette", bet, outcome.won(), outcome.multiplier(),
                outcome.resultText(),
                outcome.won() ? "key.gambling.casino.roulette.win" : "key.gambling.casino.roulette.loss",
                outcome.won()
                        ? new Object[]{Integer.toString(outcome.number()), outcome.resultColor(),
                                outcome.chosenColor(), Long.toString(bet * outcome.multiplier())}
                        : new Object[]{Integer.toString(outcome.number()), outcome.resultColor(),
                                outcome.chosenColor()});
    }

    private static Outcome playSlots(GamblingServerState state, ServerPlayer player, long bet) {
        Slots.Outcome outcome = Slots.roll(RANDOM);
        return resolve(state, player, "slots", bet, outcome.won(), outcome.multiplier(),
                outcome.resultText(),
                outcome.won() ? "key.gambling.casino.slots.win" : "key.gambling.casino.slots.loss",
                outcome.won()
                        ? new Object[]{outcome.first(), outcome.second(), outcome.third(),
                                Long.toString(bet * outcome.multiplier())}
                        : new Object[]{outcome.first(), outcome.second(), outcome.third()});
    }

    /**
     * Runs the complete transactional flow for a resolved outcome: open the
     * session, commit the stake, resolve the round. Rejected steps return an
     * error outcome carrying the original message key and arguments; only
     * rounds that actually staked chips consume the cooldown.
     */
    private static Outcome resolve(GamblingServerState state, ServerPlayer player,
                                   String gameType, long bet, boolean won, long multiplier,
                                   String resultText, String key, Object[] args) {
        GameSessionManager sessions = state.sessions();
        long now = System.currentTimeMillis();
        long waitMillis = state.roundLimiter().check(player.getUUID(), now);
        if (waitMillis > 0) {
            return new Outcome(false, false, resultText, "key.gambling.error.cooldown",
                    new Object[]{Long.toString((waitMillis + 999) / 1000)});
        }

        GameSessionManager.Result started = sessions.startSession(player.getUUID(), gameType, bet);
        if (!started.ok()) {
            return new Outcome(false, false, resultText, started.messageKey(), started.args());
        }

        GameSessionManager.Result committed = sessions.commitBet(player.getUUID(), bet);
        if (!committed.ok()) {
            sessions.cancelSession(player.getUUID());
            return new Outcome(false, false, resultText, committed.messageKey(), committed.args());
        }
        state.roundLimiter().record(player.getUUID(), now);

        GameSessionManager.Result resolved =
                sessions.resolveRound(player.getUUID(), won, resultText, multiplier);
        if (!resolved.ok()) {
            return new Outcome(false, false, resultText, resolved.messageKey(), resolved.args());
        }
        return new Outcome(true, won, resultText, key, args);
    }

    /** Big center title plus win/loss sound for every completed round. */
    public static void showFeedback(ServerPlayer player, boolean won,
                                    String messageKey, Object[] messageArgs) {
        String language = player.clientInformation().language();
        String title = ServerTranslations.translate(
                won ? "key.gambling.feedback.win" : "key.gambling.feedback.loss", language);
        String subtitle = ServerTranslations.translate(messageKey, language, messageArgs);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 45, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        playSound(player,
                won ? Holder.direct(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE) : SoundEvents.UI_BUTTON_CLICK,
                won ? 0.85F : 0.45F,
                won ? 1.0F : 0.75F);
    }

    public static void playClickSound(ServerPlayer player) {
        playSound(player, SoundEvents.UI_BUTTON_CLICK, 0.55F, 1.15F);
    }

    private static void playSound(ServerPlayer player,
                                  Holder<net.minecraft.sounds.SoundEvent> sound,
                                  float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                sound, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, RANDOM.nextLong()));
    }
}
