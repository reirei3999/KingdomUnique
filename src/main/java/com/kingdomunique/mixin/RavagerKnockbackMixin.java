package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RavagerEntity.class)
public abstract class RavagerKnockbackMixin {
    @Inject(method = "knockBack", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$braceAgainstKnockback(Entity target, CallbackInfo ci) {
        if (target instanceof PlayerEntity player && UniqueArmorEffects.isBracing(player)) {
            ci.cancel();
        }
    }
}
