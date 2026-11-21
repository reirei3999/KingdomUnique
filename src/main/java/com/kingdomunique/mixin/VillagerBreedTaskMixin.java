package com.kingdomunique.mixin;

import net.minecraft.entity.ai.brain.task.VillagerBreedTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 村人の自然繁殖を完全に無効化する（人口は王国システムで管理する）。 */
@Mixin(VillagerBreedTask.class)
public abstract class VillagerBreedTaskMixin {

    @Inject(method = "shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/VillagerEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$disableVillagerBreeding(ServerWorld world, VillagerEntity villager,
                                                        CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(false);
    }
}
