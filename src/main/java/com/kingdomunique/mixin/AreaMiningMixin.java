package com.kingdomunique.mixin;

import com.kingdomunique.ability.AbilityState;
import com.kingdomunique.ability.PlayerAbilityState;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 神匠：設定された採掘範囲（1x1 / 3x3 / 5x5）で隣接ブロックを一括破壊する。 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class AreaMiningMixin {

    @Shadow
    protected ServerPlayerEntity player;

    @Shadow
    protected ServerWorld world;

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void kingdomUnique$areaMine(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        if (!UniqueContentRegistry.has(player, UniqueContentRegistry.DIVINE_SMITH)) {
            return;
        }
        if (!EnhancementUtil.isEnhanceable(player.getMainHandStack())) {
            return;
        }

        PlayerAbilityState state = AbilityState.get(player.getUuid());
        int range = state.getMiningRange();
        if (range <= 1) {
            return;
        }
        int radius = range / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos target = pos.add(dx, 0, dz);
                if (!world.isAir(target)) {
                    world.breakBlock(target, true, player);
                }
            }
        }
    }
}
