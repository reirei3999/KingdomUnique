package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import com.kingdomunique.item.unique.UniqueArmors;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ArmorAttributesMixin {
    @Inject(method = "getAttributeValue", at = @At("RETURN"), cancellable = true)
    private void kingdomUnique$armorAttributes(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (!((Object) this instanceof PlayerEntity player)) {
            return;
        }
        if (attribute == EntityAttributes.GENERIC_ATTACK_DAMAGE && player.getMainHandStack().isEmpty()
                && UniqueArmors.isWearing(player, UniqueArmors.NEMEAN_LION_HIDE)) {
            cir.setReturnValue(cir.getReturnValueD() + 4.0D);
        } else if ((attribute == EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE
                || attribute == EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE)
                && UniqueArmorEffects.isBracing(player)) {
            cir.setReturnValue(1.0D);
        }
    }
}
