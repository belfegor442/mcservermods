package com.gambling.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BetRequestC2S(long amount) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BetRequestC2S> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("gambling", "bet_request"));

    public static final StreamCodec<FriendlyByteBuf, BetRequestC2S> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BetRequestC2S::amount,
                    BetRequestC2S::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
