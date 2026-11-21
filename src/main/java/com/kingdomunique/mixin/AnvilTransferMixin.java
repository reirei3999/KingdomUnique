package com.kingdomunique.mixin;

import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgingScreenHandler.class)
public abstract class AnvilTransferMixin {
    @Inject(method = "quickMove", at = @At("HEAD"))
    private void kingdomUnique$prepareTransfer(PlayerEntity player, int slot,
                                              CallbackInfoReturnable<ItemStack> cir) {
        if (this instanceof EnhancementUtil.AnvilTransfer transfer) {
            transfer.ku$prepareTransfer(player, slot);
        }
    }

    @Inject(method = "quickMove", at = @At("RETURN"))
    private void kingdomUnique$clearTransfer(PlayerEntity player, int slot,
                                            CallbackInfoReturnable<ItemStack> cir) {
        if (this instanceof EnhancementUtil.AnvilTransfer transfer) {
            transfer.ku$clearTransfer();
        }
    }
}
