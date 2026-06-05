package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record PlayerSettingsPayload(boolean autoClaimActivityItems) implements CustomPayload {
    public static final Id<PlayerSettingsPayload> ID = new Id<>(FriendServerMenuMod.id("player_settings"));
    public static final PacketCodec<RegistryByteBuf, PlayerSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, PlayerSettingsPayload::autoClaimActivityItems,
            PlayerSettingsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
