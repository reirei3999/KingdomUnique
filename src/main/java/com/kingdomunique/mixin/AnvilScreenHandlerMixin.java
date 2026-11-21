package com.kingdomunique.mixin;

import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 金床での「同じアイテム同士・同じ強化段階」による無限強化合成。
 *
 * Gemini 修正: updateResult では常に +1 の仮プレビューだけを見せ、
 * 実際の確率抽選（15% +3 / 25% +2 / 60% +1）はプレイヤーが結果を
 * 取り出した瞬間（onTakeOutput）に行う。これで +3 が出るまで出し入れ
 * するリセマラを防ぐ。
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    @Shadow
    private Property levelCost;

    /** プレビューは +1 固定（確率を振らない）。 */
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$applyInfiniteEnhancement(CallbackInfo ci) {
        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        ItemStack left = accessor.ku$input().getStack(0);
        ItemStack right = accessor.ku$input().getStack(1);

        EnhancementUtil.EnhancementResult enhancement =
                EnhancementUtil.tryEnhance(left, right, accessor.ku$player(), false);
        if (enhancement == null) {
            return;
        }

        accessor.ku$output().setStack(0, enhancement.stack());
        levelCost.set(enhancement.levelCost());
        ci.cancel();
    }

    /**
     * 結果を取り出した瞬間に初めて神匠の確率抽選を行い、
     * 確定した段階のアイテムへ差し替える（HEAD でバニラの消費より先に処理）。
     */
    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void kingdomUnique$rollDivineBonus(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ForgingScreenHandlerAccessor accessor = (ForgingScreenHandlerAccessor) this;
        ItemStack left = accessor.ku$input().getStack(0);
        ItemStack right = accessor.ku$input().getStack(1);

        EnhancementUtil.EnhancementResult enhancement =
                EnhancementUtil.tryEnhance(left, right, serverPlayer, true);
        if (enhancement == null) {
            return;
        }
        // 結果スロットと取り出し中のスタックを確定結果で上書きする。
        ItemStack finalResult = enhancement.stack();
        accessor.ku$output().setStack(0, finalResult.copy());
        output.applyComponentsFrom(finalResult.getComponents());
        output.setCount(finalResult.getCount());

        com.kingdomunique.item.unique.UniqueWeaponTriggers.onEnhanced(serverPlayer, enhancement.newLevel());
    }
}
