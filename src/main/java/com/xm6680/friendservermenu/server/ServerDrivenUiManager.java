package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.config.LocationEntry;
import com.xm6680.friendservermenu.config.ModConfig;
import com.xm6680.friendservermenu.config.ModConfigManager;
import com.xm6680.friendservermenu.network.ClientUiCapabilitiesPayload;
import com.xm6680.friendservermenu.network.ServerDrivenUiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerDrivenUiManager {
    public static final int PROTOCOL_VERSION = 1;
    private static final String REQUIRED_CAPABILITY = "server_driven_ui";
    private static final Map<UUID, ClientUiProfile> CLIENTS = new HashMap<>();

    private ServerDrivenUiManager() {
    }

    public static void updateCapabilities(ServerPlayerEntity player, ClientUiCapabilitiesPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        Set<String> capabilities = parseCapabilities(payload.capabilitiesCsv());
        CLIENTS.put(player.getUuid(), new ClientUiProfile(payload.protocolVersion(), capabilities));
        sendUiDefinition(player);
    }

    public static void remove(ServerPlayerEntity player) {
        if (player != null) {
            CLIENTS.remove(player.getUuid());
        }
    }

    public static void sendUiDefinition(ServerPlayerEntity player) {
        if (!supportsServerDrivenUi(player)) {
            return;
        }
        ServerPlayNetworking.send(player, new ServerDrivenUiPayload(PROTOCOL_VERSION, FriendServerMenuMod.GSON.toJson(createDefinition(player))));
    }

    public static boolean supportsServerDrivenUi(ServerPlayerEntity player) {
        ClientUiProfile profile = player == null ? null : CLIENTS.get(player.getUuid());
        return profile != null && profile.protocolVersion >= PROTOCOL_VERSION && profile.capabilities.contains(REQUIRED_CAPABILITY);
    }

    private static ServerUiDefinition createDefinition(ServerPlayerEntity player) {
        ServerUiDefinition definition = new ServerUiDefinition();
        definition.protocolVersion = PROTOCOL_VERSION;
        definition.revision = System.currentTimeMillis();

        ModConfig config = ModConfigManager.get();
        StatusManager.ServerStatus status = FriendServerMenuMod.GSON.fromJson(StatusManager.createStatusJson(player), StatusManager.ServerStatus.class);
        boolean canUseAdmin = ModNetworkingAccess.canUseAdmin(player);

        definition.pages.add(createTeleportPage(config, player, canUseAdmin));
        definition.pages.add(createCoordinatePage(status));
        definition.pages.add(createStatusPage(status));
        definition.pages.add(createSettingsPage(player));
        if (canUseAdmin) {
            definition.pages.add(createAdminPage(status));
        }
        return definition;
    }

    private static UiPage createTeleportPage(ModConfig config, ServerPlayerEntity player, boolean canUseAdmin) {
        UiPage page = page("teleport", "传送", "管理公共传送点和快速传送入口。", false);
        UiCard summary = card("公共传送点");
        summary.lines.add("已保存：" + config.locations.size() + " 个");
        summary.lines.add("创建后所有玩家都可以看到。");
        summary.buttons.add(button("新增传送点", "", "open_add_location", "", true, "normal", 104, 24));
        page.cards.add(summary);

        if (config.locations.isEmpty()) {
            UiCard empty = card("暂无传送点");
            empty.lines.add("可以点击上方按钮创建第一个公共传送点。");
            page.cards.add(empty);
            return page;
        }

        for (LocationEntry location : config.locations) {
            UiCard locationCard = card(textOr(location.name, "未命名传送点"));
            locationCard.lines.add(dimensionName(location.world) + "  X:" + format(location.x) + " Y:" + format(location.y) + " Z:" + format(location.z));
            if (!safe(location.description).isBlank()) {
                locationCard.lines.add(location.description);
            }
            locationCard.buttons.add(button("传送", "", "teleport_location", safe(location.id), false, "normal", 72, 24));
            locationCard.buttons.add(button("编辑", "", "open_edit_location", safe(location.id), true, "normal", 58, 24));
            if (canUseAdmin || player.getUuidAsString().equals(safe(location.creatorUuid))) {
                locationCard.buttons.add(button("删除", "", "delete_location", safe(location.id), true, "danger", 58, 24));
            }
            page.cards.add(locationCard);
        }
        return page;
    }

    private static UiPage createCoordinatePage(StatusManager.ServerStatus status) {
        UiPage page = page("coordinates", "坐标", "复制坐标、分享位置和查看死亡点。", false);
        UiCard current = card("当前位置");
        current.lines.add("当前维度：" + textOr(status.dimension, "未知"));
        current.lines.add("当前坐标：X:" + status.x + " Y:" + status.y + " Z:" + status.z);
        current.buttons.add(button("复制坐标", "", "copy_coords", "", true, "normal", 82, 24));
        current.buttons.add(button("公开坐标", "", "send_coords_public", "", false, "normal", 82, 24));
        current.buttons.add(button("私发坐标", "", "send_coords_private", "", false, "normal", 82, 24));
        page.cards.add(current);

        UiCard hud = card("HUD 设置");
        hud.lines.add("打开后会在屏幕上显示维度和坐标。");
        hud.buttons.add(button("坐标 HUD", "", "coordinate_hud_toggle", "", true, "normal", 88, 24));
        hud.buttons.add(button("编辑位置", "", "coordinate_hud_edit", "", true, "normal", 88, 24));
        page.cards.add(hud);

        if (status.deathPoint != null) {
            UiCard deathPoint = card("死亡点");
            deathPoint.lines.add("X:" + status.deathPoint.x + " Y:" + status.deathPoint.y + " Z:" + status.deathPoint.z);
            deathPoint.buttons.add(button("传送", "", "teleport_death_point", "", false, "normal", 72, 24));
            deathPoint.buttons.add(button("删除", "", "delete_death_point", "", false, "danger", 72, 24));
            page.cards.add(deathPoint);
        }
        return page;
    }

    private static UiPage createStatusPage(StatusManager.ServerStatus status) {
        UiPage page = page("status", "状态", "查看在线人数、世界信息和服务器性能。", true);
        UiCard server = card("服务器概况");
        server.lines.add("在线玩家：" + status.onlinePlayers + " / " + status.maxPlayers);
        server.lines.add("TPS：" + String.format(Locale.ROOT, "%.2f", status.tps));
        server.lines.add("MSPT：" + String.format(Locale.ROOT, "%.2f", status.mspt));
        page.cards.add(server);

        UiCard world = card("世界信息");
        world.lines.add("当前维度：" + textOr(status.dimension, "未知"));
        world.lines.add("当前坐标：X:" + status.x + " Y:" + status.y + " Z:" + status.z);
        world.lines.add("世界时间：" + minecraftTime(status.timeOfDay));
        world.lines.add("天气：" + textOr(status.weather, "暂无数据"));
        page.cards.add(world);
        return page;
    }

    private static UiPage createSettingsPage(ServerPlayerEntity player) {
        UiPage page = page("settings", "设置", "调整个人设置和客户端 HUD 开关。", true);
        PlayerSettingsManager.PlayerSettings playerSettings = PlayerSettingsManager.settings(player);
        UiCard personal = card("个人设置");
        personal.buttons.add(button("自动领取：" + (playerSettings.autoClaimActivityItems ? "开" : "关"), "",
                "setting_auto_claim_activity_items", "", true, playerSettings.autoClaimActivityItems ? "toggle_on" : "toggle_off", 128, 24));
        page.cards.add(personal);

        UiCard hud = card("HUD 开关");
        hud.buttons.add(button("坐标 HUD", "", "coordinate_hud_toggle", "", true, "normal", 88, 24));
        hud.buttons.add(button("任务 HUD", "", "task_hud_toggle", "", true, "normal", 88, 24));
        page.cards.add(hud);
        return page;
    }

    private static UiPage createAdminPage(StatusManager.ServerStatus status) {
        UiPage page = page("admin", "服主管理", "OP 服务器管理、活动控制和维护工具。", true);
        ServerFeatureSettingsManager.ServerFeatureSettings settings = ServerFeatureSettingsManager.settings();

        UiCard server = card("服务器状态");
        server.lines.add("在线人数：" + status.onlinePlayers + " / " + status.maxPlayers);
        server.lines.add("当前维度：" + textOr(status.dimension, "未知"));
        page.cards.add(server);

        UiCard features = card("服务器功能开关");
        features.buttons.add(button("死亡点：" + (settings.deathPointEnabled ? "开" : "关"), "",
                "server_feature_death_point_enabled", "", true, settings.deathPointEnabled ? "toggle_on" : "toggle_off", 116, 24));
        features.buttons.add(button("死亡点提示：" + (settings.deathPointChatEnabled ? "开" : "关"), "",
                "server_feature_death_point_chat_enabled", "", true, settings.deathPointChatEnabled ? "toggle_on" : "toggle_off", 136, 24));
        page.cards.add(features);

        UiCard activity = card("活动管理");
        activity.buttons.add(button("发布发物品活动", "", "admin_publish_item_activity", "", true, "normal", 124, 24));
        activity.buttons.add(button("发布通知活动", "", "admin_publish_notice_activity", "", true, "normal", 124, 24));
        activity.buttons.add(button("查看活动状态", "", "admin_view_activity", "", true, "normal", 112, 24));
        page.cards.add(activity);

        UiCard ops = card("OP 操作");
        ops.buttons.add(button("时间管理", "白天、正午、夜晚、午夜", "admin_page", "admin_time", true, "normal", 112, 40));
        ops.buttons.add(button("天气管理", "晴天、下雨、雷暴", "admin_page", "admin_weather", true, "normal", 112, 40));
        ops.buttons.add(button("玩家管理", "模式、飞行、状态、踢出和权限", "admin_page", "admin_players", true, "normal", 112, 40));
        ops.buttons.add(button("清理管理", "清理掉落物、经验球、箭矢和怪物", "admin_page", "admin_cleanup", true, "normal", 112, 40));
        ops.buttons.add(button("重载配置", "重新读取 friendservermenu.json", "admin_reload_config", "", false, "normal", 142, 40));
        page.cards.add(ops);
        return page;
    }

    private static UiPage page(String id, String label, String description, boolean allowColumns) {
        UiPage page = new UiPage();
        page.id = id;
        page.label = label;
        page.description = description;
        page.allowColumns = allowColumns;
        return page;
    }

    private static UiCard card(String title) {
        UiCard card = new UiCard();
        card.title = title;
        return card;
    }

    private static UiButton button(String label, String description, String actionId, String argument, boolean localOnly, String variant, int width, int height) {
        UiButton button = new UiButton();
        button.label = label;
        button.description = description;
        button.actionId = actionId;
        button.argument = argument;
        button.localOnly = localOnly;
        button.variant = variant;
        button.width = width;
        button.height = height;
        return button;
    }

    private static Set<String> parseCapabilities(String capabilitiesCsv) {
        Set<String> capabilities = new HashSet<>();
        for (String capability : safe(capabilitiesCsv).split(",")) {
            String normalized = capability.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                capabilities.add(normalized);
            }
        }
        return capabilities;
    }

    private static String dimensionName(String world) {
        return StatusManager.formatDimensionId(safe(world));
    }

    private static String minecraftTime(long timeOfDay) {
        long ticks = Math.floorMod(timeOfDay + 6000L, 24000L);
        int hours = (int) (ticks / 1000L);
        int minutes = (int) ((ticks % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String textOr(String value, String fallback) {
        String text = safe(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record ClientUiProfile(int protocolVersion, Set<String> capabilities) {
    }

    public static class ServerUiDefinition {
        public int protocolVersion;
        public long revision;
        public List<UiPage> pages = new ArrayList<>();
    }

    public static class UiPage {
        public String id;
        public String label;
        public String description;
        public boolean allowColumns;
        public List<UiCard> cards = new ArrayList<>();
    }

    public static class UiCard {
        public String title;
        public List<String> lines = new ArrayList<>();
        public List<UiButton> buttons = new ArrayList<>();
    }

    public static class UiButton {
        public String label;
        public String description;
        public String actionId;
        public String argument;
        public boolean localOnly;
        public String variant;
        public int width;
        public int height;
    }

    private static final class ModNetworkingAccess {
        private static boolean canUseAdmin(ServerPlayerEntity player) {
            return com.xm6680.friendservermenu.network.ModNetworking.canUseAdmin(player);
        }
    }
}
