package com.kingdomunique.mixin;

import com.kingdomunique.effect.CurseEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.entity.effect.InstantHealthOrDamageStatusEffect")
public abstract class InstantStatusEffectMixin {
    @Inject(method = "applyInstantEffect", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$blockAlliedHealing(Entity source, Entity attacker, LivingEntity target,
                                                  int amplifier, double proximity, CallbackInfo ci) {
        if (target instanceof ServerPlayerEntity player && ((StatusEffect) (Object) this).isBeneficial()
                && CurseEffects.blocksAlliedAid(player, attacker != null ? attacker : source)) {
            ci.cancel();
        }
    }
}
