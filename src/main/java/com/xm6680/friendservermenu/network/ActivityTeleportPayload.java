package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ActivityTeleportPayload(String activityId) implements CustomPayload {
    public static final Id<ActivityTeleportPayload> ID = new Id<>(FriendServerMenuMod.id("activity_teleport"));
    public static final PacketCodec<RegistryByteBuf, ActivityTeleportPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(128), ActivityTeleportPayload::activityId,
            ActivityTeleportPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
