package com.xm6680.friendservermenu;

import com.xm6680.friendservermenu.client.ClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class FriendServerMenuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FriendServerMenuMod.registerSoundEvents();
        ClientNetworking.register();
    }
}
