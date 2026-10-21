package com.gambling.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenCasinoS2C() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenCasinoS2C> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("gambling", "open_casino"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, OpenCasinoS2C> CODEC =
            StreamCodec.unit(new OpenCasinoS2C());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
