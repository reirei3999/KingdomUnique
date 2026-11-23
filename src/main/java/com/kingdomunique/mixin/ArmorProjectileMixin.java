package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class ArmorProjectileMixin {
    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private void kingdomUnique$breakVeilOnShot(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof ProjectileEntity projectile
                && (projectile instanceof PersistentProjectileEntity
                        || projectile instanceof FireworkRocketEntity rocket && rocket.wasShotAtAngle())
                && projectile.getOwner() instanceof ServerPlayerEntity player) {
            UniqueArmorEffects.breakVeil(player);
        }
    }
}
