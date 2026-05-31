package com.xm6680.friendservermenu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xm6680.friendservermenu.server.ActivityManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ActivityClaimCommand {
    private ActivityClaimCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("fsm_activity_claim")
                        .then(argument("activityId", StringArgumentType.word()).executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            ActivityManager.claimItem(player, StringArgumentType.getString(context, "activityId"));
                            return 1;
                        }))));
    }
}
