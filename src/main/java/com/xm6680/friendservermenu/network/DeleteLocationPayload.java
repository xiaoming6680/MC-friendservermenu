package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DeleteLocationPayload(String locationId) implements CustomPayload {
    public static final Id<DeleteLocationPayload> ID = new Id<>(FriendServerMenuMod.id("delete_location"));
    public static final PacketCodec<RegistryByteBuf, DeleteLocationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(128), DeleteLocationPayload::locationId,
            DeleteLocationPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
