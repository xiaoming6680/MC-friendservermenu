package com.xm6680.friendservermenu.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public String menuTitle = "小铭的服务器菜单";
    public List<LocationEntry> locations = new ArrayList<>();

    public static ModConfig defaults() {
        return new ModConfig();
    }
}
