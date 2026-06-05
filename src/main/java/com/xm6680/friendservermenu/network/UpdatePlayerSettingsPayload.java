package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record UpdatePlayerSettingsPayload(String key, boolean value) implements CustomPayload {
    public static final Id<UpdatePlayerSettingsPayload> ID = new Id<>(FriendServerMenuMod.id("update_player_settings"));
    public static final PacketCodec<RegistryByteBuf, UpdatePlayerSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), UpdatePlayerSettingsPayload::key,
            PacketCodecs.BOOLEAN, UpdatePlayerSettingsPayload::value,
            UpdatePlayerSettingsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
