package com.kingdomunique.mixin;

import com.kingdomunique.effect.RelicEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnRestriction.class)
public abstract class SpawnRestrictionMixin {
    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
    private static <T extends Entity> void kingdomUnique$wardNaturalSpawns(
            EntityType<T> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos,
            Random random, CallbackInfoReturnable<Boolean> cir) {
        if (reason != SpawnReason.NATURAL || type.getSpawnGroup() != SpawnGroup.MONSTER
                || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON
                || type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN) {
            return;
        }
        for (var player : world.toServerWorld().getPlayers()) {
            if (RelicEffects.isHostileSpawnBlocked(player)
                    && player.getPos().squaredDistanceTo(pos.toCenterPos()) <= 128.0D * 128.0D) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
