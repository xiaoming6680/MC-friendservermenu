package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.config.LocationEntry;
import com.xm6680.friendservermenu.config.ModConfigManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Set;

public final class TeleportManager {
    private TeleportManager() {
    }

    public static void teleportToLocation(ServerPlayerEntity player, String locationId) {
        LocationEntry location = ModConfigManager.findLocation(locationId);
        if (location == null) {
            player.sendMessage(Text.literal("地点不存在或配置已变更。"), false);
            return;
        }

        Identifier worldId = Identifier.tryParse(location.world);
        if (worldId == null) {
            player.sendMessage(Text.literal("地点维度配置无效：" + location.world), false);
            return;
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        ServerWorld world = player.getCommandSource().getServer().getWorld(worldKey);
        if (world == null) {
            player.sendMessage(Text.literal("服务器未加载维度：" + location.world), false);
            return;
        }

        player.teleport(world, location.x, location.y, location.z, Set.of(), location.yaw, location.pitch, false);
        player.sendMessage(Text.literal("已传送到 " + location.name + "。"), false);
    }
}
