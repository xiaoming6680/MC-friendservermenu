package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record LocationMutationResultPayload(boolean success, String message) implements CustomPayload {
    public static final Id<LocationMutationResultPayload> ID = new Id<>(FriendServerMenuMod.id("location_mutation_result"));
    public static final PacketCodec<RegistryByteBuf, LocationMutationResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, LocationMutationResultPayload::success,
            PacketCodecs.string(512), LocationMutationResultPayload::message,
            LocationMutationResultPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
