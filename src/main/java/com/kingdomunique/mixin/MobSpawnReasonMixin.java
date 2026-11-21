package com.kingdomunique.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobSpawnReasonMixin {
    @Inject(method = "initialize", at = @At("HEAD"))
    private void kingdomUnique$rememberNaturalSpawn(ServerWorldAccess world, LocalDifficulty difficulty,
            SpawnReason reason, EntityData data, CallbackInfoReturnable<EntityData> cir) {
        MobEntity mob = (MobEntity) (Object) this;
        if (reason == SpawnReason.NATURAL) {
            mob.addCommandTag("kingdomunique_natural_spawn");
        } else {
            mob.removeCommandTag("kingdomunique_natural_spawn");
        }
    }
}
