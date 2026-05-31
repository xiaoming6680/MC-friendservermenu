package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import com.xm6680.friendservermenu.network.ModNetworking;
import com.xm6680.friendservermenu.network.TaskActionPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TaskManager {
    private static final int MAX_TASKS = 80;
    private static final Map<String, ServerTask> TASKS = new LinkedHashMap<>();

    private TaskManager() {
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
            default -> player.sendMessage(Text.literal("未知任务操作：" + action), false);
        }
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
        task.reward = ModNetworking.canUseAdmin(player) ? clean(submitted.reward, 120) : "";
        task.status = "open";
        task.createdAtMillis = System.currentTimeMillis();
        task.updatedAtMillis = task.createdAtMillis;
        task.participants.put(task.publisherUuid, task.publisherName);

        TASKS.put(task.id, task);
        trimTasks();

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
        if (admin) {
            task.reward = clean(submitted.reward, 120);
        }
        task.updatedAtMillis = System.currentTimeMillis();

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
        if (!"public".equals(task.visibility) && !task.participants.containsKey(player.getUuid().toString())) {
            player.sendMessage(Text.literal("私人任务不能直接加入。"), false);
            return;
        }
        if ("completed".equals(task.status)) {
            player.sendMessage(Text.literal("这个任务已经完成。"), false);
            return;
        }

        task.participants.put(player.getUuid().toString(), player.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();
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

        task.participants.remove(playerUuid);
        task.completionVotes.remove(playerUuid);
        task.updatedAtMillis = System.currentTimeMillis();
        player.sendMessage(Text.literal("已退出任务：" + task.title), false);
        notifyParticipants(player.getCommandSource().getServer(), task, player.getName().getString() + " 退出了任务：" + task.title, Formatting.YELLOW);
        checkCompletion(player.getCommandSource().getServer(), task);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void voteComplete(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        String playerUuid = player.getUuid().toString();
        if (task == null || !task.participants.containsKey(playerUuid)) {
            player.sendMessage(Text.literal("只有已加入任务的玩家可以发起或参与完成投票。"), false);
            return;
        }
        if ("completed".equals(task.status)) {
            player.sendMessage(Text.literal("这个任务已经完成。"), false);
            return;
        }

        boolean firstVote = task.completionVotes.isEmpty();
        task.status = "voting";
        task.completionVotes.put(playerUuid, player.getName().getString());
        task.updatedAtMillis = System.currentTimeMillis();

        String message = (firstVote ? "任务完成投票已发起：" : "任务完成投票更新：") + task.title
                + "（" + task.completionVotes.size() + "/" + voteThreshold(task) + "）";
        notifyParticipants(player.getCommandSource().getServer(), task, message, Formatting.GREEN);
        checkCompletion(player.getCommandSource().getServer(), task);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void endTask(ServerPlayerEntity player, String taskId) {
        ServerTask task = TASKS.get(taskId);
        if (task == null) {
            player.sendMessage(Text.literal("找不到这个任务。"), false);
            return;
        }
        if (!isPublisher(task, player) && !ModNetworking.canUseAdmin(player)) {
            player.sendMessage(Text.literal("只有发布者或 OP 可以结束任务。"), false);
            return;
        }

        TASKS.remove(task.id);
        notifyParticipants(player.getCommandSource().getServer(), task, "任务已结束：" + task.title, Formatting.RED);
        player.sendMessage(Text.literal("任务已结束：" + task.title), false);
        ModNetworking.broadcastMenuData(player.getCommandSource().getServer());
    }

    private static void checkCompletion(MinecraftServer server, ServerTask task) {
        if ("completed".equals(task.status) || task.participants.isEmpty()) {
            return;
        }
        if (task.completionVotes.size() >= voteThreshold(task)) {
            task.status = "completed";
            task.completedAtMillis = System.currentTimeMillis();
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
        if (safe(submitted.reward).trim().length() > 120) {
            return "任务奖励说明不能超过 120 个字符。";
        }
        return null;
    }

    private static boolean isVisibleTo(ServerTask task, ServerPlayerEntity viewer) {
        if (viewer == null) {
            return false;
        }
        String viewerUuid = viewer.getUuid().toString();
        return "public".equals(task.visibility)
                || viewerUuid.equals(task.publisherUuid)
                || task.participants.containsKey(viewerUuid)
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
        boolean completed = "completed".equals(task.status);

        ViewTask view = new ViewTask();
        view.id = task.id;
        view.title = task.title;
        view.description = task.description;
        view.visibility = task.visibility;
        view.publisherName = task.publisherName;
        view.reward = task.reward;
        view.status = task.status;
        view.participants = task.participants.values().toArray(String[]::new);
        view.participantCount = task.participants.size();
        view.voteCount = task.completionVotes.size();
        view.voteThreshold = voteThreshold(task);
        view.viewerJoined = participant;
        view.viewerPublisher = publisher;
        view.viewerVotedComplete = task.completionVotes.containsKey(viewerUuid);
        view.canJoin = "public".equals(task.visibility) && !participant && !completed;
        view.canLeave = participant && !completed;
        view.canVoteComplete = participant && !completed && !view.viewerVotedComplete;
        view.canEdit = !completed && (participant || publisher || admin);
        view.canChangeVisibility = !completed && (publisher || admin);
        view.canEnd = publisher || admin;
        view.canReward = admin;
        return view;
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

    private static void trimTasks() {
        while (TASKS.size() > MAX_TASKS) {
            String firstKey = TASKS.keySet().iterator().next();
            TASKS.remove(firstKey);
        }
    }

    private static String normalizeVisibility(String visibility) {
        return "private".equals(safe(visibility)) ? "private" : "public";
    }

    private static String clean(String value, int maxLength) {
        String trimmed = safe(value).trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
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
        final LinkedHashMap<String, String> completionVotes = new LinkedHashMap<>();
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
    }
}
