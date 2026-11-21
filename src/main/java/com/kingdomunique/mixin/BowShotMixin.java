package com.kingdomunique.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 弓を放った瞬間のアポロンの太陽弓トリガー。 */
@Mixin(BowItem.class)
public abstract class BowShotMixin {

    @Inject(method = "onStoppedUsing", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/item/BowItem;shootAll(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/entity/LivingEntity;)V"))
    private void kingdomUnique$onBowShot(ItemStack stack, World world, LivingEntity user,
                                          int remainingUseTicks, CallbackInfo ci) {
        if (user instanceof ServerPlayerEntity player) {
            com.kingdomunique.item.unique.UniqueWeaponTriggers.onBowShot(player);
        }
    }
}
