package com.kingdomunique.mixin;

import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 金床での「同じアイテム同士・同じ強化段階」による無限強化合成。 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    @Shadow
    private Property levelCost;

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$applyInfiniteEnhancement(CallbackInfo ci) {
        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        ItemStack left = accessor.ku$input().getStack(0);
        ItemStack right = accessor.ku$input().getStack(1);

        EnhancementUtil.EnhancementResult enhancement =
                EnhancementUtil.tryEnhance(left, right, accessor.ku$player());
        if (enhancement == null) {
            return;
        }

        accessor.ku$output().setStack(0, enhancement.stack());
        levelCost.set(enhancement.levelCost());
        if (accessor.ku$player() instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            com.kingdomunique.item.unique.UniqueWeaponTriggers.onEnhanced(serverPlayer, enhancement.newLevel());
        }
        ci.cancel();
    }
}
