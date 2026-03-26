package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public final class WaypointParticles {
    private WaypointParticles() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (!WaypointStorage.isEnabled()) return;

        // Раз в 4 тика
        if ((mc.level.getGameTime() & 3L) != 0L) return;

        ClientLevel level = mc.level;

        for (WaypointStorage.Waypoint wp : WaypointStorage.listForCurrent(mc)) {
            double x = wp.x + 0.5;
            double y = wp.y + 1.1;
            double z = wp.z + 0.5;

            double dx = mc.player.getX() - x;
            double dy = mc.player.getY() - y;
            double dz = mc.player.getZ() - z;

            // Не рисуем слишком далеко
            if (dx * dx + dy * dy + dz * dz > 128.0 * 128.0) continue;

            DustParticleOptions dust = new DustParticleOptions(wp.color, 1.0f);

            int points = 16;
            double radius = 0.7;

            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0 * i) / points;
                double px = x + Math.cos(a) * radius;
                double pz = z + Math.sin(a) * radius;

                level.addParticle(dust, px, y, pz, 0.0, 0.0, 0.0);
            }

            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
        }
    }
}