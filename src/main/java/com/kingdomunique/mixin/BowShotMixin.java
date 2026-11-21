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

    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void kingdomUnique$onBowShot(ItemStack stack, World world, LivingEntity user,
                                          int remainingUseTicks, CallbackInfo ci) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return;
        }
        // remainingUseTicks は「使用上限(72000)からの残り」なので 0 にはならない。
        // バニラと同じく、引き絞り 0.1 以上かつ矢を持っている（またはクリエイティブ）ときだけ「放った」とみなす。
        int usedTicks = ((BowItem) (Object) this).getMaxUseTime(stack, user) - remainingUseTicks;
        boolean hasArrow = player.isCreative() || !player.getProjectileType(stack).isEmpty();
        if (BowItem.getPullProgress(usedTicks) >= 0.1F && hasArrow) {
            com.kingdomunique.item.unique.UniqueWeaponTriggers.onBowShot(player);
        }
    }
}
