package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueWeapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntity.class)
public abstract class WeaponAttackMixin {
    @Redirect(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean kingdomUnique$twilightScythe(Entity target, DamageSource source, float amount) {
        if ((Object) this instanceof ServerPlayerEntity player && target instanceof LivingEntity living
                && player.getMainHandStack().isOf(UniqueWeapons.TWILIGHT_SCYTHE)) {
            return UniqueWeapons.TWILIGHT_SCYTHE.hit(player, living, source, amount);
        }
        return target.damage(source, amount);
    }
}
