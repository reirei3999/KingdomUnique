package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import com.kingdomunique.item.unique.UniqueArmors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ArmorEntityMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$hadesInvisibility(CallbackInfoReturnable<Boolean> cir) {
        if (this instanceof UniqueArmorEffects.VeiledPlayer veil && veil.ku$isVeiled()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "setFireTicks", at = @At("HEAD"), argsOnly = true)
    private int kingdomUnique$preventIgnition(int ticks) {
        return ticks > 0 && (Object) this instanceof PlayerEntity player
                && UniqueArmors.isWearing(player, UniqueArmors.SALAMANDER_BOOTS) ? 0 : ticks;
    }
}
