package com.gambling;

import com.gambling.command.ModCommands;
import com.gambling.command.CasinoCommands;
import com.gambling.game.GameSessionManager;
import com.gambling.network.BetRequestC2S;
import com.gambling.network.GameStateSyncS2C;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.server.GamblingServerState;
import com.gambling.util.Validation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GamblingMod implements ModInitializer {
    public static final String MOD_ID = "gambling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int AUTOSAVE_INTERVAL_TICKS = 1200;

    @Override
    public void onInitialize() {
        registerPayloads();
        ModCommands.register();
        CasinoCommands.register();
        registerServerEvents();
        LOGGER.info("Gambling initialized.");
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ProfileSyncS2C.ID, ProfileSyncS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(GameStateSyncS2C.ID, GameStateSyncS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(BetRequestC2S.ID, BetRequestC2S.CODEC);
    }

    private static void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                GamblingServerState.start(server, FabricLoader.getInstance().getConfigDir()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GamblingServerState.stop());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GamblingServerState state = GamblingServerState.get();
            if (state != null) {
                state.onPlayerJoin(handler.getPlayer());
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GamblingServerState state = GamblingServerState.get();
            if (state != null) {
                state.onPlayerQuit(handler.getPlayer());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(BetRequestC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            GamblingServerState state = GamblingServerState.get();
            if (state == null) {
                return;
            }
            long amount = payload.amount();
            if (!Validation.isValidBet(amount)) {
                state.replyResult(player, GameSessionManager.Result.error("key.gambling.error.invalid_bet"));
                return;
            }
            GameSessionManager.Result result = state.sessions().commitBet(player.getUuid(), amount);
            state.replyResult(player, result);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GamblingServerState state = GamblingServerState.get();
            if (state != null && state.economy().isDirty()
                    && server.getTicks() % AUTOSAVE_INTERVAL_TICKS == 0) {
                state.economy().save();
            }
        });
    }
}
