package com.gambling.command;

import com.gambling.game.GameSessionManager;
import com.gambling.server.GamblingServerState;
import com.gambling.util.Validation;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CasinoCommands {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private CasinoCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var casino = literal("casino");
            casino.then(literal("coinflip")
                    .then(argument("bet", LongArgumentType.longArg(1, Validation.MAX_BET))
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
                    .then(argument("bet", LongArgumentType.longArg(1, Validation.MAX_BET))
                            .then(argument("color", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("red");
                                        builder.suggest("black");
                                        builder.suggest("green");
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> playRoulette(
                                            context.getSource(),
                                            LongArgumentType.getLong(context, "bet"),
                                            StringArgumentType.getString(context, "color"))))));
            casino.then(literal("help").executes(context -> {
                context.getSource().sendFeedback(
                        () -> Text.translatable("key.gambling.casino.help"), false);
                return 1;
            }));
            dispatcher.register(casino);
        });
    }

    private static int playCoinFlip(net.minecraft.server.command.ServerCommandSource source, long bet, String rawSide) {
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
            source.sendError(Text.literal("Casino system is not ready."));
            return 0;
        }

        GameSessionManager sessions = state.sessions();
        GameSessionManager.Result started = sessions.startSession(player.getUuid(), "coinflip", bet);
        if (!started.ok()) {
            state.replyResult(player, started);
            return 0;
        }

        GameSessionManager.Result committed = sessions.commitBet(player.getUuid(), bet);
        if (!committed.ok()) {
            sessions.cancelSession(player.getUuid());
            state.replyResult(player, committed);
            return 0;
        }

        String resultSide = RANDOM.nextBoolean() ? "heads" : "tails";
        boolean won = chosenSide.equals(resultSide);
        GameSessionManager.Result resolved = sessions.resolveRound(player.getUuid(), won,
                resultSide.toUpperCase(Locale.ROOT) + (won ? " — WIN" : " — LOSS"));
        if (!resolved.ok()) {
            state.replyResult(player, resolved);
            return 0;
        }

        if (won) {
            state.replyResult(player, GameSessionManager.Result.success(
                    "key.gambling.casino.coinflip.win",
                    resultSide,
                    chosenSide,
                    Long.toString(bet * 2L)));
        } else {
            state.replyResult(player, GameSessionManager.Result.success(
                    "key.gambling.casino.coinflip.loss",
                    resultSide,
                    chosenSide));
        }
        GameSessionManager.Result finished = sessions.finishSession(player.getUuid());
        if (!finished.ok()) {
            state.replyResult(player, finished);
            return 0;
        }
        return 1;
    }
    private static int playRoulette(net.minecraft.server.command.ServerCommandSource source, long bet, String rawColor) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("key.gambling.error.players_only"));
            return 0;
        }
        String chosenColor = rawColor.toLowerCase(Locale.ROOT);
        if (!Set.of("red", "black", "green").contains(chosenColor)) {
            source.sendError(Text.translatable("key.gambling.error.roulette_color"));
            return 0;
        }

        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendError(Text.literal("Casino system is not ready."));
            return 0;
        }

        GameSessionManager sessions = state.sessions();
        GameSessionManager.Result started = sessions.startSession(player.getUuid(), "roulette", bet);
        if (!started.ok()) {
            state.replyResult(player, started);
            return 0;
        }
        GameSessionManager.Result committed = sessions.commitBet(player.getUuid(), bet);
        if (!committed.ok()) {
            sessions.cancelSession(player.getUuid());
            state.replyResult(player, committed);
            return 0;
        }

        int number = RANDOM.nextInt(37);
        String resultColor = number == 0 ? "green" : RED_NUMBERS.contains(number) ? "red" : "black";
        boolean won = chosenColor.equals(resultColor);
        long multiplier = "green".equals(chosenColor) ? 36L : 2L;
        String resultText = number + " " + resultColor.toUpperCase(Locale.ROOT)
                + (won ? " — WIN" : " — LOSS");
        GameSessionManager.Result resolved =
                sessions.resolveRound(player.getUuid(), won, resultText, multiplier);
        if (!resolved.ok()) {
            state.replyResult(player, resolved);
            return 0;
        }

        if (won) {
            state.replyResult(player, GameSessionManager.Result.success(
                    "key.gambling.casino.roulette.win",
                    Integer.toString(number),
                    resultColor,
                    chosenColor,
                    Long.toString(bet * multiplier)));
        } else {
            state.replyResult(player, GameSessionManager.Result.success(
                    "key.gambling.casino.roulette.loss",
                    Integer.toString(number),
                    resultColor,
                    chosenColor));
        }
        GameSessionManager.Result finished = sessions.finishSession(player.getUuid());
        if (!finished.ok()) {
            state.replyResult(player, finished);
            return 0;
        }
        return 1;
    }

}
