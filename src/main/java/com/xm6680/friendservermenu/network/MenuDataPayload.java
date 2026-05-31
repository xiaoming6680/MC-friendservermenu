package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record MenuDataPayload(String menuTitle, boolean canUseAdmin, String locationsJson, String activeActivityJson) implements CustomPayload {
    public static final Id<MenuDataPayload> ID = new Id<>(FriendServerMenuMod.id("menu_data"));
    public static final PacketCodec<RegistryByteBuf, MenuDataPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, MenuDataPayload::menuTitle,
            PacketCodecs.BOOLEAN, MenuDataPayload::canUseAdmin,
            PacketCodecs.string(32767), MenuDataPayload::locationsJson,
            PacketCodecs.string(4096), MenuDataPayload::activeActivityJson,
            MenuDataPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
