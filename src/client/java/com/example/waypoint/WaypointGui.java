package com.example.waypoint;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class WaypointGui {
    private WaypointGui() {}

    private static KeyMapping openGui;

    public static void register() {
        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.waypointmod.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                WaypointHud.CATEGORY // <-- важно: используем уже зарегистрированную категорию
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.consumeClick()) {
                Minecraft.getInstance().setScreen(new WaypointScreen());
            }
        });
    }
}
