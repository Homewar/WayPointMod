package com.example.waypoint.mixin.client;

import com.example.waypoint.WaypointAutoPoints;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAdvancements.class)
public class ClientAdvancementsMixin {

    @Inject(method = "update", at = @At("TAIL"))
    private void waypointmod$onUpdateAdvancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        WaypointAutoPoints.onAdvancementProgressUpdate(packet.getProgress());
    }
}
