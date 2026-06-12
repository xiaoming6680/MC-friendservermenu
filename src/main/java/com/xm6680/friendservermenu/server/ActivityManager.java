package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.network.ModNetworking;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActivityManager {
    private static final DateTimeFormatter END_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int[] MAINTENANCE_REMINDERS = {60, 30, 15, 10, 5, 4, 3, 2, 1};
    private static final int ACTIVITY_ITEM_INVENTORY_SIZE = 27;
    private static final Map<UUID, SimpleInventory> DRAFT_ITEM_INVENTORIES = new LinkedHashMap<>();
    private static ActiveActivity activeActivity;

    private ActivityManager() {
    }

    public static void submitActivity(ServerPlayerEntity player, String activityJson) {
        if (!ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("只有 OP 可以组织活动。"), false);
            return;
        }

        SubmittedActivity submitted;
        try {
            submitted = FriendServerMenuMod.GSON.fromJson(safe(activityJson), SubmittedActivity.class);
        } catch (Exception exception) {
            player.sendMessage(Text.literal("活动数据格式无效。"), false);
            return;
        }

        String category = normalizeCategory(submitted.category);
        SimpleInventory draftItemInventory = "item_give".equals(category) ? draftItemInventory(player) : null;
        String validationError = validate(submitted, draftItemInventory);
        if (validationError != null) {
            player.sendMessage(Text.literal(validationError), false);
            return;
        }

        ServerWorld world = player.getEntityWorld();
        ActiveActivity activity = new ActiveActivity();
        activity.id = UUID.randomUUID().toString();
        activity.initiator = player.getName().getString();
        activity.category = category;
        activity.title = clean(submitted.title, 40);
        activity.description = clean(submitted.description, 160);
        activity.meetingPoint = needsMeetingPoint(activity.category) ? clean(submitted.meetingPoint, 80) : "";
        activity.needsTeleport = needsMeetingPoint(activity.category) && submitted.needsTeleport;
        activity.hasEndDate = needsEndDate(activity.category) && submitted.hasEndDate;
        activity.endDateText = activity.hasEndDate ? clean(submitted.endDateText, 32) : "";
        activity.expiresAtMillis = activity.hasEndDate ? parseEndDate(submitted.endDateText) : 0L;
        activity.itemInventory = new SimpleInventory(ACTIVITY_ITEM_INVENTORY_SIZE);
        if ("item_give".equals(activity.category)) {
            copyInventory(draftItemInventory, activity.itemInventory);
            activity.itemSummary = itemSummary(activity.itemInventory, "活动物品箱");
            activity.itemId = activity.itemSummary;
            activity.itemCount = totalItemCount(activity.itemInventory);
            clearInventory(draftItemInventory);
        } else {
            activity.itemSummary = "";
            activity.itemId = "";
            activity.itemCount = 0;
        }
        activity.maintenanceTimeText = clean(submitted.maintenanceTimeText, 40);
        activity.maintenanceCountdownSeconds = Math.max(5, Math.min(86400, submitted.maintenanceCountdownSeconds));
        activity.maintenanceEndsAtMillis = "maintenance".equals(activity.category)
                ? System.currentTimeMillis() + activity.maintenanceCountdownSeconds * 1000L
                : 0L;
        activity.lastMaintenanceReminderSeconds = Integer.MAX_VALUE;
        activity.world = world.getRegistryKey().getValue().toString();
        activity.x = player.getX();
        activity.y = player.getY();
        activity.z = player.getZ();
        activity.yaw = player.getYaw();
        activity.pitch = player.getPitch();
        activeActivity = activity;

        MinecraftServer server = player.getCommandSource().getServer();
        broadcastActivity(server, activity);
        if ("item_give".equals(activity.category)) {
            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
                tryAutoClaimForPlayer(target);
            }
        }
        ModNetworking.broadcastMenuData(server);
    }

    public static void openDraftItemInventory(ServerPlayerEntity player) {
        if (!ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("只有 OP 可以配置活动发物品箱。"), false);
            return;
        }

        SimpleInventory inventory = draftItemInventory(player);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, user) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory),
                Text.literal("活动发物品箱")
        ));
    }

    public static void tickActivities(MinecraftServer server) {
        ActiveActivity activity = activeActivity;
        if (activity == null || !"maintenance".equals(activity.category) || activity.maintenanceEndsAtMillis <= 0L) {
            return;
        }

        int remainingSeconds = maintenanceRemainingSeconds(activity);
        for (int reminder : MAINTENANCE_REMINDERS) {
            if (remainingSeconds <= reminder && activity.lastMaintenanceReminderSeconds > reminder) {
                activity.lastMaintenanceReminderSeconds = reminder;
                server.getPlayerManager().broadcast(Text.literal("维护倒计时：" + reminder + " 秒后服务器将停止。").formatted(Formatting.RED), false);
                break;
            }
        }

        if (remainingSeconds <= 0) {
            activeActivity = null;
            server.getPlayerManager().broadcast(Text.literal("维护倒计时结束，服务器正在停止。").formatted(Formatting.RED), false);
            server.getCommandManager().parseAndExecute(server.getCommandSource(), "stop");
        }
    }

    public static void teleportToActiveActivity(ServerPlayerEntity player, String activityId) {
        ActiveActivity activity = currentActivity();
        if (activity == null || !activity.id.equals(activityId)) {
            player.sendMessage(Text.literal("当前活动已失效。"), false);
            return;
        }
        if (!activity.needsTeleport) {
            player.sendMessage(Text.literal("当前活动没有开放传送按钮。"), false);
            return;
        }

        Identifier worldId = Identifier.tryParse(activity.world);
        if (worldId == null) {
            player.sendMessage(Text.literal("活动维度无效。"), false);
            return;
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        ServerWorld world = player.getCommandSource().getServer().getWorld(worldKey);
        if (world == null) {
            player.sendMessage(Text.literal("活动所在维度未加载。"), false);
            return;
        }

        player.teleport(world, activity.x, activity.y, activity.z, Set.of(), activity.yaw, activity.pitch, false);
        player.sendMessage(Text.literal("已前往活动集合点：" + activity.title), false);
    }

    public static void endActivity(ServerPlayerEntity player, String activityId) {
        if (!ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("只有 OP 可以结束活动。"), false);
            return;
        }

        ActiveActivity activity = currentActivity();
        if (activity == null || !safe(activity.id).equals(safe(activityId))) {
            player.sendMessage(Text.literal("当前活动已失效。"), false);
            return;
        }

        activeActivity = null;
        MinecraftServer server = player.getCommandSource().getServer();
        server.getPlayerManager().broadcast(Text.literal("活动已结束：" + activity.title).formatted(Formatting.YELLOW), false);
        ModNetworking.broadcastMenuData(server);
    }

    public static void claimItem(ServerPlayerEntity player, String activityId) {
        ActiveActivity activity = currentActivity();
        if (activity == null || !safe(activity.id).equals(safe(activityId))) {
            player.sendMessage(Text.literal("当前活动已失效，无法领取物品。"), false);
            return;
        }
        if (!"item_give".equals(activity.category)) {
            player.sendMessage(Text.literal("当前活动没有可领取物品。"), false);
            return;
        }
        if (activity.itemClaimedPlayers.contains(player.getUuid())) {
            player.sendMessage(Text.literal("你已经领取过本次活动物品。"), false);
            ModNetworking.sendLiveData(player);
            return;
        }

        if (!hasItems(activity.itemInventory)) {
            player.sendMessage(Text.literal("活动物品箱为空，无法领取。"), false);
            return;
        }

        for (int slot = 0; slot < activity.itemInventory.size(); slot++) {
            ItemStack rewardStack = activity.itemInventory.getStack(slot);
            if (rewardStack.isEmpty()) {
                continue;
            }
            ItemStack stack = rewardStack.copy();
            if (!player.giveItemStack(stack) && !stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        }
        activity.itemClaimedPlayers.add(player.getUuid());
        player.sendMessage(Text.literal("已领取活动物品：" + itemSummary(activity.itemInventory, "活动物品箱")), false);
        ModNetworking.sendLiveData(player);
    }

    public static void tryAutoClaimForPlayer(ServerPlayerEntity player) {
        ActiveActivity activity = currentActivity();
        if (player == null || activity == null || !"item_give".equals(activity.category)) {
            return;
        }
        if (!PlayerSettingsManager.autoClaimActivityItems(player)) {
            return;
        }
        claimItem(player, activity.id);
    }

    public static void notifyActiveActivityOnJoin(ServerPlayerEntity player) {
        ActiveActivity activity = currentActivity();
        if (activity == null) {
            return;
        }
        sendActivityNotice(player, activity, false);
    }

    public static String activeActivityJson(ServerPlayerEntity viewer) {
        ActiveActivity activity = currentActivity();
        if (activity != null && "maintenance".equals(activity.category)) {
            activity.maintenanceRemainingSeconds = maintenanceRemainingSeconds(activity);
        }
        if (activity != null && viewer != null && "item_give".equals(activity.category)) {
            activity.itemClaimedByViewer = activity.itemClaimedPlayers.contains(viewer.getUuid());
            activity.itemSummary = itemSummary(activity.itemInventory, activity.itemSummary);
        }
        return activity == null ? "" : FriendServerMenuMod.GSON.toJson(activity);
    }

    private static ActiveActivity currentActivity() {
        if (activeActivity != null && activeActivity.expiresAtMillis > 0L && System.currentTimeMillis() > activeActivity.expiresAtMillis) {
            activeActivity = null;
        }
        return activeActivity;
    }

    private static String validate(SubmittedActivity submitted, SimpleInventory draftItemInventory) {
        if (submitted == null) {
            return "活动数据为空。";
        }
        if (submitted.title == null || submitted.title.trim().isEmpty()) {
            return "活动标题不能为空。";
        }
        if (submitted.description == null || submitted.description.trim().isEmpty()) {
            return "活动说明不能为空。";
        }
        String category = normalizeCategory(submitted.category);
        if (needsMeetingPoint(category) && (submitted.meetingPoint == null || submitted.meetingPoint.trim().isEmpty())) {
            return "集合地点不能为空。";
        }
        if (needsEndDate(category) && submitted.hasEndDate) {
            long endAt = parseEndDate(submitted.endDateText);
            if (endAt <= System.currentTimeMillis()) {
                return "结束日期必须晚于当前时间，格式为 yyyy-MM-dd HH:mm。";
            }
        }
        if ("item_give".equals(category)) {
            if (!hasItems(draftItemInventory)) {
                return "请先打开发物品箱，并放入至少一个要发放的物品。";
            }
        }
        if ("maintenance".equals(category)) {
            if (safe(submitted.maintenanceTimeText).isBlank()) {
                return "维护时间不能为空。";
            }
            if (submitted.maintenanceCountdownSeconds < 5 || submitted.maintenanceCountdownSeconds > 86400) {
                return "维护倒计时需要在 5 到 86400 秒之间。";
            }
        }
        return null;
    }

    private static void broadcastActivity(MinecraftServer server, ActiveActivity activity) {
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            sendActivityNotice(target, activity, true);
        }
    }

    private static void sendActivityNotice(ServerPlayerEntity player, ActiveActivity activity, boolean force) {
        if (!force && !activity.notifiedPlayers.add(player.getUuid())) {
            return;
        }
        if (force) {
            activity.notifiedPlayers.add(player.getUuid());
        }

        player.sendMessage(Text.literal("========== 活动通知 ==========").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("类型：" + categoryLabel(activity.category) + "    发起人：" + activity.initiator).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("标题：" + activity.title).formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("说明：" + activity.description).formatted(Formatting.WHITE), false);
        if (needsMeetingPoint(activity.category)) {
            player.sendMessage(Text.literal("集合地点：" + activity.meetingPoint + " " + coordinates(activity)).formatted(Formatting.GREEN), false);
        }
        if (activity.hasEndDate) {
            player.sendMessage(Text.literal("结束日期：" + activity.endDateText).formatted(Formatting.LIGHT_PURPLE), false);
        }
        if ("item_give".equals(activity.category)) {
            player.sendMessage(Text.literal("可领取物品：" + itemSummary(activity.itemInventory, activity.itemSummary)).formatted(Formatting.AQUA), false);
            MutableText claimHint = Text.literal("领取：").formatted(Formatting.GREEN)
                    .append(Text.literal("[手动领取]").styled(style -> style
                            .withColor(Formatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/fsm_activity_claim " + activity.id))));
            player.sendMessage(claimHint, false);
        }
        if ("maintenance".equals(activity.category)) {
            player.sendMessage(Text.literal("维护时间：" + activity.maintenanceTimeText).formatted(Formatting.LIGHT_PURPLE), false);
            player.sendMessage(Text.literal("维护倒计时：" + activity.maintenanceCountdownSeconds + " 秒后服务器将停止。").formatted(Formatting.RED), false);
        }
        if (activity.needsTeleport) {
            MutableText teleportHint = Text.literal("传送：").formatted(Formatting.GREEN)
                    .append(Text.literal("[打开菜单前往集合点]").styled(style -> style
                            .withColor(Formatting.AQUA)
                            .withClickEvent(new ClickEvent.RunCommand("/menu activity"))));
            player.sendMessage(teleportHint, false);
        }
    }

    private static SimpleInventory draftItemInventory(ServerPlayerEntity player) {
        return DRAFT_ITEM_INVENTORIES.computeIfAbsent(player.getUuid(), ignored -> new SimpleInventory(ACTIVITY_ITEM_INVENTORY_SIZE));
    }

    private static void copyInventory(SimpleInventory source, SimpleInventory target) {
        if (source == null || target == null) {
            return;
        }
        int size = Math.min(source.size(), target.size());
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = source.getStack(slot);
            target.setStack(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
    }

    private static void clearInventory(SimpleInventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, ItemStack.EMPTY);
        }
    }

    private static boolean hasItems(SimpleInventory inventory) {
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int totalItemCount(SimpleInventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String itemSummary(SimpleInventory inventory, String fallback) {
        LinkedHashMap<String, Integer> itemCounts = new LinkedHashMap<>();
        if (inventory != null) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                itemCounts.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
            }
        }
        if (itemCounts.isEmpty()) {
            return textOr(fallback, "活动物品箱");
        }

        StringBuilder builder = new StringBuilder();
        int rendered = 0;
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (rendered > 0) {
                builder.append("，");
            }
            builder.append(entry.getKey()).append(" x").append(entry.getValue());
            rendered++;
            if (rendered >= 3) {
                break;
            }
        }
        if (itemCounts.size() > rendered) {
            builder.append(" 等");
        }
        return builder.toString();
    }

    private static long parseEndDate(String value) {
        try {
            return LocalDateTime.parse(safe(value).trim(), END_DATE_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeException exception) {
            return -1L;
        }
    }

    private static String coordinates(ActiveActivity activity) {
        return "[" + StatusManager.formatDimensionId(activity.world) + "] X:" + Math.round(activity.x)
                + " Y:" + Math.round(activity.y)
                + " Z:" + Math.round(activity.z);
    }

    private static String normalizeCategory(String category) {
        return switch (safe(category)) {
            case "item_give", "maintenance", "exploration", "custom" -> category;
            default -> "gathering";
        };
    }

    private static boolean needsMeetingPoint(String category) {
        return switch (normalizeCategory(category)) {
            case "item_give", "maintenance" -> false;
            default -> true;
        };
    }

    private static boolean needsEndDate(String category) {
        return !"maintenance".equals(normalizeCategory(category));
    }

    private static int maintenanceRemainingSeconds(ActiveActivity activity) {
        if (activity == null || activity.maintenanceEndsAtMillis <= 0L) {
            return 0;
        }
        long remainingMillis = activity.maintenanceEndsAtMillis - System.currentTimeMillis();
        return Math.max(0, (int) Math.ceil(remainingMillis / 1000.0D));
    }

    private static String categoryLabel(String category) {
        return switch (normalizeCategory(category)) {
            case "item_give" -> "发物品";
            case "maintenance" -> "维护通知";
            case "exploration" -> "探索/副本";
            case "custom" -> "自由通知";
            default -> "集合活动";
        };
    }

    private static String clean(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String textOr(String value, String fallback) {
        String text = safe(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class SubmittedActivity {
        public String title;
        public String description;
        public String meetingPoint;
        public String category;
        public boolean needsTeleport;
        public boolean hasEndDate;
        public String endDateText;
        public String itemId;
        public int itemCount;
        public String maintenanceTimeText;
        public int maintenanceCountdownSeconds;
    }

    public static class ActiveActivity {
        public String id;
        public String initiator;
        public String category;
        public String title;
        public String description;
        public String meetingPoint;
        public boolean needsTeleport;
        public boolean hasEndDate;
        public String endDateText;
        public String itemId;
        public int itemCount;
        public String itemSummary;
        public String maintenanceTimeText;
        public int maintenanceCountdownSeconds;
        public long maintenanceEndsAtMillis;
        public int maintenanceRemainingSeconds;
        public int lastMaintenanceReminderSeconds;
        public boolean itemClaimedByViewer;
        public transient SimpleInventory itemInventory = new SimpleInventory(ACTIVITY_ITEM_INVENTORY_SIZE);
        public transient Set<UUID> notifiedPlayers = new HashSet<>();
        public transient Set<UUID> itemClaimedPlayers = new HashSet<>();
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public long expiresAtMillis;
    }
}
