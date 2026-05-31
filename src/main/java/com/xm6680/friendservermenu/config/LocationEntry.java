package com.xm6680.friendservermenu.config;

public class LocationEntry {
    public String id;
    public String name;
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public String description;
    public String creatorUuid;
    public String creatorName;

    public LocationEntry() {
    }

    public LocationEntry(String id, String name, String world, double x, double y, double z, float yaw, float pitch, String description) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.description = description;
    }
}
