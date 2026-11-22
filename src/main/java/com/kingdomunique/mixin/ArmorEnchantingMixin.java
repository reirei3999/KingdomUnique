package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import com.kingdomunique.item.unique.UniqueArmorTriggers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentScreenHandler.class)
public abstract class ArmorEnchantingMixin {
    @Shadow @Final public int[] enchantmentPower;

    @Inject(method = "method_17410", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;incrementStat(Lnet/minecraft/util/Identifier;)V",
            shift = At.Shift.AFTER))
    private void kingdomUnique$recordLevelThirtyEnchant(ItemStack stack, int option, PlayerEntity player,
            int cost, ItemStack lapis, World world, BlockPos pos, CallbackInfo ci) {
        if (enchantmentPower[option] >= 30 && player instanceof ServerPlayerEntity serverPlayer
                && world instanceof ServerWorld serverWorld) {
            UniqueArmorTriggers.onEnchant(serverPlayer, serverWorld, pos);
        }
    }

    @Redirect(method = "method_17410", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;applyEnchantmentCosts(Lnet/minecraft/item/ItemStack;I)V"))
    private void kingdomUnique$discountEnchanting(PlayerEntity player, ItemStack stack, int cost) {
        player.applyEnchantmentCosts(stack, UniqueArmorEffects.discountLevels(player, cost));
    }
}
