package com.xm6680.friendservermenu.client;

import com.xm6680.friendservermenu.client.gui.FriendMenuScreen;
import com.xm6680.friendservermenu.network.ClientUiCapabilitiesPayload;
import com.xm6680.friendservermenu.network.LocationMutationResultPayload;
import com.xm6680.friendservermenu.network.MenuDataPayload;
import com.xm6680.friendservermenu.network.OpenMenuPayload;
import com.xm6680.friendservermenu.network.PlayerSettingsPayload;
import com.xm6680.friendservermenu.network.ServerDrivenUiPayload;
import com.xm6680.friendservermenu.network.ServerFeatureSettingsPayload;
import com.xm6680.friendservermenu.network.ServerStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class ClientNetworking {
    private static final int UI_PROTOCOL_VERSION = 1;
    private static final String UI_CAPABILITIES = "server_driven_ui,cards,buttons,toggles,columns,legacy_actions";
    private static ServerDrivenUiPayload latestServerDrivenUi;

    private ClientNetworking() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(ClientNetworking::sendUiCapabilities));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latestServerDrivenUi = null);

        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    sendUiCapabilities();
                    FriendMenuScreen screen = new FriendMenuScreen(payload.menuTitle(), payload.canUseAdmin(), payload.initialPage());
                    if (latestServerDrivenUi != null) {
                        screen.applyServerDrivenUi(latestServerDrivenUi);
                    }
                    MinecraftClient.getInstance().setScreen(screen);
                }));

        ClientPlayNetworking.registerGlobalReceiver(MenuDataPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientTaskHud.applyTasksJson(payload.tasksJson());
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

        ClientPlayNetworking.registerGlobalReceiver(PlayerSettingsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyPlayerSettings(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ServerFeatureSettingsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyServerFeatureSettings(payload);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ServerDrivenUiPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    latestServerDrivenUi = payload;
                    if (MinecraftClient.getInstance().currentScreen instanceof FriendMenuScreen screen) {
                        screen.applyServerDrivenUi(payload);
                    }
                }));
    }

    private static void sendUiCapabilities() {
        if (ClientPlayNetworking.canSend(ClientUiCapabilitiesPayload.ID)) {
            ClientPlayNetworking.send(new ClientUiCapabilitiesPayload(UI_PROTOCOL_VERSION, UI_CAPABILITIES));
        }
    }
}
