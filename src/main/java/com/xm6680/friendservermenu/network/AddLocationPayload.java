package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record AddLocationPayload(String locationJson) implements CustomPayload {
    public static final Id<AddLocationPayload> ID = new Id<>(FriendServerMenuMod.id("add_location"));
    public static final PacketCodec<RegistryByteBuf, AddLocationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(4096), AddLocationPayload::locationJson,
            AddLocationPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
