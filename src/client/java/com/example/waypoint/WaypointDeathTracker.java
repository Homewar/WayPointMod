package com.example.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class WaypointDeathTracker {
    private WaypointDeathTracker() {}

    private static boolean wasAlive = true;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null || client.world == null) return;

            boolean alive = client.player.isAlive();

            if (wasAlive && !alive) {
                WaypointStorage.addDeath(client);
            }

            wasAlive = alive;
        });
    }
}
