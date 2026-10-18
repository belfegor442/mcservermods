package com.gambling.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GameStateSyncS2C(boolean hasSession, String gameType, long bet, String state, String result)
        implements CustomPayload {
    public static final CustomPayload.Id<GameStateSyncS2C> ID =
            new CustomPayload.Id<>(Identifier.of("gambling", "game_state_sync"));

    public static final PacketCodec<PacketByteBuf, GameStateSyncS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BOOL, GameStateSyncS2C::hasSession,
                    PacketCodecs.STRING, GameStateSyncS2C::gameType,
                    PacketCodecs.VAR_LONG, GameStateSyncS2C::bet,
                    PacketCodecs.STRING, GameStateSyncS2C::state,
                    PacketCodecs.STRING, GameStateSyncS2C::result,
                    GameStateSyncS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
