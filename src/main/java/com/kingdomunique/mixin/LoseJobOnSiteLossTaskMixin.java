package com.kingdomunique.mixin;

import com.kingdomunique.entity.CitizenVillager;
import net.minecraft.entity.ai.brain.task.LoseJobOnSiteLossTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LoseJobOnSiteLossTask.class)
public abstract class LoseJobOnSiteLossTaskMixin {
    @Inject(method = "method_47038", at = @At("HEAD"), cancellable = true)
    private static void kingdomUnique$keepCitizenJob(ServerWorld world, VillagerEntity villager,
                                                    long time, CallbackInfoReturnable<Boolean> cir) {
        if (villager instanceof CitizenVillager citizen
                && citizen.ku$getRole() != null && !citizen.ku$getRole().isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
