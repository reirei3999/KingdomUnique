package com.kingdomunique.mixin;

import com.kingdomunique.ability.TrickeryUtil;
import com.kingdomunique.registry.UniqueContentRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 奇術：エンチャントテーブルでの付与レベルを1.5〜2.0倍（切り上げ）に拡張する。 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {

    @Shadow
    private Inventory inventory;

    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void kingdomUnique$boostEnchantTable(PlayerEntity player, int id,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!UniqueContentRegistry.TRICKERY.equals(UniqueContentRegistry.getAbility(serverPlayer))) {
            return;
        }
        ItemStack result = inventory.getStack(1);
        if (result.isEmpty()) {
            return;
        }
        TrickeryUtil.boost(result);
    }
}
