package com.kingdomunique.mixin;

import com.kingdomunique.item.ModItems;
import net.minecraft.block.AbstractBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 破壊衝動のツルハシ：手動破壊時にアイテムをドロップさせない（消滅）。 */
@Mixin(AbstractBlock.class)
public abstract class DestructionDropMixin {

    /** 破壊衝動のツルハシを手にしている間、どんなブロックも一瞬で砕けるようにする。 */
    @Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$instantBreak(net.minecraft.block.BlockState state,
                                             net.minecraft.entity.player.PlayerEntity player,
                                             net.minecraft.world.BlockView world,
                                             BlockPos pos,
                                             CallbackInfoReturnable<Float> cir) {
        if (player.getMainHandStack().isOf(ModItems.DESTRUCTION_PICKAXE)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "onStacksDropped", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$noDropsWithDestructionPickaxe(net.minecraft.block.BlockState state,
                                                             ServerWorld world, BlockPos pos,
                                                             net.minecraft.item.ItemStack tool,
                                                             boolean dropExperience, CallbackInfo ci) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > 144.0D) {
                continue;
            }
            if (player.getMainHandStack().isOf(ModItems.DESTRUCTION_PICKAXE)) {
                ci.cancel();
                return;
            }
        }
    }
}
