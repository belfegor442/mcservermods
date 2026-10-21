package com.gambling.client;

import com.gambling.client.hud.GamblingHudRenderer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
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

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen && client.player != null) {
                screen.addRenderableWidget(Button.builder(
                                net.minecraft.network.chat.Component.translatable("key.gambling.menu.open"),
                                button -> client.setScreen(new CasinoScreen()))
                        .bounds(Math.max(4, scaledWidth - 116), Math.max(4, scaledHeight - 28),
                                112, 20)
                        .build());
            }
        });
    }
}
