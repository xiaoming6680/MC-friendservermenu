package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record RequestOpenMenuPayload(int requestId) implements CustomPayload {
    public static final Id<RequestOpenMenuPayload> ID = new Id<>(FriendServerMenuMod.id("request_open_menu"));
    public static final PacketCodec<RegistryByteBuf, RequestOpenMenuPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RequestOpenMenuPayload::requestId,
            RequestOpenMenuPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
