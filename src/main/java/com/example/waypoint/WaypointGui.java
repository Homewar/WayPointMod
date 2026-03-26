package com.example.waypoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class WaypointGui {
    private WaypointGui() {}

    private static KeyMapping openGui;
    private static KeyMapping quickCreate;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openGui = new KeyMapping(
                "key.waypointmod.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                WaypointHud.CATEGORY
        );
        quickCreate = new KeyMapping(
                "key.waypointmod.quick_create",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                WaypointHud.CATEGORY
        );

        event.register(openGui);
        event.register(quickCreate);
    }

    public static void onClientTick() {
        if (openGui == null || quickCreate == null) return;

        while (openGui.consumeClick()) {
            Minecraft.getInstance().setScreen(new WaypointScreen());
        }

        while (quickCreate.consumeClick()) {
            openQuickCreateScreen();
        }
    }

    private static void openQuickCreateScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int x = Mth.floor(mc.player.getX());
        int y = Mth.floor(mc.player.getY());
        int z = Mth.floor(mc.player.getZ());

        String name = x + " " + y + " " + z;
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

        mc.setScreen(new WaypointEditScreen(mc.screen, null, draft));
    }
}
