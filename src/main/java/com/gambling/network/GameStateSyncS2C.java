package com.gambling.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GameStateSyncS2C(boolean hasSession, String gameType, long bet, String state, String result)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GameStateSyncS2C> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("gambling", "game_state_sync"));

    public static final StreamCodec<FriendlyByteBuf, GameStateSyncS2C> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, GameStateSyncS2C::hasSession,
                    ByteBufCodecs.STRING_UTF8, GameStateSyncS2C::gameType,
                    ByteBufCodecs.VAR_LONG, GameStateSyncS2C::bet,
                    ByteBufCodecs.STRING_UTF8, GameStateSyncS2C::state,
                    ByteBufCodecs.STRING_UTF8, GameStateSyncS2C::result,
                    GameStateSyncS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
