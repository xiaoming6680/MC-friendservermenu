package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record RequestMenuDataPayload(int requestId) implements CustomPayload {
    public static final Id<RequestMenuDataPayload> ID = new Id<>(FriendServerMenuMod.id("request_menu_data"));
    public static final PacketCodec<RegistryByteBuf, RequestMenuDataPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RequestMenuDataPayload::requestId,
            RequestMenuDataPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
