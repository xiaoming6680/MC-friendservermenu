package com.xm6680.friendservermenu.command;

import com.xm6680.friendservermenu.server.DeathPointManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.literal;

public final class DeathPointTeleportCommand {
    private DeathPointTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("fsm_death_tp")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            DeathPointManager.teleportToDeathPoint(player);
                            return 1;
                        })));
    }
}
