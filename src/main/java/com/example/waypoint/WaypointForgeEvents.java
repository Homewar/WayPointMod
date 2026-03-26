package com.example.waypoint;

import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class WaypointForgeEvents {
    private WaypointForgeEvents() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        WaypointGui.onClientTick();
        WaypointHud.onClientTick();
        WaypointDeathTracker.onClientTick();
        WaypointAutoPoints.onClientTick();
        WaypointParticles.onClientTick();
    }

    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        WaypointAutoPoints.onClientLogin();
    }

    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WaypointAutoPoints.onClientLogout();
    }

    public static void onPlayerChat(ClientChatReceivedEvent.Player event) {
        if (WaypointChatLinks.handle(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (WaypointChatLinks.handle(event.getMessage())) {
            event.setCanceled(true);
        }
    }
}
