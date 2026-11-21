package com.kingdomunique.mixin;

import com.kingdomunique.effect.RelicEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
    @Redirect(method = "spawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z"))
    private boolean kingdomUnique$skipWardProtectedPlayer(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            return true;
        }
        for (var holder : player.getServerWorld().getPlayers()) {
            if (RelicEffects.isHostileSpawnBlocked(holder)
                    && holder.squaredDistanceTo(player) <= 128.0D * 128.0D) {
                return true;
            }
        }
        return false;
    }
}
