package com.example.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class WaypointDeathTracker {
    private WaypointDeathTracker() {}

    private static boolean wasAlive = true;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            boolean alive = mc.player.isAlive();

            if (wasAlive && !alive) {
                WaypointStorage.addDeath(mc);
            }

            wasAlive = alive;
        });
    }
}
