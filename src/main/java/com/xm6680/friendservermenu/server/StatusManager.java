package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.FriendServerMenuMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class StatusManager {
    private StatusManager() {
    }

    public static String createStatusJson(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        MinecraftServer server = player.getCommandSource().getServer();
        BlockPos pos = player.getBlockPos();

        ServerStatus status = new ServerStatus();
        status.onlinePlayers = server.getCurrentPlayerCount();
        status.maxPlayers = server.getMaxPlayerCount();
        status.playerNames = server.getPlayerManager().getPlayerList().stream()
                .map(onlinePlayer -> onlinePlayer.getName().getString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toArray(String[]::new);
        status.dimension = formatDimension(world);
        status.x = pos.getX();
        status.y = pos.getY();
        status.z = pos.getZ();
        status.timeOfDay = world.getTimeOfDay() % 24000L;
        status.weather = weatherName(world);
        status.mspt = server.getAverageTickTime();
        status.tps = Math.min(20.0D, 1000.0D / Math.max(1.0D, status.mspt));
        status.deathPoint = DeathPointManager.getDeathPoint(player);
        return FriendServerMenuMod.GSON.toJson(status);
    }

    public static void broadcastCoordinates(ServerPlayerEntity player) {
        String shareId = CoordinateTeleportManager.createShare(player);
        MutableText message = Text.literal(player.getName().getString() + " 的坐标：" + formatCoordinates(player));
        message.append(Text.literal(" [点我传送]").styled(style -> style
                .withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/fsm_coord_tp " + shareId))));
        player.getCommandSource().getServer().getPlayerManager().broadcast(message, false);
    }

    public static void sendCoordinatesToSelf(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("你的坐标：" + formatCoordinates(player)), false);
    }

    public static String formatCoordinates(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        return "[" + formatDimension(player.getEntityWorld()) + "] X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ();
    }

    public static String formatDimension(ServerWorld world) {
        return formatDimensionId(world.getRegistryKey().getValue().toString());
    }

    public static String formatDimensionId(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id;
        };
    }

    private static String weatherName(ServerWorld world) {
        if (world.isThundering()) {
            return "雷暴";
        }
        if (world.isRaining()) {
            return "下雨";
        }
        return "晴天";
    }

    public static class ServerStatus {
        public int onlinePlayers;
        public int maxPlayers;
        public String[] playerNames;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public long timeOfDay;
        public String weather;
        public double mspt;
        public double tps;
        public DeathPointManager.ViewDeathPoint deathPoint;
    }
}
