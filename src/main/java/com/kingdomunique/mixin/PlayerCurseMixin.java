package com.kingdomunique.mixin;

import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.effect.RelicEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 狂戦士の自然回復封じ、足枷のジャンプ封じ、聖杯の空腹無効。 */
@Mixin(PlayerEntity.class)
public abstract class PlayerCurseMixin {

    @Inject(method = "canFoodHeal", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$noNaturalHeal(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player
                && CurseEffects.has(player, com.kingdomunique.registry.CurseRegistry.BERSERKER)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$noJump(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player && CurseEffects.isJumpDisabled(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$noHunger(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player && RelicEffects.isHungerDisabled(player)) {
            ci.cancel();
        }
    }
}
