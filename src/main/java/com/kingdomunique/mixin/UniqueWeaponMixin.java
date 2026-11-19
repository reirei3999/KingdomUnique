package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueWeaponManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 一点モノ武器のダメージ・回復・時間停止ギミック。 */
@Mixin(LivingEntity.class)
public abstract class UniqueWeaponMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$uniqueWeaponDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;

        if (self instanceof ServerPlayerEntity holder && UniqueWeaponManager.held(holder, "excalibur")) {
            amount *= 1.8F;
        }

        if (attacker instanceof ServerPlayerEntity player) {
            if (UniqueWeaponManager.held(player, "gae_bolg")) {
                amount *= 2.5F;
            }
            if (UniqueWeaponManager.held(player, "apollo_bow")) {
                amount *= 2.0F;
            }
            if (UniqueWeaponManager.held(player, "muramasa")) {
                player.heal(amount * 0.2F);
                player.addExhaustion(0.02F);
            }
            if (UniqueWeaponManager.held(player, "chronos_dagger")
                    && player.getRandom().nextFloat() < 0.2F) {
                UniqueWeaponManager.freeze(self);
            }
        }
        return amount;
    }
}
