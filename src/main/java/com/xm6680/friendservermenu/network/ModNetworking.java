package com.xm6680.friendservermenu.network;

import com.xm6680.friendservermenu.config.ModConfig;
import com.xm6680.friendservermenu.config.ModConfigManager;
import com.xm6680.friendservermenu.server.ActivityManager;
import com.xm6680.friendservermenu.server.ServerActionHandler;
import com.xm6680.friendservermenu.server.StatusManager;
import com.xm6680.friendservermenu.server.TaskManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(OpenMenuPayload.ID, OpenMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MenuDataPayload.ID, MenuDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerStatusPayload.ID, ServerStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LocationMutationResultPayload.ID, LocationMutationResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MenuActionPayload.ID, MenuActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMenuDataPayload.ID, RequestMenuDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AddLocationPayload.ID, AddLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditLocationPayload.ID, EditLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteLocationPayload.ID, DeleteLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActivityTemplatePayload.ID, ActivityTemplatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActivityTeleportPayload.ID, ActivityTeleportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TaskActionPayload.ID, TaskActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MenuActionPayload.ID, (payload, context) ->
                context.server().execute(() -> ServerActionHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RequestMenuDataPayload.ID, (payload, context) ->
                context.server().execute(() -> sendLiveData(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(AddLocationPayload.ID, (payload, context) ->
                context.server().execute(() -> ServerActionHandler.handleAddLocation(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(EditLocationPayload.ID, (payload, context) ->
                context.server().execute(() -> ServerActionHandler.handleEditLocation(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DeleteLocationPayload.ID, (payload, context) ->
                context.server().execute(() -> ServerActionHandler.handleDeleteLocation(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ActivityTemplatePayload.ID, (payload, context) ->
                context.server().execute(() -> ActivityManager.submitActivity(context.player(), payload.activityJson())));
        ServerPlayNetworking.registerGlobalReceiver(ActivityTeleportPayload.ID, (payload, context) ->
                context.server().execute(() -> ActivityManager.teleportToActiveActivity(context.player(), payload.activityId())));
        ServerPlayNetworking.registerGlobalReceiver(TaskActionPayload.ID, (payload, context) ->
                context.server().execute(() -> TaskManager.handleTaskAction(context.player(), payload)));
    }

    public static void sendMenu(ServerPlayerEntity player, String initialPage) {
        ModConfig config = ModConfigManager.get();
        boolean canUseAdmin = canUseAdmin(player);

        ServerPlayNetworking.send(player, new OpenMenuPayload(config.menuTitle, canUseAdmin, initialPage));
        sendLiveData(player);
    }

    public static void sendLiveData(ServerPlayerEntity player) {
        ModConfig config = ModConfigManager.get();
        ServerPlayNetworking.send(player, new MenuDataPayload(
                config.menuTitle,
                canUseAdmin(player),
                ModConfigManager.locationsJson(),
                ActivityManager.activeActivityJson(player),
                TaskManager.visibleTasksJson(player)
        ));
        sendStatus(player);
    }

    public static void sendStatus(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new ServerStatusPayload(StatusManager.createStatusJson(player)));
    }

    public static void sendLocationMutationResult(ServerPlayerEntity player, boolean success, String message) {
        ServerPlayNetworking.send(player, new LocationMutationResultPayload(success, message == null ? "" : message));
    }

    public static void broadcastMenuData(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendLiveData(player);
        }
    }

    public static boolean canUseAdmin(ServerPlayerEntity player) {
        return player.getCommandSource().getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
    }
}
