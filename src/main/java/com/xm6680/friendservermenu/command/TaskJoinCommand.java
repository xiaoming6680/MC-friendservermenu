package com.xm6680.friendservermenu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xm6680.friendservermenu.server.TaskManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TaskJoinCommand {
    private TaskJoinCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("fsm_task_join")
                        .then(argument("taskId", StringArgumentType.word()).executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            return TaskManager.joinTaskFromCommand(player, StringArgumentType.getString(context, "taskId"));
                        }))));
    }
}
