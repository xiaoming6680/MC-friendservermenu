package com.xm6680.friendservermenu.server;

import com.xm6680.friendservermenu.network.ModNetworking;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminActionManager {
    private static final long TEMPORARY_FLIGHT_MILLIS = 10L * 60L * 1000L;
    private static final Map<UUID, Long> TEMPORARY_FLIGHT_EXPIRIES = new ConcurrentHashMap<>();

    private AdminActionManager() {
    }

    public static void setDay(ServerPlayerEntity player) {
        setTime(player, 1000L);
        player.sendMessage(Text.literal("已设置为白天。"), false);
    }

    public static void setNoon(ServerPlayerEntity player) {
        setTime(player, 6000L);
        player.sendMessage(Text.literal("已设置为正午。"), false);
    }

    public static void setNight(ServerPlayerEntity player) {
        setTime(player, 13000L);
        player.sendMessage(Text.literal("已设置为夜晚。"), false);
    }

    public static void setMidnight(ServerPlayerEntity player) {
        setTime(player, 18000L);
        player.sendMessage(Text.literal("已设置为午夜。"), false);
    }

    public static void clearWeather(ServerPlayerEntity player) {
        for (ServerWorld world : server(player).getWorlds()) {
            world.setWeather(6000, 0, false, false);
        }
        player.sendMessage(Text.literal("已设置晴天。"), false);
    }

    public static void setRain(ServerPlayerEntity player) {
        for (ServerWorld world : server(player).getWorlds()) {
            world.setWeather(0, 6000, true, false);
        }
        player.sendMessage(Text.literal("已设置下雨。"), false);
    }

    public static void setThunder(ServerPlayerEntity player) {
        for (ServerWorld world : server(player).getWorlds()) {
            world.setWeather(0, 6000, true, true);
        }
        player.sendMessage(Text.literal("已设置雷暴。"), false);
    }

    public static void teleportAllToPlayer(ServerPlayerEntity owner) {
        int moved = 0;
        MinecraftServer server = server(owner);
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            target.teleport(owner.getEntityWorld(), owner.getX(), owner.getY(), owner.getZ(), Set.of(), owner.getYaw(), owner.getPitch(), false);
            moved++;
        }
        server.getPlayerManager().broadcast(Text.literal("已将 " + moved + " 名在线玩家传送到 " + owner.getName().getString() + "。"), false);
    }

    public static void healAllPlayers(ServerPlayerEntity player) {
        int count = 0;
        for (ServerPlayerEntity target : server(player).getPlayerManager().getPlayerList()) {
            target.setHealth(target.getMaxHealth());
            target.extinguish();
            count++;
        }
        player.sendMessage(Text.literal("已恢复在线玩家生命：" + count + " 名。"), false);
    }

    public static void feedAllPlayers(ServerPlayerEntity player) {
        int count = 0;
        for (ServerPlayerEntity target : server(player).getPlayerManager().getPlayerList()) {
            target.getHungerManager().setFoodLevel(20);
            target.getHungerManager().setSaturationLevel(20.0F);
            count++;
        }
        player.sendMessage(Text.literal("已补满在线玩家饥饿值：" + count + " 名。"), false);
    }

    public static void setPlayerGameMode(ServerPlayerEntity actor, String argument) {
        TargetArgument parsed = TargetArgument.parse(argument);
        ServerPlayerEntity target = targetPlayer(actor, parsed.playerName());
        if (target == null) {
            return;
        }

        GameMode gameMode = switch (parsed.value()) {
            case "creative" -> GameMode.CREATIVE;
            case "adventure" -> GameMode.ADVENTURE;
            case "spectator" -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
        target.changeGameMode(gameMode);
        actor.sendMessage(Text.literal("已将 " + target.getName().getString() + " 的游戏模式切换为 " + gameMode.getId() + "。"), false);
    }

    public static void setPlayerFlight(ServerPlayerEntity actor, String argument) {
        TargetArgument parsed = TargetArgument.parse(argument);
        ServerPlayerEntity target = targetPlayer(actor, parsed.playerName());
        if (target == null) {
            return;
        }

        if ("revoke".equals(parsed.value())) {
            revokeTemporaryFlight(target);
            target.sendMessage(Text.literal("管理员已撤销你的临时飞行。"), false);
            actor.sendMessage(Text.literal("已撤销 " + target.getName().getString() + " 的临时飞行。"), false);
            return;
        }

        target.getAbilities().allowFlying = true;
        TEMPORARY_FLIGHT_EXPIRIES.put(target.getUuid(), System.currentTimeMillis() + TEMPORARY_FLIGHT_MILLIS);
        target.sendAbilitiesUpdate();
        target.sendMessage(Text.literal("管理员已暂时授予你 10 分钟飞行权限。"), false);
        actor.sendMessage(Text.literal("已暂时授予 " + target.getName().getString() + " 10 分钟飞行权限。"), false);
    }

    public static void teleportPlayerToActor(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        target.teleport(actor.getEntityWorld(), actor.getX(), actor.getY(), actor.getZ(), Set.of(), actor.getYaw(), actor.getPitch(), false);
        target.sendMessage(Text.literal("管理员已将你传送到 " + actor.getName().getString() + "。"), false);
        actor.sendMessage(Text.literal("已将 " + target.getName().getString() + " 传送到你的位置。"), false);
    }

    public static void teleportActorToPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        actor.teleport(target.getEntityWorld(), target.getX(), target.getY(), target.getZ(), Set.of(), target.getYaw(), target.getPitch(), false);
        actor.sendMessage(Text.literal("已传送到 " + target.getName().getString() + "。"), false);
    }

    public static void healPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        target.setHealth(target.getMaxHealth());
        target.extinguish();
        target.sendMessage(Text.literal("管理员已为你恢复生命。"), false);
        actor.sendMessage(Text.literal("已恢复 " + target.getName().getString() + " 的生命。"), false);
    }

    public static void feedPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        target.getHungerManager().setFoodLevel(20);
        target.getHungerManager().setSaturationLevel(20.0F);
        target.sendMessage(Text.literal("管理员已为你补满饥饿值。"), false);
        actor.sendMessage(Text.literal("已补满 " + target.getName().getString() + " 的饥饿值。"), false);
    }

    public static void clearPlayerEffects(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        boolean cleared = target.clearStatusEffects();
        target.sendMessage(Text.literal("管理员已清除你的状态效果。"), false);
        actor.sendMessage(Text.literal(cleared
                ? "已清除 " + target.getName().getString() + " 的状态效果。"
                : target.getName().getString() + " 没有可清除的状态效果。"), false);
    }

    public static void extinguishPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }

        target.extinguish();
        target.sendMessage(Text.literal("管理员已为你熄灭火焰。"), false);
        actor.sendMessage(Text.literal("已为 " + target.getName().getString() + " 熄灭火焰。"), false);
    }

    public static void kickPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }
        if (target.getUuid().equals(actor.getUuid())) {
            actor.sendMessage(Text.literal("不能从 GUI 踢出自己。"), false);
            return;
        }

        target.networkHandler.disconnect(Text.literal("你已被管理员踢出服务器。"));
        actor.sendMessage(Text.literal("已踢出玩家：" + target.getName().getString()), false);
    }

    public static void banPlayer(ServerPlayerEntity actor, String playerName) {
        ServerPlayerEntity target = targetPlayer(actor, playerName);
        if (target == null) {
            return;
        }
        if (target.getUuid().equals(actor.getUuid())) {
            actor.sendMessage(Text.literal("不能从 GUI 封禁自己。"), false);
            return;
        }

        PlayerManager playerManager = server(actor).getPlayerManager();
        PlayerConfigEntry entry = new PlayerConfigEntry(target.getGameProfile());
        playerManager.getUserBanList().add(new BannedPlayerEntry(entry, new Date(), actor.getName().getString(), null, "由小铭的服务器菜单封禁"));
        trySaveBanList(actor, playerManager);
        target.networkHandler.disconnect(Text.literal("你已被服务器封禁。"));
        actor.sendMessage(Text.literal("已封禁玩家：" + target.getName().getString()), false);
    }

    public static void setPlayerOperator(ServerPlayerEntity actor, String argument, boolean grant) {
        TargetArgument parsed = TargetArgument.parse(argument);
        ServerPlayerEntity target = targetPlayer(actor, parsed.playerName());
        if (target == null) {
            return;
        }
        if (!grant && target.getUuid().equals(actor.getUuid())) {
            actor.sendMessage(Text.literal("不能从 GUI 撤销自己的 OP 权限。"), false);
            return;
        }

        PlayerManager playerManager = server(actor).getPlayerManager();
        PlayerConfigEntry entry = new PlayerConfigEntry(target.getGameProfile());
        if (grant) {
            int level = clampOpLevel(parsed.value());
            LeveledPermissionPredicate permission = LeveledPermissionPredicate.fromLevel(PermissionLevel.fromLevel(level));
            playerManager.addToOperators(entry, Optional.of(permission), Optional.of(false));
            actor.sendMessage(Text.literal("已给予 " + target.getName().getString() + " OP " + level + " 权限。"), false);
        } else {
            playerManager.removeFromOperators(entry);
            actor.sendMessage(Text.literal("已撤销 " + target.getName().getString() + " 的 OP 权限。"), false);
        }
        playerManager.sendCommandTree(target);
        ModNetworking.broadcastMenuData(server(actor));
    }

    public static void tickFlightGrants(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = TEMPORARY_FLIGHT_EXPIRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now < entry.getValue()) {
                continue;
            }

            iterator.remove();
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
            if (target != null) {
                revokeTemporaryFlight(target);
                target.sendMessage(Text.literal("你的临时飞行权限已到期。"), false);
            }
        }
    }

    public static void clearItems(ServerPlayerEntity player) {
        int count = discardEntities(server(player), ItemEntity.class);
        player.sendMessage(Text.literal("已清理掉落物：" + count + " 个。"), false);
    }

    public static void clearXpOrbs(ServerPlayerEntity player) {
        int count = discardEntities(server(player), ExperienceOrbEntity.class);
        player.sendMessage(Text.literal("已清理经验球：" + count + " 个。"), false);
    }

    public static void clearArrows(ServerPlayerEntity player) {
        int count = discardEntities(server(player), PersistentProjectileEntity.class);
        player.sendMessage(Text.literal("已清理箭矢/投射物：" + count + " 个。"), false);
    }

    public static void clearHostiles(ServerPlayerEntity player) {
        int count = discardEntities(server(player), HostileEntity.class);
        player.sendMessage(Text.literal("已清理敌对生物：" + count + " 个。"), false);
    }

    private static void setTime(ServerPlayerEntity player, long timeOfDay) {
        for (ServerWorld world : server(player).getWorlds()) {
            world.setTimeOfDay(timeOfDay);
        }
    }

    private static <T extends Entity> int discardEntities(MinecraftServer server, Class<T> entityClass) {
        int count = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<? extends T> entities = world.getEntitiesByType(TypeFilter.instanceOf(entityClass), Entity::isAlive);
            for (T entity : entities) {
                entity.discard();
                count++;
            }
        }
        return count;
    }

    private static ServerPlayerEntity targetPlayer(ServerPlayerEntity actor, String playerName) {
        String targetName = playerName == null ? "" : playerName.trim();
        if (targetName.isBlank()) {
            actor.sendMessage(Text.literal("请选择在线玩家。"), false);
            return null;
        }

        ServerPlayerEntity target = server(actor).getPlayerManager().getPlayer(targetName);
        if (target == null) {
            actor.sendMessage(Text.literal("玩家不在线：" + targetName), false);
            return null;
        }
        return target;
    }

    private static void revokeTemporaryFlight(ServerPlayerEntity target) {
        TEMPORARY_FLIGHT_EXPIRIES.remove(target.getUuid());
        if (!target.getGameMode().isCreative() && target.getGameMode() != GameMode.SPECTATOR) {
            target.getAbilities().allowFlying = false;
            target.getAbilities().flying = false;
            target.sendAbilitiesUpdate();
        }
    }

    private static void trySaveBanList(ServerPlayerEntity actor, PlayerManager playerManager) {
        try {
            playerManager.getUserBanList().save();
        } catch (IOException exception) {
            actor.sendMessage(Text.literal("封禁名单保存失败，请检查服务器目录权限。"), false);
        }
    }

    private static MinecraftServer server(ServerPlayerEntity player) {
        return player.getCommandSource().getServer();
    }

    private static int clampOpLevel(String value) {
        try {
            int parsed = Integer.parseInt(value == null || value.isBlank() ? "2" : value.trim());
            return Math.max(1, Math.min(4, parsed));
        } catch (NumberFormatException exception) {
            return 2;
        }
    }

    private record TargetArgument(String playerName, String value) {
        static TargetArgument parse(String argument) {
            String[] parts = (argument == null ? "" : argument).split("\\|", 2);
            String target = parts.length > 0 ? parts[0].trim() : "";
            String value = parts.length > 1 ? parts[1].trim().toLowerCase() : "";
            return new TargetArgument(target, value);
        }
    }
}
