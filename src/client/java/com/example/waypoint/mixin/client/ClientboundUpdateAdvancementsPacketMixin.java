package com.example.waypoint.mixin.client;

import com.example.waypoint.WaypointAutoPoints;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundUpdateAdvancementsPacket.class)
public class ClientboundUpdateAdvancementsPacketMixin {
    
    @Inject(method = "handle", at = @At("TAIL"))
    private void waypointmod$afterHandle(ClientGamePacketListener handler, CallbackInfo ci) {
        ClientboundUpdateAdvancementsPacket self = (ClientboundUpdateAdvancementsPacket) (Object) this;
        WaypointAutoPoints.onAdvancementProgressUpdate(self.getProgress());
    }
}
