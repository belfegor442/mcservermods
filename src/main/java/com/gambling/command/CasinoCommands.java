package com.gambling.command;

import com.gambling.config.GamblingConfig;
import com.gambling.game.GameSessionManager;
import com.gambling.game.Roulette;
import com.gambling.server.CasinoRound;
import com.gambling.server.GamblingServerState;
import com.gambling.server.ModText;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Chat entry points to the casino. The graphical entry point is the
 * server-driven chest GUI (opened by bare /casino and on join); the game
 * subcommands stay as a typed shortcut through the same CasinoRound engine.
 */
public final class CasinoCommands {
    private CasinoCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            long maxBet = GamblingConfig.get().maxBet();
            var casino = literal("casino").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player == null) {
                    context.getSource().sendSuccess(
                            () -> ModText.of(context.getSource(), "key.gambling.casino.help"), false);
                    return 1;
                }
                GamblingServerState state = GamblingServerState.get();
                if (state == null) {
                    context.getSource().sendFailure(
                            ModText.of(context.getSource(), "key.gambling.error.not_ready"));
                    return 0;
                }
                state.openCasinoMenu(player);
                CasinoRound.playClickSound(player);
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
                                    .executes(context -> play(context.getSource(), "coinflip",
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
                                    .executes(context -> play(context.getSource(), "roulette",
                                            LongArgumentType.getLong(context, "bet"),
                                            StringArgumentType.getString(context, "color"))))));
            casino.then(literal("slots")
                    .then(argument("bet", LongArgumentType.longArg(1, maxBet))
                            .executes(context -> play(context.getSource(), "slots",
                                    LongArgumentType.getLong(context, "bet"), null))));
            casino.then(literal("help").executes(context -> {
                context.getSource().sendSuccess(
                        () -> ModText.of(context.getSource(), "key.gambling.casino.help"), false);
                return 1;
            }));
            dispatcher.register(casino);
        });
    }

    private static int play(CommandSourceStack source, String gameType, long bet, String option) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.players_only"));
            return 0;
        }
        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.not_ready"));
            return 0;
        }

        CasinoRound.Outcome outcome = CasinoRound.play(state, player, gameType, bet, option);
        if (!outcome.ok()) {
            state.replyResult(player, GameSessionManager.Result.error(outcome.key(), outcome.args()));
            return 0;
        }
        state.replyResult(player, GameSessionManager.Result.success(outcome.key(), outcome.args()));
        CasinoRound.showFeedback(player, outcome.won(), outcome.key(), outcome.args());
        return 1;
    }
}
