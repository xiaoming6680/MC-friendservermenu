package com.xm6680.friendservermenu.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.network.ModNetworking;
import com.xm6680.friendservermenu.network.TaskActionPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaskManager {
    private static final int MAX_TASKS = 80;
    private static final int COMPLETION_EXPERIENCE = 10;
    private static final Path TASKS_PATH = FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-tasks.json");
    private static final Map<String, ServerTask> TASKS = new LinkedHashMap<>();
    private static MinecraftServer activeServer;

    private TaskManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        activeServer = server;
        TASKS.clear();
        if (Files.notExists(TASKS_PATH)) {
            saveTasks(server);
            return;
        }
        try (Reader reader = Files.newBufferedReader(TASKS_PATH)) {
            SavedTaskStore store = FriendServerMenuMod.GSON.fromJson(reader, SavedTaskStore.class);
            if (store == null || store.tasks == null) {
                return;
            }
            for (SavedTask savedTask : store.tasks) {
                ServerTask task = fromSavedTask(server, savedTask);
                if (task != null && !safe(task.id).isBlank()) {
                    TASKS.put(task.id, task);
                }
            }
            trimTasks();
        } catch (Exception exception) {
            TASKS.clear();
        }
    }

    public static void handleTaskAction(ServerPlayerEntity player, TaskActionPayload payload) {
        String action = safe(payload.actionId());
        String taskId = safe(payload.taskId());

        switch (action) {
            case "create" -> createTask(player, payload.taskJson());
            case "edit" -> editTask(player, taskId, payload.taskJson());
            case "join" -> joinTask(player, taskId);
            case "leave" -> leaveTask(player, taskId);
            case "vote_complete" -> voteComplete(player, taskId);
            case "end" -> endTask(player, taskId);
            case "invite" -> invitePlayer(player, taskId, payload.taskJson());
            case "open_rewards" -> openRewardInventory(player, taskId);
            case "delete_history" -> deleteHistoricalTaskForViewer(player, taskId);
            default -> player.sendMessage(Text.literal("未知任务操作：" + action), false);
        }
    }

    public static int joinTaskFromCommand(ServerPlayerEntity player, String taskId) {
        joinTask(player, taskId);
        return 1;
    }

    public static String visibleTasksJson(ServerPlayerEntity viewer) {
        List<ViewTask> visible = new ArrayList<>();
        for (ServerTask task : TASKS.values()) {
            if (isVisibleTo(task, viewer)) {
                visible.add(toViewTask(task, viewer));
            }
        }
        return FriendServerMenuMod.GSON.toJson(visible);
    }

    public static void sendTasksOnJoin(ServerPlayerEntity player) {
        deliverPendingTaskRewards(player);
        ModNetworking.sendLiveData(player);
    }

    private static void createTask(ServerPlayerEntity player, String taskJson) {
        SubmittedTask submitted = parseSubmittedTask(player, taskJson);
        if (submitted == null) {
            return;
        }

        String validationError = validate(submitted);
        if (validationError != null) {
            player.sendMessage(Text.literal(validationError), false);
            return;
        }

        ServerTask task = new ServerTask();
        task.id = UUID.randomUUID().toString();
        task.title = clean(submitted.title, 48);
        task.description = clean(submitted.description, 180);
        task.visibility = normalizeVisibility(submitted.visibility);
        task.publisherUuid = player.getUuid().toString();
        task.publisherName = player.getName().getString();
        task.reward = "";
        task.status = "open";
        task.createdAtMillis = System.currentTimeMillis();
        task.updatedAtMillis = task.createdAtMillis;
        task.participants.put(task.publisherUuid, task.publisherName);

        TASKS.put(task.id, task);
        trimTasks();
        saveTasks(player.getCommandSource().getServer());

        player.sendMessage(Text.literal("任务已发布：" + task.title), false);
        if ("public".equals(task.visibility)) {
            player.getCommandSource().getServer().getPlayerManager()
                    .broadcast(Text.literal("新任务：" + task.title + "（发布者：" + task.publisherName + "）").formatted(Formatting.AQUA), false);
        }
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void editTask(ServerPlayerEntity player, String taskId, String taskJson) {
        ServerTask task = TASKS.get(taskId);
        if (task == null || !isVisibleTo(task, player)) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }

        boolean admin = ModNetworking.canUseAdmin(player);
        boolean participant = task.participants.containsKey(player.getUuid().toString());
        boolean publisher = isPublisher(task, player);
        if (!admin && !participant && !publisher) {
            player.sendMessage(Text.literal("只有任务成员可以编辑任务。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("已经结束的任务不能再编辑。"), false);
            return;
        }

        SubmittedTask submitted = parseSubmittedTask(player, taskJson);
        if (submitted == null) {
            return;
        }

        String validationError = validate(submitted);
        if (validationError != null) {
            player.sendMessage(Text.literal(validationError), false);
            return;
        }

        task.title = clean(submitted.title, 48);
        task.description = clean(submitted.description, 180);
        if (admin || publisher) {
            task.visibility = normalizeVisibility(submitted.visibility);
        }
        task.updatedAtMillis = System.currentTimeMillis();
        saveTasks(player.getCommandSource().getServer());

        player.sendMessage(Text.literal("任务已更新：" + task.title), false);
        notifyParticipants(player.getCommandSource().getServer(), task, "任务已更新：" + task.title, Formatting.YELLOW);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void joinTask(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        if (task == null || !isVisibleTo(task, player)) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }
        String playerUuid = player.getUuid().toString();
        if (!"public".equals(task.visibility) && !task.participants.containsKey(playerUuid) && !task.invitedPlayers.containsKey(playerUuid)) {
            player.sendMessage(Text.literal("私人任务不能直接加入。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("这个任务已经结束。"), false);
            return;
        }
        if (task.participants.containsKey(playerUuid)) {
            player.sendMessage(Text.literal("你已经在这个任务中。"), false);
            return;
        }

        task.invitedPlayers.remove(playerUuid);
        task.participants.put(playerUuid, player.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();
        saveTasks(player.getCommandSource().getServer());
        player.sendMessage(Text.literal("已加入任务：" + task.title), false);
        notifyParticipants(player.getCommandSource().getServer(), task, player.getName().getString() + " 加入了任务：" + task.title, Formatting.AQUA);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void leaveTask(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        String playerUuid = player.getUuid().toString();
        if (task == null || !task.participants.containsKey(playerUuid)) {
            player.sendMessage(Text.literal("你不在这个任务中。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("已经结束的任务不能再退出。"), false);
            return;
        }

        task.participants.remove(playerUuid);
        task.completionVotes.remove(playerUuid);
        task.updatedAtMillis = System.currentTimeMillis();
        player.sendMessage(Text.literal("已退出任务：" + task.title), false);
        notifyParticipants(player.getCommandSource().getServer(), task, player.getName().getString() + " 退出了任务：" + task.title, Formatting.YELLOW);
        checkCompletion(player.getCommandSource().getServer(), task);
        saveTasks(player.getCommandSource().getServer());
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void voteComplete(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        String playerUuid = player.getUuid().toString();
        if (task == null || !task.participants.containsKey(playerUuid)) {
            player.sendMessage(Text.literal("只有已加入任务的玩家可以发起或参与完成确认。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("这个任务已经结束。"), false);
            return;
        }

        boolean firstVote = task.completionVotes.isEmpty();
        task.status = "voting";
        task.completionVotes.put(playerUuid, player.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();

        String message = (firstVote ? "任务完成确认已发起：" : "任务完成确认更新：") + task.title
                + "（" + task.completionVotes.size() + "/" + voteThreshold(task) + "）";
        notifyParticipants(player.getCommandSource().getServer(), task, message, Formatting.GREEN);
        checkCompletion(player.getCommandSource().getServer(), task);
        saveTasks(player.getCommandSource().getServer());
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void invitePlayer(ServerPlayerEntity player, String taskId, String targetName) {
        ServerTask task = TASKS.get(taskId);
        if (task == null || !isVisibleTo(task, player)) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }

        String playerUuid = player.getUuid().toString();
        boolean admin = ModNetworking.canUseAdmin(player);
        if (!admin && !task.participants.containsKey(playerUuid) && !isPublisher(task, player)) {
            player.sendMessage(Text.literal("只有任务成员可以邀请玩家。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("已经结束的任务不能再邀请玩家。"), false);
            return;
        }

        ServerPlayerEntity target = findOnlinePlayer(player.getCommandSource().getServer(), targetName);
        if (target == null) {
            player.sendMessage(Text.literal("找不到在线玩家：" + safe(targetName)), false);
            return;
        }
        String targetUuid = target.getUuid().toString();
        if (task.participants.containsKey(targetUuid)) {
            player.sendMessage(Text.literal(target.getName().getString() + " 已经在任务中。"), false);
            return;
        }

        task.invitedPlayers.put(targetUuid, target.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();
        saveTasks(player.getCommandSource().getServer());

        MutableText invite = Text.literal(player.getName().getString() + " 邀请你加入任务：" + task.title + " ")
                .formatted(Formatting.AQUA)
                .append(Text.literal("[加入任务]").styled(style -> style
                        .withColor(Formatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/fsm_task_join " + task.id))));
        target.sendMessage(invite, false);
        player.sendMessage(Text.literal("已邀请 " + target.getName().getString() + " 加入任务：" + task.title), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void endTask(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        if (task == null) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }
        if (!isPublisher(task, player)) {
            player.sendMessage(Text.literal("只有任务发布者可以结束任务。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("这个任务已经结束。"), false);
            return;
        }

        task.status = "ended";
        task.completedAtMillis = System.currentTimeMillis();
        task.updatedAtMillis = task.completedAtMillis;
        saveTasks(player.getCommandSource().getServer());
        notifyParticipants(player.getCommandSource().getServer(), task, "任务已结束：" + task.title, Formatting.RED);
        player.sendMessage(Text.literal("任务已结束：" + task.title), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void openRewardInventory(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        if (task == null || !isVisibleTo(task, player)) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }
        if (!ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("只有 OP 可以编辑任务奖励。"), false);
            return;
        }
        if (isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("已经结束的任务不能再编辑奖励。"), false);
            return;
        }

        task.rewardInventory.attach(task);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, user) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, task.rewardInventory),
                Text.literal("任务奖励：" + task.title)
        ));
    }

    private static void deleteHistoricalTaskForViewer(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        if (task == null || !isVisibleTo(task, player)) {
            player.sendMessage(Text.literal("找不到这个历史任务。"), false);
            return;
        }
        if (!isHistoricalStatus(task.status)) {
            player.sendMessage(Text.literal("只能删除历史任务。"), false);
            return;
        }

        String playerUuid = player.getUuid().toString();
        task.hiddenFromPlayers.put(playerUuid, player.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();
        saveTasks(player.getCommandSource().getServer());
        player.sendMessage(Text.literal("已从历史任务中删除：" + task.title), false);
        ModNetworking.sendLiveData(player);
    }

    private static void checkCompletion(MinecraftServer server, ServerTask task) {
        if (isHistoricalStatus(task.status) || task.participants.isEmpty()) {
            return;
        }
        if (task.completionVotes.size() >= voteThreshold(task)) {
            task.status = "completed";
            task.completedAtMillis = System.currentTimeMillis();
            task.updatedAtMillis = task.completedAtMillis;
            giveRewardsToOnlineParticipants(server, task);
            celebrateOnlineParticipants(server, task);
            saveTasks(server);
            notifyParticipants(server, task, "任务完成：" + task.title, Formatting.GOLD);
        }
    }

    private static SubmittedTask parseSubmittedTask(ServerPlayerEntity player, String taskJson) {
        try {
            return FriendServerMenuMod.GSON.fromJson(safe(taskJson), SubmittedTask.class);
        } catch (Exception exception) {
            player.sendMessage(Text.literal("任务数据格式无效。"), false);
            return null;
        }
    }

    private static String validate(SubmittedTask submitted) {
        if (submitted == null) {
            return "任务数据为空。";
        }
        if (safe(submitted.title).trim().isEmpty()) {
            return "任务标题不能为空。";
        }
        if (safe(submitted.title).trim().length() > 48) {
            return "任务标题不能超过 48 个字符。";
        }
        if (safe(submitted.description).trim().length() > 180) {
            return "任务说明不能超过 180 个字符。";
        }
        return null;
    }

    private static boolean isVisibleTo(ServerTask task, ServerPlayerEntity viewer) {
        if (viewer == null) {
            return false;
        }
        String viewerUuid = viewer.getUuid().toString();
        if (task.hiddenFromPlayers.containsKey(viewerUuid)) {
            return false;
        }
        if (isHistoricalStatus(task.status)) {
            return viewerUuid.equals(task.publisherUuid)
                    || task.participants.containsKey(viewerUuid)
                    || ModNetworking.canUseAdmin(viewer);
        }
        return "public".equals(task.visibility)
                || viewerUuid.equals(task.publisherUuid)
                || task.participants.containsKey(viewerUuid)
                || task.invitedPlayers.containsKey(viewerUuid)
                || ModNetworking.canUseAdmin(viewer);
    }

    private static boolean isPublisher(ServerTask task, ServerPlayerEntity player) {
        return task.publisherUuid.equals(player.getUuid().toString());
    }

    private static ViewTask toViewTask(ServerTask task, ServerPlayerEntity viewer) {
        String viewerUuid = viewer.getUuid().toString();
        boolean admin = ModNetworking.canUseAdmin(viewer);
        boolean participant = task.participants.containsKey(viewerUuid);
        boolean publisher = viewerUuid.equals(task.publisherUuid);
        boolean invited = task.invitedPlayers.containsKey(viewerUuid);
        boolean historical = isHistoricalStatus(task.status);

        ViewTask view = new ViewTask();
        view.id = task.id;
        view.title = task.title;
        view.description = task.description;
        view.visibility = task.visibility;
        view.publisherName = task.publisherName;
        view.reward = rewardSummary(task);
        view.status = task.status;
        view.participants = task.participants.values().toArray(String[]::new);
        view.participantCount = task.participants.size();
        view.voteCount = task.completionVotes.size();
        view.voteThreshold = voteThreshold(task);
        view.viewerJoined = participant;
        view.viewerPublisher = publisher;
        view.viewerVotedComplete = task.completionVotes.containsKey(viewerUuid);
        view.canJoin = ("public".equals(task.visibility) || invited) && !participant && !historical;
        view.canLeave = participant && !historical;
        view.canVoteComplete = participant && !historical && !view.viewerVotedComplete;
        view.canEdit = !historical && (participant || publisher || admin);
        view.canChangeVisibility = !historical && (publisher || admin);
        view.canEnd = publisher && !historical;
        view.canReward = admin && !historical;
        view.canInvite = !historical && (participant || publisher || admin);
        return view;
    }

    private static void giveRewardsToOnlineParticipants(MinecraftServer server, ServerTask task) {
        if (!hasRewardItems(task)) {
            return;
        }
        for (String uuidText : task.participants.keySet()) {
            try {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(UUID.fromString(uuidText));
                if (target != null) {
                    giveRewardToPlayer(target, task);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed historical participant ids.
            }
        }
    }

    private static void celebrateOnlineParticipants(MinecraftServer server, ServerTask task) {
        for (String uuidText : task.participants.keySet()) {
            try {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(UUID.fromString(uuidText));
                if (target != null) {
                    celebratePlayer(target);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed historical participant ids.
            }
        }
    }

    private static void celebratePlayer(ServerPlayerEntity player) {
        player.addExperience(COMPLETION_EXPERIENCE);
        ServerWorld world = player.getEntityWorld();
        double baseX = player.getX();
        double baseY = player.getY() + 1.1D;
        double baseZ = player.getZ();
        double[][] offsets = {{-0.55D, 0.15D}, {0.55D, -0.15D}};
        for (int i = 0; i < offsets.length; i++) {
            double x = baseX + offsets[i][0];
            double y = baseY + 0.45D + i * 0.2D;
            double z = baseZ + offsets[i][1];
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 28, 0.3D, 0.35D, 0.3D, 0.08D);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.45F, 1.0F + i * 0.08F);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.75F, 1.05F + i * 0.12F);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.55F, 1.2F + i * 0.1F);
        }
        world.playSound(null, baseX, baseY, baseZ, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.35F, 1.6F);
    }

    private static void deliverPendingTaskRewards(ServerPlayerEntity player) {
        String playerUuid = player.getUuid().toString();
        for (ServerTask task : TASKS.values()) {
            if ("completed".equals(task.status)
                    && task.participants.containsKey(playerUuid)
                    && !task.rewardedPlayers.containsKey(playerUuid)) {
                giveRewardToPlayer(player, task);
            }
        }
    }

    private static void giveRewardToPlayer(ServerPlayerEntity player, ServerTask task) {
        if (!hasRewardItems(task)) {
            return;
        }
        String playerUuid = player.getUuid().toString();
        if (task.rewardedPlayers.containsKey(playerUuid)) {
            return;
        }

        for (int slot = 0; slot < task.rewardInventory.size(); slot++) {
            ItemStack rewardStack = task.rewardInventory.getStack(slot);
            if (rewardStack.isEmpty()) {
                continue;
            }
            ItemStack stack = rewardStack.copy();
            if (!player.giveItemStack(stack) && !stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        }
        task.rewardedPlayers.put(playerUuid, player.getName().getString());
        saveTasks(player.getCommandSource().getServer());
        player.sendMessage(Text.literal("已获得任务奖励：" + task.title).formatted(Formatting.GOLD), false);
    }

    private static boolean hasRewardItems(ServerTask task) {
        for (int slot = 0; slot < task.rewardInventory.size(); slot++) {
            if (!task.rewardInventory.getStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String rewardSummary(ServerTask task) {
        LinkedHashMap<String, Integer> itemCounts = new LinkedHashMap<>();
        for (int slot = 0; slot < task.rewardInventory.size(); slot++) {
            ItemStack stack = task.rewardInventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String name = stack.getName().getString();
            itemCounts.merge(name, stack.getCount(), Integer::sum);
        }
        if (itemCounts.isEmpty()) {
            return task.reward;
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

    private static synchronized void saveTasks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Files.createDirectories(TASKS_PATH.getParent());
            SavedTaskStore store = new SavedTaskStore();
            for (ServerTask task : TASKS.values()) {
                store.tasks.add(toSavedTask(server, task));
            }
            try (Writer writer = Files.newBufferedWriter(TASKS_PATH)) {
                FriendServerMenuMod.GSON.toJson(store, writer);
            }
        } catch (IOException ignored) {
            // Task persistence is best effort; keep the in-memory task list alive.
        }
    }

    private static SavedTask toSavedTask(MinecraftServer server, ServerTask task) {
        SavedTask saved = new SavedTask();
        saved.id = task.id;
        saved.title = task.title;
        saved.description = task.description;
        saved.visibility = task.visibility;
        saved.publisherUuid = task.publisherUuid;
        saved.publisherName = task.publisherName;
        saved.reward = task.reward;
        saved.status = task.status;
        saved.createdAtMillis = task.createdAtMillis;
        saved.updatedAtMillis = task.updatedAtMillis;
        saved.completedAtMillis = task.completedAtMillis;
        saved.participants.putAll(task.participants);
        saved.invitedPlayers.putAll(task.invitedPlayers);
        saved.completionVotes.putAll(task.completionVotes);
        saved.rewardedPlayers.putAll(task.rewardedPlayers);
        saved.hiddenFromPlayers.putAll(task.hiddenFromPlayers);
        for (int slot = 0; slot < task.rewardInventory.size(); slot++) {
            ItemStack stack = task.rewardInventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            int slotIndex = slot;
            ItemStack.CODEC.encodeStart(server.getRegistryManager().getOps(JsonOps.INSTANCE), copy)
                    .result()
                    .ifPresent(encoded -> saved.rewardStacks.add(new SavedRewardStack(slotIndex, encoded.toString())));
        }
        return saved;
    }

    private static ServerTask fromSavedTask(MinecraftServer server, SavedTask saved) {
        if (saved == null || safe(saved.id).isBlank()) {
            return null;
        }
        ServerTask task = new ServerTask();
        task.id = clean(saved.id, 64);
        task.title = clean(saved.title, 48);
        task.description = clean(saved.description, 180);
        task.visibility = normalizeVisibility(saved.visibility);
        task.publisherUuid = clean(saved.publisherUuid, 64);
        task.publisherName = clean(saved.publisherName, 40);
        task.reward = clean(saved.reward, 120);
        task.status = normalizeStatus(saved.status);
        task.createdAtMillis = saved.createdAtMillis;
        task.updatedAtMillis = saved.updatedAtMillis;
        task.completedAtMillis = saved.completedAtMillis;
        putAllClean(task.participants, saved.participants);
        putAllClean(task.invitedPlayers, saved.invitedPlayers);
        putAllClean(task.completionVotes, saved.completionVotes);
        putAllClean(task.rewardedPlayers, saved.rewardedPlayers);
        putAllClean(task.hiddenFromPlayers, saved.hiddenFromPlayers);
        loadRewardInventory(server, task, saved.rewardStacks);
        return task;
    }

    private static void loadRewardInventory(MinecraftServer server, ServerTask task, List<SavedRewardStack> savedStacks) {
        if (savedStacks == null) {
            return;
        }
        for (SavedRewardStack savedStack : savedStacks) {
            if (savedStack == null || savedStack.slot < 0 || savedStack.slot >= task.rewardInventory.size() || safe(savedStack.stackJson).isBlank()) {
                continue;
            }
            try {
                JsonElement element = JsonParser.parseString(savedStack.stackJson);
                ItemStack stack = ItemStack.CODEC.parse(server.getRegistryManager().getOps(JsonOps.INSTANCE), element)
                        .result()
                        .orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    task.rewardInventory.setStack(savedStack.slot, stack);
                }
            } catch (Exception ignored) {
                // Ignore a single malformed reward stack and keep loading the rest of the task.
            }
        }
        task.rewardInventory.attach(task);
    }

    private static void putAllClean(LinkedHashMap<String, String> target, LinkedHashMap<String, String> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = clean(entry.getKey(), 64);
            if (!key.isBlank()) {
                target.put(key, clean(entry.getValue(), 40));
            }
        }
    }

    private static int voteThreshold(ServerTask task) {
        int participantCount = task == null ? 0 : task.participants.size();
        return Math.max(1, (participantCount + 1) / 2);
    }

    private static void notifyParticipants(MinecraftServer server, ServerTask task, String message, Formatting formatting) {
        for (String uuidText : task.participants.keySet()) {
            try {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(UUID.fromString(uuidText));
                if (target != null) {
                    target.sendMessage(Text.literal(message).formatted(formatting), false);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed historical participant ids.
            }
        }
    }

    private static ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String playerName) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(safe(playerName).trim())) {
                return player;
            }
        }
        return null;
    }

    private static void trimTasks() {
        while (TASKS.size() > MAX_TASKS) {
            String firstKey = TASKS.keySet().iterator().next();
            TASKS.remove(firstKey);
        }
    }

    private static String normalizeVisibility(String visibility) {
        return "private".equals(safe(visibility)) ? "private" : "public";
    }

    private static String normalizeStatus(String status) {
        return switch (safe(status)) {
            case "voting" -> "voting";
            case "completed" -> "completed";
            case "ended" -> "ended";
            default -> "open";
        };
    }

    private static String clean(String value, int maxLength) {
        String trimmed = safe(value).trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static boolean isHistoricalStatus(String status) {
        return "completed".equals(status) || "ended".equals(status);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class SubmittedTask {
        public String title;
        public String description;
        public String visibility;
        public String reward;
    }

    private static class ServerTask {
        String id;
        String title;
        String description;
        String visibility;
        String publisherUuid;
        String publisherName;
        String reward;
        String status;
        long createdAtMillis;
        long updatedAtMillis;
        long completedAtMillis;
        final LinkedHashMap<String, String> participants = new LinkedHashMap<>();
        final LinkedHashMap<String, String> invitedPlayers = new LinkedHashMap<>();
        final LinkedHashMap<String, String> completionVotes = new LinkedHashMap<>();
        final LinkedHashMap<String, String> rewardedPlayers = new LinkedHashMap<>();
        final LinkedHashMap<String, String> hiddenFromPlayers = new LinkedHashMap<>();
        final RewardInventory rewardInventory = new RewardInventory(27);
    }

    private static class RewardInventory extends SimpleInventory {
        private ServerTask task;

        RewardInventory(int size) {
            super(size);
        }

        void attach(ServerTask task) {
            this.task = task;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (task != null) {
                task.updatedAtMillis = System.currentTimeMillis();
                saveTasks(activeServer);
            }
        }
    }

    private static class SavedTaskStore {
        List<SavedTask> tasks = new ArrayList<>();
    }

    private static class SavedTask {
        String id;
        String title;
        String description;
        String visibility;
        String publisherUuid;
        String publisherName;
        String reward;
        String status;
        long createdAtMillis;
        long updatedAtMillis;
        long completedAtMillis;
        LinkedHashMap<String, String> participants = new LinkedHashMap<>();
        LinkedHashMap<String, String> invitedPlayers = new LinkedHashMap<>();
        LinkedHashMap<String, String> completionVotes = new LinkedHashMap<>();
        LinkedHashMap<String, String> rewardedPlayers = new LinkedHashMap<>();
        LinkedHashMap<String, String> hiddenFromPlayers = new LinkedHashMap<>();
        List<SavedRewardStack> rewardStacks = new ArrayList<>();
    }

    private static class SavedRewardStack {
        int slot;
        String stackJson;

        SavedRewardStack() {
        }

        SavedRewardStack(int slot, String stackJson) {
            this.slot = slot;
            this.stackJson = stackJson;
        }
    }

    public static class ViewTask {
        public String id;
        public String title;
        public String description;
        public String visibility;
        public String publisherName;
        public String reward;
        public String status;
        public String[] participants;
        public int participantCount;
        public int voteCount;
        public int voteThreshold;
        public boolean viewerJoined;
        public boolean viewerPublisher;
        public boolean viewerVotedComplete;
        public boolean canJoin;
        public boolean canLeave;
        public boolean canVoteComplete;
        public boolean canEdit;
        public boolean canChangeVisibility;
        public boolean canEnd;
        public boolean canReward;
        public boolean canInvite;
    }
}
