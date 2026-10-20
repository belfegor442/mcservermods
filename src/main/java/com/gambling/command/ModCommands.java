package com.gambling.command;

import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.game.GameSessionManager;
import com.gambling.server.GamblingServerState;
import com.gambling.util.ChipsFormat;
import com.gambling.util.Validation;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildChipsCommand());
            dispatcher.register(buildGamblingCommand());
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildChipsCommand() {
        return literal("chips")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendError(Text.translatable("key.gambling.error.players_only"));
                        return 0;
                    }
                    GamblingServerState state = requireState(ctx.getSource());
                    if (state == null) {
                        return 0;
                    }
                    PlayerProfile profile = state.economy().getOrCreate(player.getUuid(), player.getName().getString());
                    ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.balance.self",
                            ChipsFormat.format(profile.chips()), String.valueOf(profile.level())), false);
                    return 1;
                })
                .then(literal("balance")
                        .then(argument("player", StringArgumentType.word())
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    GamblingServerState state = requireState(ctx.getSource());
                                    if (state == null) {
                                        return 0;
                                    }
                                    PlayerProfile profile = resolveTarget(state, ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"));
                                    if (profile == null) {
                                        ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.error.player_not_found"), false);
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.balance.other",
                                            profile.name(), ChipsFormat.format(profile.chips()), String.valueOf(profile.level())), true);
                                    return 1;
                                })))
                .then(literal("set")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("amount", LongArgumentType.longArg(0, Validation.MAX_ADMIN_AMOUNT))
                                        .executes(ctx -> {
                                            GamblingServerState state = requireState(ctx.getSource());
                                            if (state == null) {
                                                return 0;
                                            }
                                            String name = StringArgumentType.getString(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            PlayerProfile target = resolveTarget(state, ctx.getSource(), name);
                                            if (target == null) {
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.error.player_not_found"), false);
                                                return 0;
                                            }
                                            boolean ok = state.economy().setChips(target.uuid(), target.name(), amount, "ADMIN_SET");
                                            if (ok) {
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.set",
                                                        target.name(), ChipsFormat.format(amount)), true);
                                            }
                                            return ok ? 1 : 0;
                                        }))))
                .then(literal("add")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("amount", LongArgumentType.longArg(1, Validation.MAX_ADMIN_AMOUNT))
                                        .executes(ctx -> {
                                            GamblingServerState state = requireState(ctx.getSource());
                                            if (state == null) {
                                                return 0;
                                            }
                                            String name = StringArgumentType.getString(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            PlayerProfile target = resolveTarget(state, ctx.getSource(), name);
                                            if (target == null) {
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.error.player_not_found"), false);
                                                return 0;
                                            }
                                            boolean ok = state.economy().addChips(target.uuid(), target.name(), amount, "ADMIN_ADD");
                                            if (ok) {
                                                PlayerProfile profile = state.economy().getProfile(target.uuid());
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.add",
                                                        ChipsFormat.format(amount), target.name(),
                                                        ChipsFormat.format(profile != null ? profile.chips() : amount)), true);
                                            }
                                            return ok ? 1 : 0;
                                        }))))
                .then(literal("remove")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("amount", LongArgumentType.longArg(1, Validation.MAX_ADMIN_AMOUNT))
                                        .executes(ctx -> {
                                            GamblingServerState state = requireState(ctx.getSource());
                                            if (state == null) {
                                                return 0;
                                            }
                                            String name = StringArgumentType.getString(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");
                                            PlayerProfile target = resolveTarget(state, ctx.getSource(), name);
                                            if (target == null) {
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.error.player_not_found"), false);
                                                return 0;
                                            }
                                            boolean ok = state.economy().removeChips(target.uuid(), target.name(), amount, "ADMIN_REMOVE");
                                            if (ok) {
                                                PlayerProfile profile = state.economy().getProfile(target.uuid());
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.remove",
                                                        ChipsFormat.format(amount), target.name(),
                                                        ChipsFormat.format(profile != null ? profile.chips() : 0)), true);
                                            } else {
                                                ctx.getSource().sendFeedback(() -> Text.translatable("key.gambling.cmd.remove_failed"), false);
                                            }
                                            return ok ? 1 : 0;
                                        }))))
                .then(literal("profile")
                        .executes(ctx -> showProfile(ctx, null))
                        .then(argument("player", StringArgumentType.word())
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> showProfile(ctx, StringArgumentType.getString(ctx, "player")))));
    }

    private static int showProfile(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                   String targetName) {
        ServerCommandSource source = ctx.getSource();
        GamblingServerState state = requireState(source);
        if (state == null) {
            return 0;
        }
        PlayerProfile profile;
        if (targetName == null) {
            ServerPlayerEntity self = source.getPlayer();
            if (self == null) {
                source.sendError(Text.translatable("key.gambling.error.players_only"));
                return 0;
            }
            profile = state.economy().getOrCreate(self.getUuid(), self.getName().getString());
        } else {
            profile = resolveTarget(state, source, targetName);
            if (profile == null) {
                source.sendFeedback(() -> Text.translatable("key.gambling.error.player_not_found"), false);
                return 0;
            }
        }
        java.util.UUID uuid = profile.uuid();
        GameSession session = state.sessions().getSession(uuid);
        source.sendFeedback(() -> Text.translatable("key.gambling.cmd.profile",
                profile.name(),
                ChipsFormat.format(profile.chips()),
                String.valueOf(profile.level()),
                ChipsFormat.format(profile.xp()),
                ChipsFormat.format(profile.totalWagered())), targetName == null);
        if (session != null) {
            GameSession s = session;
            source.sendFeedback(() -> Text.translatable("key.gambling.cmd.session",
                    s.gameType(), ChipsFormat.format(s.bet()), s.state().id(), s.result()), false);
        } else {
            source.sendFeedback(() -> Text.translatable("key.gambling.cmd.session.none"), false);
        }
        return 1;
    }

    /**
     * Resolves a command target by name: the online player if connected,
     * otherwise the stored profile of an offline player. Returns null when
     * neither exists (the profile is only created when the player joins).
     */
    private static PlayerProfile resolveTarget(GamblingServerState state, ServerCommandSource source,
                                               String name) {
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(name);
        if (online != null) {
            return state.economy().getOrCreate(online.getUuid(), online.getName().getString());
        }
        return state.economy().findByName(name);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildGamblingCommand() {
        long maxBet = com.gambling.config.GamblingConfig.get().maxBet();
        return literal("gambling")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(
                            () -> Text.translatable("key.gambling.help"), false);
                    return 1;
                })
                .then(literal("start")
                        // Demo/debug flow: resolving a round requires op, so only
                        // operators may open sessions outside the casino games.
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(argument("game", StringArgumentType.word())
                                .then(argument("bet", LongArgumentType.longArg(1, maxBet))
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                            GamblingServerState state = requireState(ctx.getSource());
                                            if (player == null || state == null) {
                                                return 0;
                                            }
                                            String game = StringArgumentType.getString(ctx, "game");
                                            long bet = LongArgumentType.getLong(ctx, "bet");
                                            GameSessionManager.Result result =
                                                    state.sessions().startSession(player.getUuid(), game, bet);
                                            state.replyResult(player, result);
                                            return result.ok() ? 1 : 0;
                                        }))))
                .then(literal("bet")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            GamblingServerState state = requireState(ctx.getSource());
                            if (player == null || state == null) {
                                return 0;
                            }
                            GameSession session = state.sessions().getSession(player.getUuid());
                            if (session == null) {
                                state.replyResult(player, GameSessionManager.Result.error("key.gambling.error.no_session"));
                                return 0;
                            }
                            GameSessionManager.Result result =
                                    state.sessions().commitBet(player.getUuid(), session.bet());
                            state.replyResult(player, result);
                            return result.ok() ? 1 : 0;
                        })
                        .then(argument("amount", LongArgumentType.longArg(1, maxBet))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    GamblingServerState state = requireState(ctx.getSource());
                                    if (player == null || state == null) {
                                        return 0;
                                    }
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    GameSessionManager.Result result = state.sessions().commitBet(player.getUuid(), amount);
                                    state.replyResult(player, result);
                                    return result.ok() ? 1 : 0;
                                })))
                .then(literal("resolve")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("win")
                                .executes(ctx -> resolve(ctx.getSource(), true, "WIN")))
                        .then(literal("lose")
                                .executes(ctx -> resolve(ctx.getSource(), false, "LOSS")))
                        .then(literal("win")
                                .then(argument("result", StringArgumentType.greedyString())
                                        .executes(ctx -> resolve(ctx.getSource(), true,
                                                StringArgumentType.getString(ctx, "result"))))))
                .then(literal("finish")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            GamblingServerState state = requireState(ctx.getSource());
                            if (player == null || state == null) {
                                return 0;
                            }
                            GameSessionManager.Result result = state.sessions().finishSession(player.getUuid());
                            state.replyResult(player, result);
                            return result.ok() ? 1 : 0;
                        }))
                .then(literal("cancel")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            GamblingServerState state = requireState(ctx.getSource());
                            if (player == null || state == null) {
                                return 0;
                            }
                            GameSessionManager.Result result = state.sessions().cancelSession(player.getUuid());
                            state.replyResult(player, result);
                            return result.ok() ? 1 : 0;
                        }))
                .then(literal("help")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    () -> Text.translatable("key.gambling.help"), false);
                            return 1;
                        }))
                .then(literal("sync")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            GamblingServerState state = requireState(ctx.getSource());
                            if (player == null || state == null) {
                                return 0;
                            }
                            state.sendFullSync(player);
                            return 1;
                        }));
    }

    private static int resolve(ServerCommandSource source, boolean win, String resultText) {
        ServerPlayerEntity player = source.getPlayer();
        GamblingServerState state = requireState(source);
        if (player == null || state == null) {
            return 0;
        }
        GameSessionManager.Result result = state.sessions().resolveRound(player.getUuid(), win, resultText);
        state.replyResult(player, result);
        return result.ok() ? 1 : 0;
    }

    private static GamblingServerState requireState(ServerCommandSource source) {
        GamblingServerState state = GamblingServerState.get();
        if (state == null) {
            source.sendFeedback(() -> Text.translatable("key.gambling.error.not_ready"), false);
        }
        return state;
    }
}
