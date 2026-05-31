package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.config.LocationEntry;
import com.xm6680.friendservermenu.config.ModConfigManager;
import com.xm6680.friendservermenu.network.AddLocationPayload;
import com.xm6680.friendservermenu.network.DeleteLocationPayload;
import com.xm6680.friendservermenu.network.EditLocationPayload;
import com.xm6680.friendservermenu.network.MenuActionPayload;
import com.xm6680.friendservermenu.network.ModNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class ServerActionHandler {
    private ServerActionHandler() {
    }

    public static void handle(ServerPlayerEntity player, MenuActionPayload payload) {
        String action = safe(payload.actionId());
        String argument = safe(payload.argument());

        if (action.startsWith("admin_") && !ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("你没有权限执行服主管理操作。"), false);
            return;
        }

        switch (action) {
            case "teleport_location" -> TeleportManager.teleportToLocation(player, argument);
            case "send_coords_public" -> StatusManager.broadcastCoordinates(player);
            case "send_coords_private" -> StatusManager.sendCoordinatesToSelf(player);
            case "activity_request" -> player.sendMessage(Text.literal("活动组织功能已改为 OP 模板。"), false);
            case "activity_end" -> ActivityManager.endActivity(player, argument);
            case "activity_claim_item" -> ActivityManager.claimItem(player, argument);
            case "admin_day" -> AdminActionManager.setDay(player);
            case "admin_noon" -> AdminActionManager.setNoon(player);
            case "admin_night" -> AdminActionManager.setNight(player);
            case "admin_midnight" -> AdminActionManager.setMidnight(player);
            case "admin_clear_weather" -> AdminActionManager.clearWeather(player);
            case "admin_rain" -> AdminActionManager.setRain(player);
            case "admin_thunder" -> AdminActionManager.setThunder(player);
            case "admin_tp_all_to_me" -> AdminActionManager.teleportAllToPlayer(player);
            case "admin_heal_all" -> AdminActionManager.healAllPlayers(player);
            case "admin_feed_all" -> AdminActionManager.feedAllPlayers(player);
            case "admin_player_gamemode" -> AdminActionManager.setPlayerGameMode(player, argument);
            case "admin_player_flight" -> AdminActionManager.setPlayerFlight(player, argument);
            case "admin_player_tp_to_me" -> AdminActionManager.teleportPlayerToActor(player, argument);
            case "admin_player_tp_me_to" -> AdminActionManager.teleportActorToPlayer(player, argument);
            case "admin_player_heal" -> AdminActionManager.healPlayer(player, argument);
            case "admin_player_feed" -> AdminActionManager.feedPlayer(player, argument);
            case "admin_player_clear_effects" -> AdminActionManager.clearPlayerEffects(player, argument);
            case "admin_player_extinguish" -> AdminActionManager.extinguishPlayer(player, argument);
            case "admin_player_kick" -> AdminActionManager.kickPlayer(player, argument);
            case "admin_player_ban" -> AdminActionManager.banPlayer(player, argument);
            case "admin_player_op" -> AdminActionManager.setPlayerOperator(player, argument, true);
            case "admin_player_deop" -> AdminActionManager.setPlayerOperator(player, argument, false);
            case "admin_clear_items" -> AdminActionManager.clearItems(player);
            case "admin_clear_xp_orbs" -> AdminActionManager.clearXpOrbs(player);
            case "admin_clear_arrows" -> AdminActionManager.clearArrows(player);
            case "admin_clear_hostiles" -> AdminActionManager.clearHostiles(player);
            case "admin_reload_config" -> {
                ModConfigManager.load();
                player.sendMessage(Text.literal("FriendServerMenu 配置已重载。"), false);
                ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
            }
            default -> player.sendMessage(Text.literal("未知菜单操作：" + action), false);
        }

        ModNetworking.sendLiveData(player);
    }

    public static void handleAddLocation(ServerPlayerEntity player, AddLocationPayload payload) {
        LocationEntry requested;
        try {
            requested = FriendServerMenuMod.GSON.fromJson(safe(payload.locationJson()), LocationEntry.class);
        } catch (Exception exception) {
            sendLocationResult(player, false, "传送点数据格式无效。");
            return;
        }

        String worldError = validateWorld(player, requested);
        if (worldError != null) {
            sendLocationResult(player, false, worldError);
            return;
        }

        ModConfigManager.AddLocationResult result = ModConfigManager.addLocation(requested, ModNetworking.canUseAdmin(player));
        if (!result.success()) {
            sendLocationResult(player, false, result.message());
            return;
        }

        sendLocationResult(player, true, "公共传送点已新增：" + result.location().name);
        player.sendMessage(Text.literal("公共传送点已新增：" + result.location().name), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    public static void handleEditLocation(ServerPlayerEntity player, EditLocationPayload payload) {
        LocationEntry requested;
        try {
            requested = FriendServerMenuMod.GSON.fromJson(safe(payload.locationJson()), LocationEntry.class);
        } catch (Exception exception) {
            sendLocationResult(player, false, "传送点数据格式无效。");
            return;
        }

        String worldError = validateWorld(player, requested);
        if (worldError != null) {
            sendLocationResult(player, false, worldError);
            return;
        }

        ModConfigManager.AddLocationResult result = ModConfigManager.editLocation(safe(payload.originalId()), requested, ModNetworking.canUseAdmin(player));
        if (!result.success()) {
            sendLocationResult(player, false, result.message());
            return;
        }

        sendLocationResult(player, true, "公共传送点已更新：" + result.location().name);
        player.sendMessage(Text.literal("公共传送点已更新：" + result.location().name), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    public static void handleDeleteLocation(ServerPlayerEntity player, DeleteLocationPayload payload) {
        if (!ModNetworking.canUseAdmin(player)) {
            sendLocationResult(player, false, "只有 OP 可以删除公共传送点。");
            return;
        }

        ModConfigManager.AddLocationResult result = ModConfigManager.deleteLocation(safe(payload.locationId()));
        if (!result.success()) {
            sendLocationResult(player, false, result.message());
            return;
        }

        sendLocationResult(player, true, "公共传送点已删除：" + result.location().name);
        player.sendMessage(Text.literal("公共传送点已删除：" + result.location().name), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void sendLocationResult(ServerPlayerEntity player, boolean success, String message) {
        ModNetworking.sendLocationMutationResult(player, success, message);
    }

    private static String validateWorld(ServerPlayerEntity player, LocationEntry requested) {
        if (requested == null || requested.world == null || requested.world.isBlank()) {
            return "传送点维度不能为空。";
        }

        Identifier worldId = Identifier.tryParse(requested.world.trim());
        if (worldId == null) {
            return "传送点维度 ID 无效。";
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        if (player.getCommandSource().getServer().getWorld(worldKey) == null) {
            return "服务器未加载维度：" + requested.world;
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
