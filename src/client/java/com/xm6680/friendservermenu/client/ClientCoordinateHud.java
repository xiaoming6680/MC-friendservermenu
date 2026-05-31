package com.xm6680.friendservermenu.client;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientCoordinateHud {
    private static final int MIN_WIDTH = 120;
    private static final int MIN_FALLBACK_WIDTH = 80;
    private static final double ASPECT_RATIO = 190.0D / 58.0D;
    private static final HudSettings SETTINGS = new HudSettings();
    private static boolean loaded;
    private static boolean editMode;

    private ClientCoordinateHud() {
    }

    public static void register() {
        loadSettings();
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
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
        int maxWidth = maxHudWidth(screenWidth, screenHeight);
        int minWidth = Math.min(MIN_WIDTH, maxWidth);
        SETTINGS.width = clamp(SETTINGS.width + steps * 14, minWidth, maxWidth);
        SETTINGS.height = heightForWidth(SETTINGS.width);
        clampPosition(screenWidth, screenHeight);
        saveSettings();
    }

    public static void renderPreview(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        loadSettings();
        fitSizeToScreen(screenWidth, screenHeight);
        clampPosition(screenWidth, screenHeight);
        drawHud(context, textRenderer, true);
    }

    private static void render(DrawContext context) {
        loadSettings();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SETTINGS.enabled || client.currentScreen != null || client.player == null) {
            return;
        }
        fitSizeToScreen(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        clampPosition(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        drawHud(context, client.textRenderer, false);
    }

    private static void drawHud(DrawContext context, TextRenderer textRenderer, boolean preview) {
        loadSettings();
        int x = SETTINGS.x;
        int y = SETTINGS.y;
        int width = SETTINGS.width;
        int height = SETTINGS.height;
        int border = preview ? 0xFFFFD166 : 0xFF77E287;
        context.fill(x, y, x + width, y + height, 0xAA101821);
        context.fill(x, y, x + width, y + 1, border);
        context.fill(x, y + height - 1, x + width, y + height, border);
        context.fill(x, y, x + 1, y + height, border);
        context.fill(x + width - 1, y, x + width, y + height, border);

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        int textWidth = Math.max(20, width - 14);
        context.drawText(textRenderer, Text.literal("坐标 HUD"), x + 7, y + 7, 0xFFFFFFFF, true);
        if (player == null) {
            context.drawText(textRenderer, Text.literal("进入世界后显示当前位置"), x + 7, y + 22, 0xFFC9D4DE, false);
            return;
        }

        BlockPos pos = player.getBlockPos();
        String dimension = client.world == null ? "未知维度" : dimensionName(client.world.getRegistryKey().getValue().toString());
        String biome = client.world == null ? "未知群系" : client.world.getBiome(pos).getKey()
                .map(key -> biomeName(key.getValue().toString()))
                .orElse("未知群系");
        context.drawText(textRenderer, Text.literal(trim(textRenderer, dimension + " / " + biome, textWidth)), x + 7, y + 22, 0xFFC9D4DE, false);
        context.drawText(textRenderer, Text.literal(trim(textRenderer, "X:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ(), textWidth)), x + 7, y + 37, 0xFFDDE7F0, false);
        if (height >= 72) {
            context.drawText(textRenderer, Text.literal(trim(textRenderer, String.format(Locale.ROOT, "Yaw: %.1f Pitch: %.1f", player.getYaw(), player.getPitch()), textWidth)), x + 7, y + 52, 0xFF9FB0BF, false);
        }
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
            }
        } catch (Exception ignored) {
            // HUD settings are client convenience data; keep defaults if loading fails.
        }
    }

    private static void saveSettings() {
        try {
            Path path = settingsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, FriendServerMenuMod.GSON.toJson(SETTINGS));
        } catch (Exception ignored) {
            // HUD settings should never interrupt gameplay.
        }
    }

    private static Path settingsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-coordinate-hud.json");
    }

    private static String dimensionName(String id) {
        return switch (id == null ? "" : id) {
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            case "minecraft:overworld" -> "主世界";
            default -> id == null || id.isBlank() ? "未知维度" : id;
        };
    }

    private static String biomeName(String id) {
        return switch (id == null ? "" : id) {
            case "minecraft:badlands" -> "恶地";
            case "minecraft:bamboo_jungle" -> "竹林";
            case "minecraft:basalt_deltas" -> "玄武岩三角洲";
            case "minecraft:beach" -> "沙滩";
            case "minecraft:birch_forest" -> "白桦森林";
            case "minecraft:cherry_grove" -> "樱花树林";
            case "minecraft:cold_ocean" -> "冷水海洋";
            case "minecraft:crimson_forest" -> "绯红森林";
            case "minecraft:dark_forest" -> "黑森林";
            case "minecraft:deep_cold_ocean" -> "冷水深海";
            case "minecraft:deep_dark" -> "深暗之域";
            case "minecraft:deep_frozen_ocean" -> "冰冻深海";
            case "minecraft:deep_lukewarm_ocean" -> "温水深海";
            case "minecraft:deep_ocean" -> "深海";
            case "minecraft:desert" -> "沙漠";
            case "minecraft:dripstone_caves" -> "溶洞";
            case "minecraft:end_barrens" -> "末地荒地";
            case "minecraft:end_highlands" -> "末地高地";
            case "minecraft:end_midlands" -> "末地内陆";
            case "minecraft:eroded_badlands" -> "风蚀恶地";
            case "minecraft:flower_forest" -> "繁花森林";
            case "minecraft:forest" -> "森林";
            case "minecraft:frozen_ocean" -> "冰冻海洋";
            case "minecraft:frozen_peaks" -> "冰封山峰";
            case "minecraft:frozen_river" -> "冰冻河流";
            case "minecraft:grove" -> "雪林";
            case "minecraft:ice_spikes" -> "冰刺平原";
            case "minecraft:jagged_peaks" -> "尖峭山峰";
            case "minecraft:jungle" -> "丛林";
            case "minecraft:lukewarm_ocean" -> "温水海洋";
            case "minecraft:lush_caves" -> "繁茂洞穴";
            case "minecraft:mangrove_swamp" -> "红树林沼泽";
            case "minecraft:meadow" -> "草甸";
            case "minecraft:mushroom_fields" -> "蘑菇岛";
            case "minecraft:nether_wastes" -> "下界荒地";
            case "minecraft:ocean" -> "海洋";
            case "minecraft:old_growth_birch_forest" -> "原始白桦森林";
            case "minecraft:old_growth_pine_taiga" -> "原始松木针叶林";
            case "minecraft:old_growth_spruce_taiga" -> "原始云杉针叶林";
            case "minecraft:pale_garden" -> "苍白花园";
            case "minecraft:plains" -> "平原";
            case "minecraft:river" -> "河流";
            case "minecraft:savanna" -> "热带草原";
            case "minecraft:savanna_plateau" -> "热带高原";
            case "minecraft:small_end_islands" -> "末地小型岛屿";
            case "minecraft:snowy_beach" -> "积雪沙滩";
            case "minecraft:snowy_plains" -> "雪原";
            case "minecraft:snowy_slopes" -> "积雪山坡";
            case "minecraft:snowy_taiga" -> "积雪针叶林";
            case "minecraft:soul_sand_valley" -> "灵魂沙峡谷";
            case "minecraft:sparse_jungle" -> "稀疏丛林";
            case "minecraft:stony_peaks" -> "裸岩山峰";
            case "minecraft:stony_shore" -> "石岸";
            case "minecraft:sunflower_plains" -> "向日葵平原";
            case "minecraft:swamp" -> "沼泽";
            case "minecraft:taiga" -> "针叶林";
            case "minecraft:the_end" -> "末地";
            case "minecraft:warm_ocean" -> "暖水海洋";
            case "minecraft:warped_forest" -> "诡异森林";
            case "minecraft:windswept_forest" -> "风袭森林";
            case "minecraft:windswept_gravelly_hills" -> "风袭沙砾丘陵";
            case "minecraft:windswept_hills" -> "风袭丘陵";
            case "minecraft:windswept_savanna" -> "风袭热带草原";
            case "minecraft:wooded_badlands" -> "疏林恶地";
            default -> id == null || id.isBlank() ? "未知群系" : id;
        };
    }

    private static String trim(TextRenderer textRenderer, String text, int maxWidth) {
        return textRenderer.trimToWidth(text == null ? "" : text, Math.max(10, maxWidth));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void fitSizeToScreen(int screenWidth, int screenHeight) {
        int maxWidth = maxHudWidth(screenWidth, screenHeight);
        int minWidth = Math.min(MIN_WIDTH, maxWidth);
        SETTINGS.width = clamp(SETTINGS.width, minWidth, maxWidth);
        SETTINGS.height = heightForWidth(SETTINGS.width);
    }

    private static int maxHudWidth(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(MIN_FALLBACK_WIDTH, screenWidth - 8);
        int availableHeightAsWidth = Math.max(MIN_FALLBACK_WIDTH, (int) Math.floor(Math.max(1, screenHeight - 8) * ASPECT_RATIO));
        return Math.max(MIN_FALLBACK_WIDTH, Math.min(availableWidth, availableHeightAsWidth));
    }

    private static int heightForWidth(int width) {
        return Math.max(42, (int) Math.round(width / ASPECT_RATIO));
    }

    private static void clampPosition(int screenWidth, int screenHeight) {
        SETTINGS.x = clamp(SETTINGS.x, 0, Math.max(0, screenWidth - SETTINGS.width));
        SETTINGS.y = clamp(SETTINGS.y, 0, Math.max(0, screenHeight - SETTINGS.height));
    }

    private static class HudSettings {
        boolean enabled = true;
        int x = 8;
        int y = 72;
        int width = 190;
        int height = 58;
    }
}
