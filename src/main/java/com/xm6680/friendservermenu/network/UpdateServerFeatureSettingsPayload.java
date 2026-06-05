package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record UpdateServerFeatureSettingsPayload(String key, boolean value) implements CustomPayload {
    public static final Id<UpdateServerFeatureSettingsPayload> ID = new Id<>(FriendServerMenuMod.id("update_server_feature_settings"));
    public static final PacketCodec<RegistryByteBuf, UpdateServerFeatureSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), UpdateServerFeatureSettingsPayload::key,
            PacketCodecs.BOOLEAN, UpdateServerFeatureSettingsPayload::value,
            UpdateServerFeatureSettingsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
