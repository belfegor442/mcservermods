package com.gambling.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ProfileSyncS2C(String playerName, long chips, int level, long xp, long totalWagered)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProfileSyncS2C> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("gambling", "profile_sync"));

    public static final StreamCodec<FriendlyByteBuf, ProfileSyncS2C> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ProfileSyncS2C::playerName,
                    ByteBufCodecs.VAR_LONG, ProfileSyncS2C::chips,
                    ByteBufCodecs.VAR_INT, ProfileSyncS2C::level,
                    ByteBufCodecs.VAR_LONG, ProfileSyncS2C::xp,
                    ByteBufCodecs.VAR_LONG, ProfileSyncS2C::totalWagered,
                    ProfileSyncS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
