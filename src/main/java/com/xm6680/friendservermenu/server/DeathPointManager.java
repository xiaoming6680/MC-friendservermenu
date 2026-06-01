package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DeathPointManager {
    private static final long DEATH_POINT_LIFETIME_MILLIS = 5L * 60L * 1000L;
    private static final Path DEATH_POINTS_PATH = FabricLoader.getInstance().getConfigDir().resolve("friendservermenu-death-points.json");
    private static final Map<String, DeathPoint> DEATH_POINTS = new LinkedHashMap<>();

    private DeathPointManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        DEATH_POINTS.clear();
        if (Files.notExists(DEATH_POINTS_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(DEATH_POINTS_PATH)) {
            DeathPointStore store = FriendServerMenuMod.GSON.fromJson(reader, DeathPointStore.class);
            if (store == null || store.deathPoints == null) {
                return;
            }
            for (DeathPoint point : store.deathPoints) {
                DeathPoint sanitized = sanitize(point);
                if (sanitized != null) {
                    DEATH_POINTS.put(sanitized.ownerUuid, sanitized);
                }
            }
        } catch (Exception exception) {
            DEATH_POINTS.clear();
        }
    }

    public static synchronized void recordDeath(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        DeathPoint point = new DeathPoint();
        point.ownerUuid = player.getUuid().toString();
        point.ownerName = player.getName().getString();
        point.world = world.getRegistryKey().getValue().toString();
        point.x = pos.getX();
        point.y = pos.getY();
        point.z = pos.getZ();
        point.createdAtMillis = System.currentTimeMillis();

        DEATH_POINTS.put(point.ownerUuid, point);
        save();
        MutableText message = Text.literal("刚刚的死亡点：" + format(point)).formatted(Formatting.RED);
        message.append(Text.literal(" [点我传送]").styled(style -> style
                .withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/fsm_death_tp"))));
        player.sendMessage(message, false);
    }

    public static synchronized ViewDeathPoint getDeathPoint(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        DeathPoint point = DEATH_POINTS.get(player.getUuid().toString());
        if (point == null) {
            return null;
        }
        if (isExpired(point)) {
            DEATH_POINTS.remove(player.getUuid().toString());
            save();
            return null;
        }
        return ViewDeathPoint.from(point);
    }

    public static synchronized void deleteDeathPoint(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        DeathPoint removed = DEATH_POINTS.remove(player.getUuid().toString());
        if (removed == null) {
            player.sendMessage(Text.literal("当前没有可删除的死亡点。"), false);
            return;
        }
        save();
        player.sendMessage(Text.literal("死亡点已删除。"), false);
    }

    public static void teleportToDeathPoint(ServerPlayerEntity player) {
        DeathPoint point;
        synchronized (DeathPointManager.class) {
            if (player == null) {
                return;
            }
            point = DEATH_POINTS.get(player.getUuid().toString());
            if (point == null) {
                player.sendMessage(Text.literal("当前没有可传送的死亡点。"), false);
                return;
            }
            if (isExpired(point)) {
                DEATH_POINTS.remove(player.getUuid().toString());
                save();
                player.sendMessage(Text.literal("死亡点已超过 5 分钟，已自动失效。"), false);
                return;
            }
        }

        Identifier worldId = Identifier.tryParse(safe(point.world));
        if (worldId == null) {
            player.sendMessage(Text.literal("死亡点维度无效。"), false);
            return;
        }

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        ServerWorld world = player.getCommandSource().getServer().getWorld(worldKey);
        if (world == null) {
            player.sendMessage(Text.literal("服务器未加载死亡点所在维度。"), false);
            return;
        }

        player.teleport(world, point.x + 0.5D, point.y, point.z + 0.5D, Set.of(), player.getYaw(), player.getPitch(), false);
        synchronized (DeathPointManager.class) {
            DEATH_POINTS.remove(player.getUuid().toString());
            save();
        }
        player.sendMessage(Text.literal("已传送到最近死亡点。"), false);
    }

    private static void save() {
        try {
            Files.createDirectories(DEATH_POINTS_PATH.getParent());
            DeathPointStore store = new DeathPointStore();
            store.deathPoints.addAll(DEATH_POINTS.values());
            try (Writer writer = Files.newBufferedWriter(DEATH_POINTS_PATH)) {
                FriendServerMenuMod.GSON.toJson(store, writer);
            }
        } catch (IOException ignored) {
            // Death point persistence is best effort; keep the in-memory point available.
        }
    }

    private static DeathPoint sanitize(DeathPoint point) {
        if (point == null || safe(point.ownerUuid).isBlank() || safe(point.world).isBlank()) {
            return null;
        }
        DeathPoint sanitized = new DeathPoint();
        sanitized.ownerUuid = clean(point.ownerUuid, 64);
        sanitized.ownerName = clean(point.ownerName, 40);
        sanitized.world = clean(point.world, 80);
        sanitized.x = point.x;
        sanitized.y = point.y;
        sanitized.z = point.z;
        sanitized.createdAtMillis = point.createdAtMillis;
        return sanitized;
    }

    private static boolean isExpired(DeathPoint point) {
        return point == null || point.createdAtMillis + DEATH_POINT_LIFETIME_MILLIS <= System.currentTimeMillis();
    }

    private static String format(DeathPoint point) {
        return "[" + StatusManager.formatDimensionId(safe(point.world)) + "] X: " + point.x + ", Y: " + point.y + ", Z: " + point.z;
    }

    private static String clean(String value, int maxLength) {
        String safeValue = safe(value).trim();
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class DeathPointStore {
        ArrayList<DeathPoint> deathPoints = new ArrayList<>();
    }

    private static class DeathPoint {
        String ownerUuid;
        String ownerName;
        String world;
        int x;
        int y;
        int z;
        long createdAtMillis;
    }

    public static class ViewDeathPoint {
        public String world;
        public int x;
        public int y;
        public int z;
        public long createdAtMillis;

        static ViewDeathPoint from(DeathPoint point) {
            ViewDeathPoint view = new ViewDeathPoint();
            view.world = point.world;
            view.x = point.x;
            view.y = point.y;
            view.z = point.z;
            view.createdAtMillis = point.createdAtMillis;
            return view;
        }
    }
}
