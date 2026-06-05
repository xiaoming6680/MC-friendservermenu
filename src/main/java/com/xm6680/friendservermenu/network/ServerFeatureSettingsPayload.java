package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ServerFeatureSettingsPayload(boolean deathPointEnabled, boolean deathPointChatEnabled) implements CustomPayload {
    public static final Id<ServerFeatureSettingsPayload> ID = new Id<>(FriendServerMenuMod.id("server_feature_settings"));
    public static final PacketCodec<RegistryByteBuf, ServerFeatureSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, ServerFeatureSettingsPayload::deathPointEnabled,
            PacketCodecs.BOOLEAN, ServerFeatureSettingsPayload::deathPointChatEnabled,
            ServerFeatureSettingsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
