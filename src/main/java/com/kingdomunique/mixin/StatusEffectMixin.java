package com.kingdomunique.mixin;

import com.kingdomunique.effect.CurseEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 重力の足枷：すべての状態異常を無効化する。 */
@Mixin(LivingEntity.class)
public abstract class StatusEffectMixin {

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$blockStatusEffects(StatusEffectInstance effect, net.minecraft.entity.Entity source,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && CurseEffects.blocksStatusEffects(player)) {
            cir.setReturnValue(false);
        }
    }
}
