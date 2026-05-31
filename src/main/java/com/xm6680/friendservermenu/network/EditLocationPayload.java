package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record EditLocationPayload(String originalId, String locationJson) implements CustomPayload {
    public static final Id<EditLocationPayload> ID = new Id<>(FriendServerMenuMod.id("edit_location"));
    public static final PacketCodec<RegistryByteBuf, EditLocationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(128), EditLocationPayload::originalId,
            PacketCodecs.string(4096), EditLocationPayload::locationJson,
            EditLocationPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
