package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ServerDrivenUiPayload(int protocolVersion, String uiJson) implements CustomPayload {
    public static final Id<ServerDrivenUiPayload> ID = new Id<>(FriendServerMenuMod.id("server_driven_ui"));
    public static final PacketCodec<RegistryByteBuf, ServerDrivenUiPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ServerDrivenUiPayload::protocolVersion,
            PacketCodecs.string(65535), ServerDrivenUiPayload::uiJson,
            ServerDrivenUiPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
