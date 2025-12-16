package com.example.waypoint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.WorldSavePath;

import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Locale;

public final class WaypointWorldKey {
    private WaypointWorldKey() {}

    public static String get(MinecraftClient client) {
        // Singleplayer: уникально по папке сейва
        var server = client.getServer();
        if (server != null) {
            Path root = server.getSavePath(WorldSavePath.ROOT);
            return "sp:" + root.toAbsolutePath().normalize();
        }

        // Multiplayer: адрес сервера из списка серверов
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return "mp:" + info.address.trim().toLowerCase(Locale.ROOT);
        }

        // Fallback: адрес активного соединения (если ServerInfo == null)
        if (client.getNetworkHandler() != null && client.getNetworkHandler().getConnection() != null) {
            SocketAddress addr = client.getNetworkHandler().getConnection().getAddress();
            if (addr != null) return "mp:" + addr.toString();
        }

        return "unknown";
    }
}
