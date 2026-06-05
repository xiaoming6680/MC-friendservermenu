package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSettingsManager {
    public static final String AUTO_CLAIM_ACTIVITY_ITEMS = "auto_claim_activity_items";

    private static final Path SETTINGS_PATH = FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-player-settings.json");
    private static final Map<String, PlayerSettings> SETTINGS = new LinkedHashMap<>();

    private PlayerSettingsManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        SETTINGS.clear();
        if (Files.notExists(SETTINGS_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
            PlayerSettingsStore store = FriendServerMenuMod.GSON.fromJson(reader, PlayerSettingsStore.class);
            if (store == null || store.players == null) {
                return;
            }
            for (Map.Entry<String, PlayerSettings> entry : store.players.entrySet()) {
                String uuid = sanitizeUuid(entry.getKey());
                if (!uuid.isBlank()) {
                    SETTINGS.put(uuid, sanitize(entry.getValue()));
                }
            }
        } catch (Exception exception) {
            SETTINGS.clear();
            save();
        }
    }

    public static synchronized PlayerSettings settings(ServerPlayerEntity player) {
        if (player == null) {
            return new PlayerSettings();
        }
        return copy(mutableSettings(player.getUuid()));
    }

    public static synchronized boolean autoClaimActivityItems(ServerPlayerEntity player) {
        return settings(player).autoClaimActivityItems;
    }

    public static synchronized boolean update(ServerPlayerEntity player, String key, boolean value) {
        if (player == null || !isKnownKey(key)) {
            return false;
        }

        PlayerSettings settings = mutableSettings(player.getUuid());
        switch (key) {
            case AUTO_CLAIM_ACTIVITY_ITEMS -> settings.autoClaimActivityItems = value;
            default -> {
                return false;
            }
        }
        save();
        return true;
    }

    private static PlayerSettings mutableSettings(UUID uuid) {
        return SETTINGS.computeIfAbsent(uuid.toString(), ignored -> new PlayerSettings());
    }

    private static boolean isKnownKey(String key) {
        return AUTO_CLAIM_ACTIVITY_ITEMS.equals(key);
    }

    private static void save() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            PlayerSettingsStore store = new PlayerSettingsStore();
            for (Map.Entry<String, PlayerSettings> entry : SETTINGS.entrySet()) {
                store.players.put(entry.getKey(), copy(entry.getValue()));
            }
            try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
                FriendServerMenuMod.GSON.toJson(store, writer);
            }
        } catch (IOException ignored) {
            // Settings are kept in memory if the config directory cannot be written.
        }
    }

    private static PlayerSettings sanitize(PlayerSettings settings) {
        return settings == null ? new PlayerSettings() : copy(settings);
    }

    private static PlayerSettings copy(PlayerSettings source) {
        PlayerSettings copy = new PlayerSettings();
        if (source != null) {
            copy.autoClaimActivityItems = source.autoClaimActivityItems;
        }
        return copy;
    }

    private static String sanitizeUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static class PlayerSettingsStore {
        Map<String, PlayerSettings> players = new LinkedHashMap<>();
    }

    public static class PlayerSettings {
        public boolean autoClaimActivityItems = false;
    }
}
