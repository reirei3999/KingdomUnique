package com.kingdomunique.mixin;

import com.kingdomunique.registry.UniqueContentRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 豊穣：近くに所持者がいる成熟作物のドロップを2倍にする。 */
@Mixin(AbstractBlock.class)
public abstract class CropDropMixin {

    @Inject(method = "onStacksDropped", at = @At("HEAD"))
    private void kingdomUnique$doubleCropDrops(BlockState state, ServerWorld world, BlockPos pos,
                                               ItemStack tool, boolean dropExperience, CallbackInfo ci) {
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > 256.0D) {
                continue;
            }
            if (UniqueContentRegistry.FERTILITY.equals(UniqueContentRegistry.getAbility(player))) {
                // 富国強兵令の農民効率-20%を収穫ドロップにも反映する。
                double rate = com.kingdomunique.kingdom.PolicyManager.cropGrowthMultiplier(
                        world, player.getUuid());
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < rate) {
                    Block.dropStack(world, pos, new ItemStack(crop.asItem()));
                }
                return;
            }
        }
    }
}
