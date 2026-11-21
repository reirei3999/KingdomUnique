package com.kingdomunique.mixin;

import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.effect.RelicEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 混沌のサイコロのダメージ変動と守護の結界石の夜間軽減。 */
@Mixin(LivingEntity.class)
public abstract class CombatCurseMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$chaosDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (source.getAttacker() instanceof LivingEntity attacker
                && !self.getWorld().isClient()
                && com.kingdomunique.war.Warfare.isFriendly(
                        ((net.minecraft.server.world.ServerWorld) self.getWorld()).getServer(),
                        attacker, self)) {
            return 0.0F;
        }
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            amount *= (float) CurseEffects.rollChaosMultiplier(player);
        }
        // 刺客英雄は落下ダメージ完全無効。
        if (self instanceof ServerPlayerEntity victim
                && RelicEffects.getNightRaidDamageMultiplier(victim) < 1.0D
                && victim.getWorld().isNight()
                && source.getAttacker() instanceof net.minecraft.entity.mob.HostileEntity) {
            amount *= (float) RelicEffects.getNightRaidDamageMultiplier(victim);
        }
        if (self instanceof ServerPlayerEntity victim) {
            amount *= (float) CurseEffects.incomingMultiplier(victim);
        }
        return amount;
    }
}
