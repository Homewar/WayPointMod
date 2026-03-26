package com.example.waypoint;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class WaypointForgeClient {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "hud");

    private WaypointForgeClient() {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        WaypointHud.registerKeyMappings(event);
        WaypointGui.registerKeyMappings(event);
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        WaypointCommands.register(event.getDispatcher());
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CHAT,
                HUD_LAYER_ID,
                (graphics, deltaTracker) -> {
                    WaypointHud.render(graphics);
                    WaypointLocatorHud.render(graphics);
                }
        );
    }
}
