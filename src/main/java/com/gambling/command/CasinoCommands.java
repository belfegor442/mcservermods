package com.gambling.command;

import com.gambling.game.GameSessionManager;
import com.gambling.game.Roulette;
import com.gambling.game.Slots;
import com.gambling.network.OpenCasinoS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.gambling.server.GamblingServerState;
import com.gambling.server.ModText;
import com.gambling.util.ServerTranslations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.gambling.config.GamblingConfig;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class CasinoCommands {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ROULETTE_COLORS = Set.of(Roulette.RED, Roulette.BLACK, Roulette.GREEN);

    private CasinoCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            long maxBet = GamblingConfig.get().maxBet();
            var casino = literal("casino").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                // Vanilla clients cannot open the graphical menu (and Fabric
                // would reject the payload); they get the command list instead.
                if (player == null || !ServerPlayNetworking.canSend(player, OpenCasinoS2C.ID)) {
                    context.getSource().sendSuccess(
                            () -> ModText.of(context.getSource(), "key.gambling.casino.help"), false);
                    return 1;
                }
                ServerPlayNetworking.send(player, new OpenCasinoS2C());
                playSound(player, SoundEvents.UI_BUTTON_CLICK, 0.55F, 1.15F);
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
                context.getSource().sendSuccess(
                        () -> ModText.of(context.getSource(), "key.gambling.casino.help"), false);
                return 1;
            }));
            dispatcher.register(casino);
        });
    }

    private static int playCoinFlip(CommandSourceStack source, long bet, String rawSide) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.players_only"));
            return 0;
        }
        String chosenSide = rawSide.toLowerCase(Locale.ROOT);
        if (!chosenSide.equals("heads") && !chosenSide.equals("tails")) {
            source.sendFailure(ModText.of(source, "key.gambling.error.coinflip_side"));
            return 0;
        }

        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.not_ready"));
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

    private static int playRoulette(CommandSourceStack source, long bet, String rawColor) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.players_only"));
            return 0;
        }
        String chosenColor = rawColor.toLowerCase(Locale.ROOT);
        if (!ROULETTE_COLORS.contains(chosenColor)) {
            source.sendFailure(ModText.of(source, "key.gambling.error.roulette_color"));
            return 0;
        }

        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendFailure(ModText.of(source, "key.gambling.error.not_ready"));
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

    private static int playSlots(CommandSourceStack source, long bet) {
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
    private static void playSound(ServerPlayer player,
                                  net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                  float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                sound, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, RANDOM.nextLong()));
    }

    private static void showRoundFeedback(ServerPlayer player, boolean won,
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

    private static int playRound(GamblingServerState state, ServerPlayer player,
                                 String gameType, long bet, boolean won, long multiplier,
                                 String resultText, String messageKey, Object[] messageArgs) {
        GameSessionManager sessions = state.sessions();
        long now = System.currentTimeMillis();
        long waitMillis = state.roundLimiter().check(player.getUUID(), now);
        if (waitMillis > 0) {
            state.replyResult(player, GameSessionManager.Result.error(
                    "key.gambling.error.cooldown", Long.toString((waitMillis + 999) / 1000)));
            return 0;
        }

        GameSessionManager.Result started = sessions.startSession(player.getUUID(), gameType, bet);
        if (!started.ok()) {
            state.replyResult(player, started);
            return 0;
        }

        GameSessionManager.Result committed = sessions.commitBet(player.getUUID(), bet);
        if (!committed.ok()) {
            sessions.cancelSession(player.getUUID());
            state.replyResult(player, committed);
            return 0;
        }
        // Only rounds that actually staked chips consume the cooldown, so a
        // rejected start/commit does not lock the player out.
        state.roundLimiter().record(player.getUUID(), now);

        GameSessionManager.Result resolved = sessions.resolveRound(player.getUUID(), won, resultText, multiplier);
        if (!resolved.ok()) {
            state.replyResult(player, resolved);
            return 0;
        }

        state.replyResult(player, GameSessionManager.Result.success(messageKey, messageArgs));
        showRoundFeedback(player, won, messageKey, messageArgs);
        return 1;
    }
}
