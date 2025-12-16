package com.example.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class WaypointHud {
    private WaypointHud() {}

    // Сколько меток на страницу
    private static final int PAGE_SIZE = 5;

    private static int page = 0;

    private static KeyBinding nextPage;
    private static KeyBinding prevPage;

    public static void register() {
        // keybinds
        nextPage = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointmod.hud_next",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                "category.waypointmod"
        ));

        prevPage = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waypointmod.hud_prev",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                "category.waypointmod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextPage.wasPressed()) page++;
            while (prevPage.wasPressed()) page--;
        });

        // HUD render (с сигнатурой RenderTickCounter)
        HudRenderCallback.EVENT.register((DrawContext drawContext, net.minecraft.client.render.RenderTickCounter tickCounter) -> {
            if (!WaypointStorage.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            List<WaypointStorage.Waypoint> list = WaypointStorage.listForCurrent(client);
            if (list.isEmpty()) return;

            // сортируем по дистанции
            list = list.stream()
                    .sorted(Comparator.comparingDouble(wp -> distSq(client, wp)))
                    .toList();

            int total = list.size();
            int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);

            // clamp page
            if (page < 0) page = 0;
            if (page > pages - 1) page = pages - 1;

            int from = page * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, total);

            int x = 8;
            int y = 8;

            // Заголовок + подсказка клавиш
            String header = "Waypoints " + (page + 1) + "/" + pages + "  (PgUp/PgDn)";
            drawContext.drawTextWithShadow(client.textRenderer, header, x, y, 0xFFFFFFFF);
            y += 12;

            for (int i = from; i < to; i++) {
                var wp = list.get(i);
                int dist = (int) Math.round(Math.sqrt(distSq(client, wp)));
                String arrow = arrowTo(client, wp);
                String line = arrow + " " + wp.name + " [" + dist + "m]";

                int argb = 0xFF000000 | (wp.color & 0xFFFFFF);
                drawContext.drawTextWithShadow(client.textRenderer, line, x, y, argb);
                y += 10;
            }
        });
    }

    private static double distSq(MinecraftClient client, WaypointStorage.Waypoint wp) {
        double dx = (wp.x + 0.5) - client.player.getX();
        double dy = (wp.y) - client.player.getY();
        double dz = (wp.z + 0.5) - client.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String arrowTo(MinecraftClient client, WaypointStorage.Waypoint wp) {
        double dx = (wp.x + 0.5) - client.player.getX();
        double dz = (wp.z + 0.5) - client.player.getZ();

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float playerYaw = client.player.getYaw();
        float delta = MathHelper.wrapDegrees(targetYaw - playerYaw);

        if (delta >= -22.5 && delta < 22.5) return "↑";
        if (delta >= 22.5 && delta < 67.5) return "↗";
        if (delta >= 67.5 && delta < 112.5) return "→";
        if (delta >= 112.5 && delta < 157.5) return "↘";
        if (delta >= 157.5 || delta < -157.5) return "↓";
        if (delta >= -157.5 && delta < -112.5) return "↙";
        if (delta >= -112.5 && delta < -67.5) return "←";
        return "↖";
    }
}
