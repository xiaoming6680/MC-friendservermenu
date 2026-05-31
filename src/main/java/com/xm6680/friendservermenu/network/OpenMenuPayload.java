package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record OpenMenuPayload(String menuTitle, boolean canUseAdmin, String initialPage) implements CustomPayload {
    public static final Id<OpenMenuPayload> ID = new Id<>(FriendServerMenuMod.id("open_menu"));
    public static final PacketCodec<RegistryByteBuf, OpenMenuPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenMenuPayload::menuTitle,
            PacketCodecs.BOOLEAN, OpenMenuPayload::canUseAdmin,
            PacketCodecs.STRING, OpenMenuPayload::initialPage,
            OpenMenuPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
