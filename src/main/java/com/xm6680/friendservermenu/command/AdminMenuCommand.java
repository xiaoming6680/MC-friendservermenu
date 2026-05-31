package com.xm6680.friendservermenu.command;

import com.xm6680.friendservermenu.network.ModNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.literal;

public final class AdminMenuCommand {
    private AdminMenuCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("adminmenu")
                        .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            ModNetworking.sendMenu(player, "admin");
                            return 1;
                        })));
    }
}
