package com.example.waypoint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class WaypointModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WaypointStorage.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                WaypointCommands.register(dispatcher)  
        );

        WaypointRenderPipeline.init();
        WaypointDeathTracker.register();
        WaypointHud.register();

        WaypointGui.register();
        WaypointChatLinks.register();
        WaypointAutoPoints.init();
        WaypointLocatorHud.init();
    }
}
