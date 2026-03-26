package com.example.waypoint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = WaypointMod.MOD_ID, dist = Dist.CLIENT)
public class WaypointMod {
    public static final String MOD_ID = "waypointmod";

    public WaypointMod(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(WaypointForgeClient::onRegisterKeyMappings);
        modBus.addListener(WaypointForgeClient::onRegisterGuiLayers);

        NeoForge.EVENT_BUS.addListener(WaypointForgeClient::onRegisterClientCommands);

        NeoForge.EVENT_BUS.addListener(WaypointForgeEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(WaypointForgeEvents::onClientLogin);
        NeoForge.EVENT_BUS.addListener(WaypointForgeEvents::onClientLogout);
        NeoForge.EVENT_BUS.addListener(WaypointForgeEvents::onPlayerChat);
        NeoForge.EVENT_BUS.addListener(WaypointForgeEvents::onSystemChat);

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (IConfigScreenFactory) ((client, parent) -> new WaypointScreen())
        );
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(WaypointStorage::load);
    }
}