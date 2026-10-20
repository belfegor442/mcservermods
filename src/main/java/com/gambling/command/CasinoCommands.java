package com.gambling.command;

import com.gambling.game.GameSessionManager;
import com.gambling.game.Roulette;
import com.gambling.game.Slots;
import com.gambling.server.GamblingServerState;
import com.gambling.config.GamblingConfig;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CasinoCommands {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ROULETTE_COLORS = Set.of(Roulette.RED, Roulette.BLACK, Roulette.GREEN);

    private CasinoCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            long maxBet = GamblingConfig.get().maxBet();
            var casino = literal("casino").executes(context -> {
                context.getSource().sendFeedback(
                        () -> Text.translatable("key.gambling.casino.help"), false);
                return 1;
            });
            casino.then(literal("coinflip")
                    .then(argument("bet", LongArgumentType.longArg(1, maxBet))
                            .then(argument("side", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("heads");
                                        builder.suggest("tails");
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> playCoinFlip(
                                            context.getSource(),
                                            LongArgumentType.getLong(context, "bet"),
                                            StringArgumentType.getString(context, "side"))))));
            casino.then(literal("roulette")
                    .then(argument("bet", LongArgumentType.longArg(1, maxBet))
                            .then(argument("color", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest(Roulette.RED);
                                        builder.suggest(Roulette.BLACK);
                                        builder.suggest(Roulette.GREEN);
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> playRoulette(
                                            context.getSource(),
                                            LongArgumentType.getLong(context, "bet"),
                                            StringArgumentType.getString(context, "color"))))));
            casino.then(literal("slots")
                    .then(argument("bet", LongArgumentType.longArg(1, maxBet))
                            .executes(context -> playSlots(
                                    context.getSource(),
                                    LongArgumentType.getLong(context, "bet")))));
            casino.then(literal("help").executes(context -> {
                context.getSource().sendFeedback(
                        () -> Text.translatable("key.gambling.casino.help"), false);
                return 1;
            }));
            dispatcher.register(casino);
        });
    }

    private static int playCoinFlip(ServerCommandSource source, long bet, String rawSide) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("key.gambling.error.players_only"));
            return 0;
        }
        String chosenSide = rawSide.toLowerCase(Locale.ROOT);
        if (!chosenSide.equals("heads") && !chosenSide.equals("tails")) {
            source.sendError(Text.translatable("key.gambling.error.coinflip_side"));
            return 0;
        }

        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendError(Text.translatable("key.gambling.error.not_ready"));
            return 0;
        }

        String resultSide = RANDOM.nextBoolean() ? "heads" : "tails";
        boolean won = chosenSide.equals(resultSide);
        return playRound(state, player, "coinflip", bet, won, 2L,
                resultSide.toUpperCase(Locale.ROOT) + (won ? " — WIN" : " — LOSS"),
                won ? "key.gambling.casino.coinflip.win" : "key.gambling.casino.coinflip.loss",
                won ? new Object[]{resultSide, chosenSide, Long.toString(bet * 2L)}
                        : new Object[]{resultSide, chosenSide});
    }

    private static int playRoulette(ServerCommandSource source, long bet, String rawColor) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("key.gambling.error.players_only"));
            return 0;
        }
        String chosenColor = rawColor.toLowerCase(Locale.ROOT);
        if (!ROULETTE_COLORS.contains(chosenColor)) {
            source.sendError(Text.translatable("key.gambling.error.roulette_color"));
            return 0;
        }

        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendError(Text.translatable("key.gambling.error.not_ready"));
            return 0;
        }

        Roulette.Outcome outcome = Roulette.spin(chosenColor, RANDOM);
        return playRound(state, player, "roulette", bet, outcome.won(), outcome.multiplier(),
                outcome.resultText(),
                outcome.won() ? "key.gambling.casino.roulette.win" : "key.gambling.casino.roulette.loss",
                outcome.won()
                        ? new Object[]{Integer.toString(outcome.number()), outcome.resultColor(),
                                outcome.chosenColor(), Long.toString(bet * outcome.multiplier())}
                        : new Object[]{Integer.toString(outcome.number()), outcome.resultColor(),
                                outcome.chosenColor()});
    }

    private static int playSlots(ServerCommandSource source, long bet) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("key.gambling.error.players_only"));
            return 0;
        }
        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendError(Text.translatable("key.gambling.error.not_ready"));
            return 0;
        }

        Slots.Outcome outcome = Slots.roll(RANDOM);
        return playRound(state, player, "slots", bet, outcome.won(), outcome.multiplier(),
                outcome.resultText(),
                outcome.won() ? "key.gambling.casino.slots.win" : "key.gambling.casino.slots.loss",
                outcome.won()
                        ? new Object[]{outcome.first(), outcome.second(), outcome.third(),
                                Long.toString(bet * outcome.multiplier())}
                        : new Object[]{outcome.first(), outcome.second(), outcome.third()});
    }

    /**
     * Runs one complete round: open the session, commit the stake, resolve the
     * outcome, and report it. The session stays in FINALIZING (visible on the
     * HUD) and is closed automatically when the next session starts.
     */
    private static int playRound(GamblingServerState state, ServerPlayerEntity player,
                                 String gameType, long bet, boolean won, long multiplier,
                                 String resultText, String messageKey, Object[] messageArgs) {
        GameSessionManager sessions = state.sessions();
        long now = System.currentTimeMillis();
        long waitMillis = state.roundLimiter().check(player.getUuid(), now);
        if (waitMillis > 0) {
            state.replyResult(player, GameSessionManager.Result.error(
                    "key.gambling.error.cooldown", Long.toString((waitMillis + 999) / 1000)));
            return 0;
        }

        GameSessionManager.Result started = sessions.startSession(player.getUuid(), gameType, bet);
        if (!started.ok()) {
            state.replyResult(player, started);
            return 0;
        }
        state.roundLimiter().record(player.getUuid(), now);

        GameSessionManager.Result committed = sessions.commitBet(player.getUuid(), bet);
        if (!committed.ok()) {
            sessions.cancelSession(player.getUuid());
            state.replyResult(player, committed);
            return 0;
        }

        GameSessionManager.Result resolved = sessions.resolveRound(player.getUuid(), won, resultText, multiplier);
        if (!resolved.ok()) {
            state.replyResult(player, resolved);
            return 0;
        }

        state.replyResult(player, GameSessionManager.Result.success(messageKey, messageArgs));
        return 1;
    }
}
