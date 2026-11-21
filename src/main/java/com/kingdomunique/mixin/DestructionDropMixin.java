package com.kingdomunique.mixin;

import com.kingdomunique.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 破壊衝動のツルハシ：このツルハシで壊したブロックはアイテム・経験値を一切ドロップしない（消滅）。 */
@Mixin(Block.class)
public abstract class DestructionDropMixin {

    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void kingdomUnique$noDropsWithDestructionPickaxe(BlockState state, World world, BlockPos pos,
                                                                    BlockEntity blockEntity, Entity entity,
                                                                    ItemStack tool, CallbackInfo ci) {
        if (tool.isOf(ModItems.DESTRUCTION_PICKAXE)) {
            ci.cancel();
        }
    }
}
