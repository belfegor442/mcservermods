package com.gambling.client;

import com.gambling.client.hud.GamblingHudRenderer;
import com.gambling.network.GameStateSyncS2C;
import com.gambling.network.ProfileSyncS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class GamblingClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ProfileSyncS2C.ID, (payload, context) -> {
            ClientGamblingState state = ClientGamblingState.get();
            state.updateProfile(payload.playerName(), payload.chips(), payload.level(),
                    payload.xp(), payload.totalWagered());
        });

        ClientPlayNetworking.registerGlobalReceiver(GameStateSyncS2C.ID, (payload, context) -> {
            ClientGamblingState state = ClientGamblingState.get();
            state.updateGame(payload.hasSession(), payload.gameType(), payload.bet(),
                    payload.state(), payload.result());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientGamblingState.get().clear());

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("gambling", "hud"),
                (extractor, tickCounter) -> GamblingHudRenderer.render(extractor));
    }
}
