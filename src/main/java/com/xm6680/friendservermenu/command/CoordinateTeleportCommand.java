package com.xm6680.friendservermenu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xm6680.friendservermenu.server.CoordinateTeleportManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CoordinateTeleportCommand {
    private CoordinateTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("fsm_coord_tp")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    CoordinateTeleportManager.teleportToShare(player, StringArgumentType.getString(context, "id"));
                                    return 1;
                                }))));
    }
}
