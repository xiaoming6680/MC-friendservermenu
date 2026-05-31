package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ServerStatusPayload(String statusJson) implements CustomPayload {
    public static final Id<ServerStatusPayload> ID = new Id<>(FriendServerMenuMod.id("server_status"));
    public static final PacketCodec<RegistryByteBuf, ServerStatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32767), ServerStatusPayload::statusJson,
            ServerStatusPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
