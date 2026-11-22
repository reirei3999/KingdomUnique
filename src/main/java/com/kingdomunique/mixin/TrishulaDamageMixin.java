package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.TrishulaEffects;
import com.kingdomunique.item.unique.TrishulaEntity;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TrishulaDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$trishulaProtection(DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && TrishulaEffects.isProtected(player, source)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$trishulaDamage(float amount, DamageSource source) {
        if ((Object) this instanceof PlayerEntity) {
            return amount;
        }
        boolean thrown = source.getSource() instanceof TrishulaEntity && !source.isIn(DamageTypeTags.IS_EXPLOSION);
        boolean melee = source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == source.getAttacker()
                && source.getAttacker() instanceof ServerPlayerEntity player
                && player.getMainHandStack().isOf(UniqueWeapons.TRISHULA);
        return thrown || melee ? amount * 2.0F : amount;
    }
}
