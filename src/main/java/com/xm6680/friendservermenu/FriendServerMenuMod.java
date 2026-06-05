package com.xm6680.friendservermenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xm6680.friendservermenu.command.ActivityClaimCommand;
import com.xm6680.friendservermenu.command.AdminMenuCommand;
import com.xm6680.friendservermenu.command.CoordinateTeleportCommand;
import com.xm6680.friendservermenu.command.DeathPointTeleportCommand;
import com.xm6680.friendservermenu.command.MenuCommand;
import com.xm6680.friendservermenu.command.TaskJoinCommand;
import com.xm6680.friendservermenu.config.ModConfigManager;
import com.xm6680.friendservermenu.network.ModNetworking;
import com.xm6680.friendservermenu.server.AdminActionManager;
import com.xm6680.friendservermenu.server.ActivityManager;
import com.xm6680.friendservermenu.server.DeathPointManager;
import com.xm6680.friendservermenu.server.PlayerSettingsManager;
import com.xm6680.friendservermenu.server.ServerFeatureSettingsManager;
import com.xm6680.friendservermenu.server.TaskManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class FriendServerMenuMod implements ModInitializer {
    public static final String MOD_ID = "friendservermenu";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Identifier GUI_CLICK_ID = id("gui_click");
    public static final SoundEvent GUI_CLICK_SOUND = SoundEvent.of(GUI_CLICK_ID);

    @Override
    public void onInitialize() {
        registerSoundEvents();
        ModConfigManager.load();
        ModNetworking.registerCommon();
        ServerLifecycleEvents.SERVER_STARTED.register(TaskManager::load);
        ServerLifecycleEvents.SERVER_STARTED.register(DeathPointManager::load);
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerSettingsManager::load);
        ServerLifecycleEvents.SERVER_STARTED.register(ServerFeatureSettingsManager::load);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                DeathPointManager.recordDeath(player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(AdminActionManager::tickFlightGrants);
        ServerTickEvents.END_SERVER_TICK.register(ActivityManager::tickActivities);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    ActivityManager.notifyActiveActivityOnJoin(handler.player);
                    ActivityManager.tryAutoClaimForPlayer(handler.player);
                    TaskManager.sendTasksOnJoin(handler.player);
                    ModNetworking.sendPlayerSettings(handler.player);
                    ModNetworking.sendServerFeatureSettings(handler.player);
                    if (ModConfigManager.needsInitialization() && ModNetworking.canUseAdmin(handler.player)) {
                        ModNetworking.sendMenu(handler.player, "setup");
                    }
                }));
        MenuCommand.register();
        AdminMenuCommand.register();
        ActivityClaimCommand.register();
        CoordinateTeleportCommand.register();
        DeathPointTeleportCommand.register();
        TaskJoinCommand.register();
    }

    public static void registerSoundEvents() {
        if (!Registries.SOUND_EVENT.containsId(GUI_CLICK_ID)) {
            Registry.register(Registries.SOUND_EVENT, GUI_CLICK_ID, GUI_CLICK_SOUND);
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
