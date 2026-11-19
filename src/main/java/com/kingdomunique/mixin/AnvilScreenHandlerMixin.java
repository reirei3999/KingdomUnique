package com.kingdomunique.mixin;

import com.kingdomunique.ability.TrickeryUtil;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.inventory.CraftingResultInventory;
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

    /** 奇術：バニラ側の金床エンチャント合成結果も上限を引き上げる。 */
    @Inject(method = "updateResult", at = @At("RETURN"))
    private void kingdomUnique$boostAnvilEnchantments(CallbackInfo ci) {
        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        if (!(accessor.ku$player() instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!UniqueContentRegistry.TRICKERY.equals(UniqueContentRegistry.getAbility(serverPlayer))) {
            return;
        }
        CraftingResultInventory output = accessor.ku$output();
        ItemStack result = output.getStack(0);
        if (!result.isEmpty()) {
            TrickeryUtil.boost(result);
        }
    }
}
