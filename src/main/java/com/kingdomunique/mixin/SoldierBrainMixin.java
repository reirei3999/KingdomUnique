package com.kingdomunique.mixin;

import com.kingdomunique.entity.CitizenVillager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class SoldierBrainMixin {
    @Inject(method = {"startTasks", "updateTasks"}, at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$prioritizeSoldierCombat(ServerWorld world, LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof VillagerEntity soldier)
                || !(soldier instanceof CitizenVillager citizen)
                || !CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole())) {
            return;
        }
        Brain<VillagerEntity> brain = soldier.getBrain();
        if (soldier.getTarget() == null
                && !brain.hasMemoryModule(MemoryModuleType.NEAREST_HOSTILE)
                && !brain.hasMemoryModule(MemoryModuleType.HURT_BY)
                && !brain.hasMemoryModule(MemoryModuleType.AVOID_TARGET)
                && !brain.hasActivity(Activity.PANIC)) {
            return;
        }
        brain.stopAllTasks(world, soldier);
        brain.forget(MemoryModuleType.WALK_TARGET);
        brain.forget(MemoryModuleType.PATH);
        brain.forget(MemoryModuleType.AVOID_TARGET);
        if (brain.hasActivity(Activity.PANIC)) {
            brain.doExclusively(Activity.IDLE);
        }
        ci.cancel();
    }
}
