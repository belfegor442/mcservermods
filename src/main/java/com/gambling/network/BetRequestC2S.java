package com.gambling.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BetRequestC2S(long amount) implements CustomPayload {
    public static final CustomPayload.Id<BetRequestC2S> ID =
            new CustomPayload.Id<>(Identifier.of("gambling", "bet_request"));

    public static final PacketCodec<PacketByteBuf, BetRequestC2S> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_LONG, BetRequestC2S::amount,
                    BetRequestC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
