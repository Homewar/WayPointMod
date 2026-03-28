package com.example.waypoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class WaypointHud {
    private WaypointHud() {
    }

    private static final int PAGE_SIZE = 5;
    private static int page = 0;

    private static KeyMapping nextPage;
    private static KeyMapping prevPage;

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "waypointmod"));

    public static void register() {
        nextPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointmod.hud_next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                CATEGORY));

        prevPage = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.waypointmod.hud_prev",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (nextPage.consumeClick())
                page++;
            while (prevPage.consumeClick())
                page--;
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "waypoint_hud"),
                WaypointHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
        if (!WaypointStorage.isEnabled())
            return;
        if (!WaypointStorage.isHudEnabled())
            return;
        if (!WaypointStorage.isWaypointHudEnabled())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        // ВАЖНО: берём all, чтобы favorites учитывались всегда одинаково.
        // Hidden отфильтруем тут.
        List<WaypointStorage.Waypoint> src = WaypointStorage.listForCurrentAll(mc);
        if (src.isEmpty())
            return;

        ArrayList<WaypointStorage.Waypoint> list = new ArrayList<>(src.size());
        for (var w : src) {
            if (w == null)
                continue;
            if (w.hidden)
                continue; // hidden скрывает в HUD
            list.add(w);
        }
        if (list.isEmpty())
            return;

        // Сортировка: избранные первыми, внутри групп — по дистанции
        list.sort((a, b) -> {
            if (a.favorite != b.favorite)
                return a.favorite ? -1 : 1;
            return Double.compare(distSq(mc, a), distSq(mc, b));
        });

        int total = list.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);

        page = Mth.clamp(page, 0, pages - 1);

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);

        int x = 8;
        int y = 8;

        String header = "Waypoints " + (page + 1) + "/" + pages;
        g.text(mc.font, header, x, y, 0xFFFFFFFF, true);
        y += 12;

        for (int i = from; i < to; i++) {
            var wp = list.get(i);
            int dist = (int) Math.round(Math.sqrt(distSq(mc, wp)));
            String arrow = arrowTo(mc, wp);

            String name = (wp.name == null) ? Component.translatable("screen.waypointmod.unnamed").getString() : wp.name;
            String star = wp.favorite ? "★ " : "";
            String line = arrow + " " + star + name + Component.translatable("hud.waypointmod.distance_brackets", dist).getString();

            int argb = 0xFF000000 | (wp.color & 0xFFFFFF);
            g.text(mc.font, line, x, y, argb, true);
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

        if (delta >= -22.5f && delta < 22.5f)
            return "↑";
        if (delta >= 22.5f && delta < 67.5f)
            return "↗";
        if (delta >= 67.5f && delta < 112.5f)
            return "→";
        if (delta >= 112.5f && delta < 157.5f)
            return "↘";
        if (delta >= 157.5f || delta < -157.5f)
            return "↓";
        if (delta >= -157.5f && delta < -112.5f)
            return "↙";
        if (delta >= -112.5f && delta < -67.5f)
            return "←";
        return "↖";
    }
}
