package com.xm6680.friendservermenu.client;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.network.RequestOpenMenuPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyBindings {
    private static final KeyBinding.Category MENU_CATEGORY = KeyBinding.Category.create(FriendServerMenuMod.id("menu"));
    private static final KeyBinding OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.friendservermenu.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            MENU_CATEGORY
    ));

    private ClientKeyBindings() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientKeyBindings::onEndClientTick);
    }

    private static void onEndClientTick(MinecraftClient client) {
        while (OPEN_MENU_KEY.wasPressed()) {
            if (client.player != null && client.currentScreen == null && ClientPlayNetworking.canSend(RequestOpenMenuPayload.ID)) {
                ClientPlayNetworking.send(new RequestOpenMenuPayload((int) (System.currentTimeMillis() & 0x7FFFFFFF)));
            }
        }
    }
}
