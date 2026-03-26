package com.example.waypoint;

import net.minecraft.client.Minecraft;

public final class WaypointDeathTracker {
    private WaypointDeathTracker() {}

    private static boolean wasAlive = true;

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean alive = mc.player.isAlive();
        if (wasAlive && !alive) {
            WaypointStorage.addDeath(mc);
        }
        wasAlive = alive;
    }
}
