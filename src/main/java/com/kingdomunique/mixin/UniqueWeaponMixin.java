package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 一点モノ武器のダメージ・回復・時間停止ギミック。 */
@Mixin(LivingEntity.class)
public abstract class UniqueWeaponMixin {

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("TAIL"))
    private void kingdomUnique$windSlash(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
        if (hand == Hand.MAIN_HAND && (Object) this instanceof ServerPlayerEntity player
                && player.getMainHandStack().isOf(UniqueWeapons.KUSANAGI)) {
            UniqueWeapons.KUSANAGI.slash(player);
        }
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$uniqueWeaponDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;

        if (attacker instanceof ServerPlayerEntity holder
                && UniqueWeaponManager.held(holder, "excalibur")
                && com.kingdomunique.kingdom.KingdomManager.isInTerritory(holder)) {
            amount *= 1.8F;
        }

        if (attacker instanceof ServerPlayerEntity player) {
            if (UniqueWeaponManager.held(player, "gae_bolg")) {
                amount *= 2.5F;
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
