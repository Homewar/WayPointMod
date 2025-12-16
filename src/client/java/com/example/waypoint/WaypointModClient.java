package com.example.waypoint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class WaypointModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WaypointStorage.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                WaypointCommands.register(dispatcher)  
        );

        WorldRenderEvents.AFTER_ENTITIES.register(WaypointRenderer::render);
        WaypointDeathTracker.register();
        WaypointHud.register();
    }
}
