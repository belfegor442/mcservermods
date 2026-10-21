package com.gambling;

import com.gambling.command.ModCommands;
import com.gambling.command.CasinoCommands;
import com.gambling.config.GamblingConfig;
import com.gambling.game.GameSessionManager;
import com.gambling.network.BetRequestC2S;
import com.gambling.network.GameStateSyncS2C;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.network.OpenCasinoS2C;
import com.gambling.server.GamblingServerState;
import com.gambling.util.Validation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GamblingMod implements ModInitializer {
    public static final String MOD_ID = "gambling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int TICKS_PER_SECOND = 20;

    @Override
    public void onInitialize() {
        GamblingConfig.load(FabricLoader.getInstance().getConfigDir()
                .resolve("gambling").resolve("gambling.json"));
        registerPayloads();
        ModCommands.register();
        CasinoCommands.register();
        registerServerEvents();
        LOGGER.info("Gambling initialized.");
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(ProfileSyncS2C.ID, ProfileSyncS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenCasinoS2C.ID, OpenCasinoS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GameStateSyncS2C.ID, GameStateSyncS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BetRequestC2S.ID, BetRequestC2S.CODEC);
    }

    private static void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                GamblingServerState.start(server, FabricLoader.getInstance().getConfigDir()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GamblingServerState.stop());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GamblingServerState state = GamblingServerState.get();
            if (state != null) {
                state.onPlayerJoin(handler.player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GamblingServerState state = GamblingServerState.get();
            if (state != null) {
                state.onPlayerQuit(handler.player);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(BetRequestC2S.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            GamblingServerState state = GamblingServerState.get();
            if (state == null) {
                return;
            }
            long amount = payload.amount();
            if (!Validation.isValidBet(amount)) {
                state.replyResult(player, GameSessionManager.Result.error("key.gambling.error.invalid_bet"));
                return;
            }
            GameSessionManager.Result result = state.sessions().commitBet(player.getUUID(), amount);
            state.replyResult(player, result);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GamblingServerState state = GamblingServerState.get();
            int intervalTicks = GamblingConfig.get().autosaveSeconds() * TICKS_PER_SECOND;
            if (state != null && state.economy().isDirty()
                    && server.getTickCount() % intervalTicks == 0) {
                state.saveProfilesIfSafe();
            }
        });
    }
}
