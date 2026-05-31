package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ActivityTemplatePayload(String activityJson) implements CustomPayload {
    public static final Id<ActivityTemplatePayload> ID = new Id<>(FriendServerMenuMod.id("activity_template"));
    public static final PacketCodec<RegistryByteBuf, ActivityTemplatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(4096), ActivityTemplatePayload::activityJson,
            ActivityTemplatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
