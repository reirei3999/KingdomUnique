package com.kingdomunique.mixin;

import com.kingdomunique.effect.CurseEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class StatusEffectMixin {

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$blockStatusEffects(StatusEffectInstance effect, Entity source,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && (CurseEffects.blocksStatusEffects(player)
                || (effect.getEffectType().value().isBeneficial() && CurseEffects.blocksAlliedAid(player, source)))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setStatusEffect", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$blockAlliedBuff(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player
                && effect.getEffectType().value().isBeneficial() && CurseEffects.blocksAlliedAid(player, source)) {
            ci.cancel();
        }
    }
}
