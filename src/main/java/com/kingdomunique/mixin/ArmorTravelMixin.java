package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorTriggers;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ArmorTravelMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void kingdomUnique$resetArmorTravel(double x, double y, double z, float yaw, float pitch,
            Set<PositionFlag> flags, CallbackInfo ci) {
        UniqueArmorTriggers.resetTravel(player);
    }
}
