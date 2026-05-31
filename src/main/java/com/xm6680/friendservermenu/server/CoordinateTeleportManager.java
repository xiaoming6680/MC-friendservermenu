package com.xm6680.friendservermenu.server;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CoordinateTeleportManager {
    private static final long SHARE_LIFETIME_MILLIS = 5L * 60L * 1000L;
    private static final Map<String, CoordinateShare> SHARES = new HashMap<>();
    private static long nextId = 1L;

    private CoordinateTeleportManager() {
    }

    public static synchronized String createShare(ServerPlayerEntity player) {
        pruneExpired();
        String id = Long.toString(nextId++, 36);
        SHARES.put(id, new CoordinateShare(
                player.getEntityWorld().getRegistryKey().getValue().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch(),
                player.getName().getString(),
                System.currentTimeMillis() + SHARE_LIFETIME_MILLIS
        ));
        return id;
    }

    public static void teleportToShare(ServerPlayerEntity player, String id) {
        CoordinateShare share;
        synchronized (CoordinateTeleportManager.class) {
            pruneExpired();
            share = SHARES.get(id);
        }

        if (share == null) {
            player.sendMessage(Text.literal("这个坐标传送按钮已失效。"), false);
            return;
        }

        Identifier worldId = Identifier.tryParse(share.worldId);
        if (worldId == null) {
            player.sendMessage(Text.literal("坐标维度无效。"), false);
            return;
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        ServerWorld world = player.getCommandSource().getServer().getWorld(worldKey);
        if (world == null) {
            player.sendMessage(Text.literal("服务器未加载这个坐标所在维度。"), false);
            return;
        }

        player.teleport(world, share.x, share.y, share.z, Set.of(), share.yaw, share.pitch, false);
        player.sendMessage(Text.literal("已传送到 " + share.ownerName + " 广播的坐标。"), false);
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        SHARES.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
    }

    private record CoordinateShare(String worldId, double x, double y, double z, float yaw, float pitch, String ownerName, long expiresAtMillis) {
    }
}
