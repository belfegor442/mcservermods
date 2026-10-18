package com.gambling.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ProfileSyncS2C(String playerName, long chips, int level, long xp, long totalWagered)
        implements CustomPayload {
    public static final CustomPayload.Id<ProfileSyncS2C> ID =
            new CustomPayload.Id<>(Identifier.of("gambling", "profile_sync"));

    public static final PacketCodec<PacketByteBuf, ProfileSyncS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, ProfileSyncS2C::playerName,
                    PacketCodecs.VAR_LONG, ProfileSyncS2C::chips,
                    PacketCodecs.VAR_INT, ProfileSyncS2C::level,
                    PacketCodecs.VAR_LONG, ProfileSyncS2C::xp,
                    PacketCodecs.VAR_LONG, ProfileSyncS2C::totalWagered,
                    ProfileSyncS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
