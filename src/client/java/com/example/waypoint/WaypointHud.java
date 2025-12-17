package com.example.waypoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class WaypointHud {
    private WaypointHud() {}

    private static final int PAGE_SIZE = 5;
    private static int page = 0;

    private static KeyMapping nextPage;
    private static KeyMapping prevPage;

    // ВАЖНО: поле должно быть на уровне класса, не внутри register()
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "waypointmod"));

    public static void register() {
        nextPage = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waypointmod.hud_next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                CATEGORY
        ));

        prevPage = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waypointmod.hud_prev",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextPage.consumeClick()) page++;
            while (prevPage.consumeClick()) page--;
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "waypoint_hud"),
                WaypointHud::render
        );
    }

    private static void render(GuiGraphics g, DeltaTracker tickCounter) {
        if (!WaypointStorage.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        List<WaypointStorage.Waypoint> list = WaypointStorage.listForCurrent(mc);
        if (list.isEmpty()) return;

        list = list.stream()
                .sorted(Comparator.comparingDouble(wp -> distSq(mc, wp)))
                .toList();

        int total = list.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);

        if (page < 0) page = 0;
        if (page > pages - 1) page = pages - 1;

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);

        int x = 8;
        int y = 8;

        String header = "Waypoints " + (page + 1) + "/" + pages + "  (PgUp/PgDn)";
        g.drawString(mc.font, header, x, y, 0xFFFFFFFF, true);
        y += 12;

        for (int i = from; i < to; i++) {
            var wp = list.get(i);
            int dist = (int) Math.round(Math.sqrt(distSq(mc, wp)));
            String arrow = arrowTo(mc, wp);
            String line = arrow + " " + wp.name + " [" + dist + "m]";

            int argb = 0xFF000000 | (wp.color & 0xFFFFFF);
            g.drawString(mc.font, line, x, y, argb, true);
            y += 10;
        }
    }

    private static double distSq(Minecraft mc, WaypointStorage.Waypoint wp) {
        double dx = (wp.x + 0.5) - mc.player.getX();
        double dy = (wp.y) - mc.player.getY();
        double dz = (wp.z + 0.5) - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String arrowTo(Minecraft mc, WaypointStorage.Waypoint wp) {
        double dx = (wp.x + 0.5) - mc.player.getX();
        double dz = (wp.z + 0.5) - mc.player.getZ();

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float playerYaw = mc.player.getYRot();
        float delta = Mth.wrapDegrees(targetYaw - playerYaw);

        if (delta >= -22.5f && delta < 22.5f) return "↑";
        if (delta >= 22.5f && delta < 67.5f) return "↗";
        if (delta >= 67.5f && delta < 112.5f) return "→";
        if (delta >= 112.5f && delta < 157.5f) return "↘";
        if (delta >= 157.5f || delta < -157.5f) return "↓";
        if (delta >= -157.5f && delta < -112.5f) return "↙";
        if (delta >= -112.5f && delta < -67.5f) return "←";
        return "↖";
    }
}
