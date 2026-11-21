package com.kingdomunique.mixin;

import com.kingdomunique.ability.AbilityPassives;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 豊穣：所持者の王国領土内で収穫された成熟作物のドロップを2倍にする。 */
@Mixin(AbstractBlock.class)
public abstract class CropDropMixin {

    @Inject(method = "onStacksDropped", at = @At("HEAD"))
    private void kingdomUnique$doubleCropDrops(BlockState state, ServerWorld world, BlockPos pos,
                                               ItemStack tool, boolean dropExperience, CallbackInfo ci) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) {
            return;
        }
        UUID owner = AbilityPassives.fertilityKingdomAt(world, pos);
        if (owner == null) {
            return;
        }
        // 富国強兵令の農民効率-20%を収穫ドロップにも反映する。
        double rate = com.kingdomunique.kingdom.PolicyManager.cropGrowthMultiplier(world, owner);
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < rate) {
            Block.dropStack(world, pos, new ItemStack(crop.asItem()));
        }
    }
}
