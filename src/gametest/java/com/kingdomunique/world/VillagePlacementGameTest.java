package com.kingdomunique.world;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public final class VillagePlacementGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void starterHallUsesTerraceInsteadOfBuriedCenterHeight(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(40, 70, 40));
        for (int x = -18; x <= 18; x++) {
            for (int z = -35; z <= 18; z++) {
                int elevation = z <= -8 ? 12 : z <= -1 ? -z : 0;
                BlockPos ground = center.add(x, elevation, z);
                world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        BlockPos expected = TownHallBuilder.findVillageOrigin(world, center.getX(), center.getZ());
        context.assertTrue(expected != null, "A natural town hall plot must exist on the terrace");
        context.assertTrue(expected.getY() == center.getY() + 13, "The hall must use the terrace elevation");
        BlockPos slope = center.add(0, 7, -7);
        VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ());

        BlockPos door = expected.add(5, 0, 12);
        context.assertTrue(world.getBlockState(expected.add(5, -1, 5)).isOf(Blocks.SPRUCE_PLANKS),
                "The starter village must build its town hall on the terrace");
        context.assertTrue(world.getBlockState(door).isAir() && world.getBlockState(door.up()).isAir()
                        && world.getBlockState(door.south(2)).isAir()
                        && world.getBlockState(door.south(2).up()).isAir(),
                "The entrance and its approach must not be buried");
        context.assertTrue(world.getBlockState(slope).isOf(Blocks.STONE)
                        && world.getBlockState(expected.down(2)).isAir(),
                "Generation must retain the slope and not build a raised foundation under the hall");
        context.complete();
    }
}
