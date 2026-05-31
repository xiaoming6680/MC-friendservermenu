package com.xm6680.friendservermenu.client;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class ClientTaskHud {
    private static final int MIN_WIDTH = 120;
    private static final int MIN_HEIGHT = 42;
    private static final int MIN_FALLBACK_WIDTH = 80;
    private static final int MIN_FALLBACK_HEIGHT = 32;
    private static final HudSettings SETTINGS = new HudSettings();
    private static List<HudTask> tasks = List.of();
    private static boolean loaded;
    private static boolean editMode;

    private ClientTaskHud() {
    }

    public static void register() {
        loadSettings();
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    public static void applyTasksJson(String tasksJson) {
        try {
            HudTask[] parsed = FriendServerMenuMod.GSON.fromJson(tasksJson == null ? "" : tasksJson, HudTask[].class);
            tasks = parsed == null ? List.of() : Arrays.asList(parsed);
        } catch (Exception ignored) {
            tasks = List.of();
        }
        if (!SETTINGS.selectedTaskId.isBlank() && selectedTask(false) == null) {
            SETTINGS.selectedTaskId = "";
            saveSettings();
        }
    }

    public static boolean isEnabled() {
        loadSettings();
        return SETTINGS.enabled;
    }

    public static boolean toggleEnabled() {
        loadSettings();
        SETTINGS.enabled = !SETTINGS.enabled;
        saveSettings();
        return SETTINGS.enabled;
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static void setEditMode(boolean value) {
        editMode = value;
    }

    public static boolean contains(double mouseX, double mouseY) {
        loadSettings();
        return mouseX >= SETTINGS.x && mouseX < SETTINGS.x + SETTINGS.width
                && mouseY >= SETTINGS.y && mouseY < SETTINGS.y + SETTINGS.height;
    }

    public static void moveBy(double deltaX, double deltaY, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        SETTINGS.x = (int) Math.round(SETTINGS.x + deltaX);
        SETTINGS.y = (int) Math.round(SETTINGS.y + deltaY);
        clampPosition(screenWidth, screenHeight);
        saveSettings();
    }

    public static void resizeBy(int steps, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        double heightRatio = SETTINGS.height / (double) Math.max(1, SETTINGS.width);
        int nextWidth = clamp(SETTINGS.width + steps * 14, minHudWidth(screenWidth), maxHudWidth(screenWidth));
        int nextHeight = clamp((int) Math.round(nextWidth * heightRatio), minHudHeight(screenHeight), maxHudHeight(screenHeight));
        SETTINGS.width = nextWidth;
        SETTINGS.height = nextHeight;
        clampPosition(screenWidth, screenHeight);
        saveSettings();
    }

    public static void resizeWidthBy(int steps, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        SETTINGS.width = clamp(SETTINGS.width + steps * 14, minHudWidth(screenWidth), maxHudWidth(screenWidth));
        clampPosition(screenWidth, screenHeight);
        saveSettings();
    }

    public static void resizeHeightBy(int steps, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        SETTINGS.height = clamp(SETTINGS.height + steps * 8, minHudHeight(screenHeight), maxHudHeight(screenHeight));
        clampPosition(screenWidth, screenHeight);
        saveSettings();
    }

    public static void selectTask(String taskId) {
        loadSettings();
        SETTINGS.selectedTaskId = taskId == null ? "" : taskId;
        saveSettings();
    }

    public static void clearSelectedTask(String taskId) {
        loadSettings();
        if (SETTINGS.selectedTaskId.equals(taskId == null ? "" : taskId)) {
            SETTINGS.selectedTaskId = "";
            saveSettings();
        }
    }

    public static boolean isSelectedTask(String taskId) {
        loadSettings();
        HudTask selected = selectedTask(false);
        return selected != null && selected.id.equals(taskId);
    }

    public static String selectedTaskTitle() {
        HudTask selected = selectedTask(false);
        return selected == null ? "未选择任务" : selected.title;
    }

    public static void renderPreview(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        clampPosition(screenWidth, screenHeight);
        drawHud(context, textRenderer, selectedTask(true), true);
    }

    private static void render(DrawContext context) {
        loadSettings();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SETTINGS.enabled || client.currentScreen != null || client.player == null) {
            return;
        }
        fitSizeToScreen(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        clampPosition(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        HudTask selected = selectedTask(false);
        if (selected == null) {
            return;
        }
        drawHud(context, client.textRenderer, selected, false);
    }

    private static void drawHud(DrawContext context, TextRenderer textRenderer, HudTask task, boolean preview) {
        loadSettings();
        int x = SETTINGS.x;
        int y = SETTINGS.y;
        int width = SETTINGS.width;
        int height = SETTINGS.height;
        int border = preview ? 0xFFFFD166 : 0xFF6CB6FF;
        context.fill(x, y, x + width, y + height, 0xAA101821);
        context.fill(x, y, x + width, y + 1, border);
        context.fill(x, y + height - 1, x + width, y + height, border);
        context.fill(x, y, x + 1, y + height, border);
        context.fill(x + width - 1, y, x + width, y + height, border);

        if (task == null) {
            context.drawText(textRenderer, Text.literal("任务 HUD"), x + 7, y + 7, 0xFFFFFFFF, true);
            context.drawText(textRenderer, Text.literal("加入任务后会显示在这里"), x + 7, y + 22, 0xFFC9D4DE, false);
            return;
        }

        int textWidth = Math.max(20, width - 14);
        context.drawText(textRenderer, Text.literal(trim(textRenderer, task.title, textWidth)), x + 7, y + 7, 0xFFFFFFFF, true);
        context.drawText(textRenderer, Text.literal("状态：" + statusLabel(task.status) + "  人数：" + task.participantCount), x + 7, y + 22, 0xFFC9D4DE, false);
        if (height >= 52) {
            if ("voting".equals(task.status)) {
                context.drawText(textRenderer, Text.literal("确认：" + task.voteCount + "/" + task.voteThreshold), x + 7, y + 37, 0xFF9BFFB2, false);
            } else if (task.reward != null && !task.reward.isBlank()) {
                context.drawText(textRenderer, Text.literal(trim(textRenderer, "奖励：" + task.reward, textWidth)), x + 7, y + 37, 0xFFFFE08A, false);
            } else if (height >= 58) {
                context.drawText(textRenderer, Text.literal(trim(textRenderer, task.description, textWidth)), x + 7, y + 37, 0xFF9FB0BF, false);
            }
        }
    }

    private static HudTask selectedTask(boolean allowPreviewFallback) {
        loadSettings();
        if (!SETTINGS.selectedTaskId.isBlank()) {
            for (HudTask task : tasks) {
                if (SETTINGS.selectedTaskId.equals(task.id) && task.viewerJoined && !isHistoricalStatus(task.status)) {
                    return task;
                }
            }
        }
        for (HudTask task : tasks) {
            if (task.viewerJoined && !isHistoricalStatus(task.status)) {
                SETTINGS.selectedTaskId = task.id;
                saveSettings();
                return task;
            }
        }
        return null;
    }

    private static void loadSettings() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = settingsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            HudSettings loadedSettings = FriendServerMenuMod.GSON.fromJson(Files.readString(path), HudSettings.class);
            if (loadedSettings != null) {
                SETTINGS.enabled = loadedSettings.enabled;
                SETTINGS.x = loadedSettings.x;
                SETTINGS.y = loadedSettings.y;
                SETTINGS.width = loadedSettings.width;
                SETTINGS.height = loadedSettings.height;
                SETTINGS.selectedTaskId = loadedSettings.selectedTaskId == null ? "" : loadedSettings.selectedTaskId;
            }
        } catch (Exception ignored) {
            // Keep defaults if the local HUD settings file is malformed.
        }
    }

    private static void saveSettings() {
        try {
            Path path = settingsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, FriendServerMenuMod.GSON.toJson(SETTINGS));
        } catch (Exception ignored) {
            // HUD settings are client convenience data; do not interrupt gameplay if saving fails.
        }
    }

    private static Path settingsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-hud.json");
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "voting" -> "确认中";
            case "completed" -> "已完成";
            case "ended" -> "已结束";
            default -> "进行中";
        };
    }

    private static boolean isHistoricalStatus(String status) {
        return "completed".equals(status) || "ended".equals(status);
    }

    private static String trim(TextRenderer textRenderer, String text, int maxWidth) {
        return textRenderer.trimToWidth(text == null ? "" : text, Math.max(10, maxWidth));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void fitSizeToScreen(int screenWidth, int screenHeight) {
        SETTINGS.width = clamp(SETTINGS.width, minHudWidth(screenWidth), maxHudWidth(screenWidth));
        SETTINGS.height = clamp(SETTINGS.height, minHudHeight(screenHeight), maxHudHeight(screenHeight));
    }

    private static int minHudWidth(int screenWidth) {
        return Math.min(MIN_WIDTH, maxHudWidth(screenWidth));
    }

    private static int maxHudWidth(int screenWidth) {
        return Math.max(MIN_FALLBACK_WIDTH, screenWidth - 8);
    }

    private static int minHudHeight(int screenHeight) {
        return Math.min(MIN_HEIGHT, maxHudHeight(screenHeight));
    }

    private static int maxHudHeight(int screenHeight) {
        return Math.max(MIN_FALLBACK_HEIGHT, screenHeight - 8);
    }

    private static void clampPosition(int screenWidth, int screenHeight) {
        SETTINGS.x = clamp(SETTINGS.x, 0, Math.max(0, screenWidth - SETTINGS.width));
        SETTINGS.y = clamp(SETTINGS.y, 0, Math.max(0, screenHeight - SETTINGS.height));
    }

    public static class HudTask {
        public String id = "";
        public String title = "";
        public String description = "";
        public String reward = "";
        public String status = "open";
        public int participantCount;
        public int voteCount;
        public int voteThreshold;
        public boolean viewerJoined;
    }

    private static class HudSettings {
        boolean enabled = true;
        int x = 8;
        int y = 8;
        int width = 190;
        int height = 58;
        String selectedTaskId = "";
    }
}
