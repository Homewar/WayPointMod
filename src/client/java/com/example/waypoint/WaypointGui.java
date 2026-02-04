package com.example.waypoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class WaypointGui {
    private WaypointGui() {}

    private static KeyMapping openGui;          // M
    private static KeyMapping quickCreate;      // B

    public static void register() {
        // Открыть список меток (M)
        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waypointmod.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                WaypointHud.CATEGORY
        ));

        // Быстрое создание метки (B)
        quickCreate = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waypointmod.quick_create",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                WaypointHud.CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.consumeClick()) {
                Minecraft.getInstance().setScreen(new WaypointScreen());
            }

            while (quickCreate.consumeClick()) {
                openQuickCreateScreen();
            }
        });
    }

    private static void openQuickCreateScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int x = Mth.floor(mc.player.getX());
        int y = Mth.floor(mc.player.getY());
        int z = Mth.floor(mc.player.getZ());

        // имя = координаты
        String name = x + " " + y + " " + z;

        // цвет = рандом
        int color = new Random().nextInt(0x1000000) & 0xFFFFFF;

        WaypointStorage.Waypoint draft = new WaypointStorage.Waypoint(
                name,
                WaypointStorage.currentWorldId(mc),
                WaypointStorage.currentDimId(mc),
                x, y, z,
                color
        );
        draft.kind = "normal";
        draft.createdAt = System.currentTimeMillis();
        draft.hidden = false;
        draft.favorite = false;

        // Открываем редактор как "создание" (originalNameOrNull = null)
        mc.setScreen(new WaypointEditScreen(mc.screen, null, draft));
    }
}