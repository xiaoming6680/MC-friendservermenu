package com.xm6680.friendservermenu.config;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public final class ModConfigManager {
    public static final String DEFAULT_MENU_TITLE = "小铭的服务器菜单";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("friendservermenu.json");
    private static ModConfig config = ModConfig.defaults();

    private ModConfigManager() {
    }

    public static synchronized void load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                config = ModConfig.defaults();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig loaded = FriendServerMenuMod.GSON.fromJson(reader, ModConfig.class);
                config = sanitize(loaded);
            }
        } catch (Exception exception) {
            config = ModConfig.defaults();
            save();
        }
    }

    public static synchronized ModConfig get() {
        return config;
    }

    public static synchronized LocationEntry findLocation(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (LocationEntry location : config.locations) {
            if (id.equals(location.id)) {
                return location;
            }
        }
        return null;
    }

    public static synchronized AddLocationResult addLocation(LocationEntry location) {
        return addLocation(location, true);
    }

    public static synchronized AddLocationResult addLocation(LocationEntry location, boolean allowRequestedId) {
        if (location == null) {
            return AddLocationResult.fail("传送点数据为空。");
        }

        boolean idWasBlank = location.id == null || location.id.isBlank();
        LocationEntry sanitized = sanitizeLocation(location);
        if (!allowRequestedId || idWasBlank) {
            sanitized.id = generateUniqueId(sanitized.name);
        }

        String validationError = validateLocation(sanitized);
        if (validationError != null) {
            return AddLocationResult.fail(validationError);
        }

        if (findLocation(sanitized.id) != null) {
            return AddLocationResult.fail("传送点 ID 已存在：" + sanitized.id);
        }

        config.locations.add(sanitized);
        if (!save()) {
            config.locations.remove(sanitized);
            return AddLocationResult.fail("配置保存失败，请检查服务器 config 目录权限。");
        }
        return AddLocationResult.success(sanitized);
    }

    public static synchronized AddLocationResult editLocation(String originalId, LocationEntry updated) {
        return editLocation(originalId, updated, true);
    }

    public static synchronized AddLocationResult editLocation(String originalId, LocationEntry updated, boolean allowIdChange) {
        if (originalId == null || originalId.isBlank()) {
            return AddLocationResult.fail("原传送点 ID 不能为空。");
        }
        if (updated == null) {
            return AddLocationResult.fail("传送点数据为空。");
        }

        int index = indexOfLocation(originalId);
        if (index < 0) {
            return AddLocationResult.fail("传送点不存在：" + originalId);
        }

        boolean idWasBlank = updated.id == null || updated.id.isBlank();
        LocationEntry sanitized = sanitizeLocation(updated);
        LocationEntry existing = config.locations.get(index);
        sanitized.creatorUuid = existing.creatorUuid;
        sanitized.creatorName = existing.creatorName;
        if (!allowIdChange) {
            sanitized.id = originalId;
        } else if (idWasBlank) {
            sanitized.id = generateUniqueId(sanitized.name);
        }
        String validationError = validateLocation(sanitized);
        if (validationError != null) {
            return AddLocationResult.fail(validationError);
        }

        int duplicateIndex = indexOfLocation(sanitized.id);
        if (duplicateIndex >= 0 && duplicateIndex != index) {
            return AddLocationResult.fail("传送点 ID 已存在：" + sanitized.id);
        }

        LocationEntry previous = config.locations.set(index, sanitized);
        if (!save()) {
            config.locations.set(index, previous);
            return AddLocationResult.fail("配置保存失败，请检查服务器 config 目录权限。");
        }
        return AddLocationResult.success(sanitized);
    }

    public static synchronized AddLocationResult deleteLocation(String id) {
        int index = indexOfLocation(id);
        if (index < 0) {
            return AddLocationResult.fail("传送点不存在：" + id);
        }

        LocationEntry removed = config.locations.remove(index);
        if (!save()) {
            config.locations.add(index, removed);
            return AddLocationResult.fail("配置保存失败，请检查服务器 config 目录权限。");
        }
        return AddLocationResult.success(removed);
    }

    public static synchronized String locationsJson() {
        return FriendServerMenuMod.GSON.toJson(config.locations);
    }

    public static synchronized String generateUniqueId(String name) {
        String base = normalizeId(name);
        String candidate = base;
        int suffix = 2;
        while (findLocation(candidate) != null) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = Math.max(1, 64 - suffixText.length());
            candidate = base.substring(0, Math.min(base.length(), maxBaseLength)) + suffixText;
        }
        return candidate;
    }

    private static int indexOfLocation(String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        for (int i = 0; i < config.locations.size(); i++) {
            if (id.equals(config.locations.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                FriendServerMenuMod.GSON.toJson(config, writer);
            }
            return true;
        } catch (IOException ignored) {
            // If the config cannot be saved, keep the in-memory defaults so the mod still works.
            return false;
        }
    }

    private static ModConfig sanitize(ModConfig loaded) {
        if (loaded == null) {
            return ModConfig.defaults();
        }

        if (loaded.menuTitle == null || loaded.menuTitle.isBlank() || "朋友服控制台".equals(loaded.menuTitle) || "小铭的控制台".equals(loaded.menuTitle)) {
            loaded.menuTitle = DEFAULT_MENU_TITLE;
        }

        if (loaded.locations == null) {
            loaded.locations = new ArrayList<>();
        }

        ArrayList<LocationEntry> sanitizedLocations = new ArrayList<>();
        for (LocationEntry location : loaded.locations) {
            LocationEntry sanitized = sanitizeLocation(location);
            if (validateLocation(sanitized) == null && sanitizedLocations.stream().noneMatch(existing -> existing.id.equals(sanitized.id))) {
                sanitizedLocations.add(sanitized);
            }
        }
        loaded.locations = sanitizedLocations;

        return loaded;
    }

    private static LocationEntry sanitizeLocation(LocationEntry location) {
        if (location == null) {
            return null;
        }

        LocationEntry sanitized = new LocationEntry();
        sanitized.id = normalizeId(location.id);
        sanitized.name = clean(location.name, 40);
        sanitized.world = clean(location.world, 80);
        sanitized.x = location.x;
        sanitized.y = location.y;
        sanitized.z = location.z;
        sanitized.yaw = location.yaw;
        sanitized.pitch = location.pitch;
        sanitized.description = clean(location.description, 120);
        sanitized.creatorUuid = clean(location.creatorUuid, 64);
        sanitized.creatorName = clean(location.creatorName, 40);
        return sanitized;
    }

    private static String validateLocation(LocationEntry location) {
        if (location == null) {
            return "传送点数据为空。";
        }
        if (location.name == null || location.name.isBlank()) {
            return "传送点名称不能为空。";
        }
        if (location.id == null || location.id.isBlank()) {
            return "传送点 ID 不能为空。";
        }
        if (location.world == null || location.world.isBlank()) {
            return "传送点维度不能为空。";
        }
        if (!isFinite(location.x) || !isFinite(location.y) || !isFinite(location.z) || !isFinite(location.yaw) || !isFinite(location.pitch)) {
            return "传送点坐标或朝向不是合法数字。";
        }
        return null;
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String normalizeId(String value) {
        String source = clean(value, 64);
        String cleaned = source.toLowerCase()
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!cleaned.isBlank()) {
            return cleaned;
        }
        return source.isBlank() ? "location" : "tp_" + Integer.toUnsignedString(source.hashCode(), 36);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public record AddLocationResult(boolean success, String message, LocationEntry location) {
        public static AddLocationResult success(LocationEntry location) {
            return new AddLocationResult(true, "", location);
        }

        public static AddLocationResult fail(String message) {
            return new AddLocationResult(false, message, null);
        }
    }
}
