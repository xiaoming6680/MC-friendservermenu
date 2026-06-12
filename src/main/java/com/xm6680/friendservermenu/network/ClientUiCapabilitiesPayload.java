package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ClientUiCapabilitiesPayload(int protocolVersion, String capabilitiesCsv) implements CustomPayload {
    public static final Id<ClientUiCapabilitiesPayload> ID = new Id<>(FriendServerMenuMod.id("client_ui_capabilities"));
    public static final PacketCodec<RegistryByteBuf, ClientUiCapabilitiesPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ClientUiCapabilitiesPayload::protocolVersion,
            PacketCodecs.string(1024), ClientUiCapabilitiesPayload::capabilitiesCsv,
            ClientUiCapabilitiesPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
