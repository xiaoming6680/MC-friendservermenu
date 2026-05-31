package com.xm6680.friendservermenu.client;

import com.xm6680.friendservermenu.client.gui.FriendMenuScreen;
import com.xm6680.friendservermenu.network.LocationMutationResultPayload;
import com.xm6680.friendservermenu.network.MenuDataPayload;
import com.xm6680.friendservermenu.network.OpenMenuPayload;
import com.xm6680.friendservermenu.network.ServerStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    FriendMenuScreen screen = new FriendMenuScreen(payload.menuTitle(), payload.canUseAdmin(), payload.initialPage());
                    MinecraftClient.getInstance().setScreen(screen);
                }));

        ClientPlayNetworking.registerGlobalReceiver(MenuDataPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyMenuData(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ServerStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyStatus(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(LocationMutationResultPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyLocationMutationResult(payload);
                    }
                }));
    }
}
