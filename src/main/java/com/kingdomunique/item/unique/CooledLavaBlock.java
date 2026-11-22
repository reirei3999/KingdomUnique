package com.kingdomunique.item.unique;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public final class CooledLavaBlock extends Block {
    private static final Block COOLED_LAVA = Registry.register(Registries.BLOCK,
            Identifier.of("kingdomunique", "cooled_lava"), new CooledLavaBlock());

    private CooledLavaBlock() {
        super(AbstractBlock.Settings.copy(Blocks.MAGMA_BLOCK).strength(-1.0F, 3600000.0F).dropsNothing());
    }

    public static void initialize() {
    }

    static void freezeAround(ServerPlayerEntity player) {
        if (player.hasVehicle() || player.isFallFlying() || player.getAbilities().flying) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos().down();
        BlockState cooled = COOLED_LAVA.getDefaultState();
        for (BlockPos pos : BlockPos.iterate(center.add(-2, 0, -2), center.add(2, 0, 2))) {
            if (pos.getSquaredDistance(center) > 4 || !world.isChunkLoaded(pos) || !world.isAir(pos.up())) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.LAVA) && state.getFluidState().isStill()
                    && world.canPlace(cooled, pos, ShapeContext.absent())
                    && world.setBlockState(pos, cooled)) {
                world.scheduleBlockTick(pos, COOLED_LAVA, 60 + world.getRandom().nextInt(41));
            }
        }
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.setBlockState(pos, Blocks.LAVA.getDefaultState());
    }
}
