package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;

public final class WaypointWorldKey {
    private WaypointWorldKey() {}

    public static String get(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT);
            return "sp:" + root.toAbsolutePath().normalize();
        }

        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return "mp:" + data.ip.trim().toLowerCase(Locale.ROOT);
        }

        return "unknown";
    }
}
