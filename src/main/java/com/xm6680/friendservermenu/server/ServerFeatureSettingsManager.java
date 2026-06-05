package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerFeatureSettingsManager {
    public static final String DEATH_POINT_ENABLED = "death_point_enabled";
    public static final String DEATH_POINT_CHAT_ENABLED = "death_point_chat_enabled";

    private static final Path SETTINGS_PATH = FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-feature-settings.json");
    private static ServerFeatureSettings settings = new ServerFeatureSettings();

    private ServerFeatureSettingsManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        settings = new ServerFeatureSettings();
        if (Files.notExists(SETTINGS_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
            ServerFeatureSettings loaded = FriendServerMenuMod.GSON.fromJson(reader, ServerFeatureSettings.class);
            settings = sanitize(loaded);
        } catch (Exception exception) {
            settings = new ServerFeatureSettings();
            save();
        }
        if (!settings.deathPointEnabled) {
            DeathPointManager.clearAllDeathPoints();
        }
    }

    public static synchronized ServerFeatureSettings settings() {
        return copy(settings);
    }

    public static synchronized boolean deathPointEnabled() {
        return settings.deathPointEnabled;
    }

    public static synchronized boolean deathPointChatEnabled() {
        return settings.deathPointChatEnabled;
    }

    public static synchronized boolean update(String key, boolean value) {
        if (!isKnownKey(key)) {
            return false;
        }
        switch (key) {
            case DEATH_POINT_ENABLED -> settings.deathPointEnabled = value;
            case DEATH_POINT_CHAT_ENABLED -> settings.deathPointChatEnabled = value;
            default -> {
                return false;
            }
        }
        save();
        return true;
    }

    private static boolean isKnownKey(String key) {
        return DEATH_POINT_ENABLED.equals(key) || DEATH_POINT_CHAT_ENABLED.equals(key);
    }

    private static ServerFeatureSettings sanitize(ServerFeatureSettings source) {
        return source == null ? new ServerFeatureSettings() : copy(source);
    }

    private static ServerFeatureSettings copy(ServerFeatureSettings source) {
        ServerFeatureSettings copy = new ServerFeatureSettings();
        if (source != null) {
            copy.deathPointEnabled = source.deathPointEnabled;
            copy.deathPointChatEnabled = source.deathPointChatEnabled;
        }
        return copy;
    }

    private static void save() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
                FriendServerMenuMod.GSON.toJson(settings, writer);
            }
        } catch (IOException ignored) {
            // Keep in-memory settings if the config directory cannot be written.
        }
    }

    public static class ServerFeatureSettings {
        public boolean deathPointEnabled = true;
        public boolean deathPointChatEnabled = true;
    }
}
