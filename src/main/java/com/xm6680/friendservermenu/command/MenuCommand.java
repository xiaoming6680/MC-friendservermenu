package com.xm6680.friendservermenu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xm6680.friendservermenu.network.ModNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MenuCommand {
    private MenuCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("menu")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            ModNetworking.sendMenu(player, "");
                            return 1;
                        })
                        .then(argument("page", StringArgumentType.word()).executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            ModNetworking.sendMenu(player, StringArgumentType.getString(context, "page"));
                            return 1;
                        }))));
    }
}
