package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record TaskActionPayload(String actionId, String taskId, String taskJson) implements CustomPayload {
    public static final Id<TaskActionPayload> ID = new Id<>(FriendServerMenuMod.id("task_action"));
    public static final PacketCodec<RegistryByteBuf, TaskActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, TaskActionPayload::actionId,
            PacketCodecs.STRING, TaskActionPayload::taskId,
            PacketCodecs.string(8192), TaskActionPayload::taskJson,
            TaskActionPayload::new
    );

    public static TaskActionPayload simple(String actionId, String taskId) {
        return new TaskActionPayload(actionId, taskId, "");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
