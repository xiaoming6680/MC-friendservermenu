package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record MenuActionPayload(String actionId, String argument) implements CustomPayload {
    public static final Id<MenuActionPayload> ID = new Id<>(FriendServerMenuMod.id("menu_action"));
    public static final PacketCodec<RegistryByteBuf, MenuActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, MenuActionPayload::actionId,
            PacketCodecs.string(1024), MenuActionPayload::argument,
            MenuActionPayload::new
    );

    public static MenuActionPayload simple(String actionId) {
        return new MenuActionPayload(actionId, "");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
