package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ArmorPlayerMixin implements UniqueArmorEffects.VeiledPlayer {
    @Unique
    private static final TrackedData<Boolean> KU_VEILED =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void kingdomUnique$initVeil(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(KU_VEILED, false);
    }

    @Override
    public boolean ku$isVeiled() {
        return ((PlayerEntity) (Object) this).getDataTracker().get(KU_VEILED);
    }

    @Override
    public void ku$setVeiled(boolean veiled) {
        ((PlayerEntity) (Object) this).getDataTracker().set(KU_VEILED, veiled);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void kingdomUnique$breakVeilOnAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            UniqueArmorEffects.breakVeil(player);
        }
    }

    // After vanilla armor and enchantments, before absorption is consumed.
    @ModifyVariable(method = "applyDamage", at = @At(value = "STORE", ordinal = 1), ordinal = 0)
    private float kingdomUnique$reduceArmorDamage(float amount, DamageSource source) {
        return UniqueArmorEffects.reduceDamage((PlayerEntity) (Object) this, source, amount);
    }
}
