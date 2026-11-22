package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.StructurePlacer;
import com.kingdomunique.building.StructureRotationUtil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class VillageFixtureGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_fixture_well")
    public void fallbackWellBellSurvivesNeighborUpdates(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = new BlockPos(12000, 180, 12000);
        world.getChunk(base);
        VillageBuilder.buildWell(world, base.getX(), base.getY(), base.getZ());
        BlockPos bell = base.up(3);
        context.assertTrue(world.getBlockState(bell).isOf(Blocks.BELL), "Fallback must place its bell");
        BlockPos roof = base.add(1, 2, 0);
        world.setBlockState(roof, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(roof, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(bell.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(bell.east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.updateNeighborsAlways(bell.down(), Blocks.OAK_PLANKS);
        context.waitAndRun(30, () -> {
            var state = world.getBlockState(bell);
            context.assertTrue(state.isOf(Blocks.BELL) && state.canPlaceAt(world, bell),
                    "Fallback bell must retain valid support after neighbor updates");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_fixture_lanterns")
    public void porchAndMarketLanternsHaveSupportedHeadroomInEveryFacing(TestContext context) {
        ServerWorld world = context.getWorld();
        Direction[] facings = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        BuildingType[] types = {BuildingType.TOWN_HALL, BuildingType.MARKET};
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < facings.length; j++) {
                BlockPos origin = new BlockPos(12128 + i * 64, 180, 12000 + j * 64);
                StructurePlacer.place(world, origin, types[i], facings[j]);
            }
        }
        context.waitAndRun(30, () -> {
            for (int i = 0; i < types.length; i++) {
                for (int j = 0; j < facings.length; j++) {
                    BuildingType type = types[i];
                    Direction facing = facings[j];
                    BlockPos origin = new BlockPos(12128 + i * 64, 180, 12000 + j * 64);
                    BlockBox box = StructureRotationUtil.computeBoundingBox(origin, type, facing);
                    int count = 0;
                    for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(),
                            box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                        var state = world.getBlockState(pos);
                        if (state.isOf(Blocks.LANTERN) && state.get(LanternBlock.HANGING)) {
                            count++;
                            context.assertTrue(state.canPlaceAt(world, pos) && !world.getBlockState(pos.up()).isAir(),
                                    type + " " + facing + " lantern needs overhead support at " + pos);
                            BlockPos floor = pos.down();
                            while (floor.getY() >= box.getMinY() && world.getBlockState(floor).isAir()) {
                                floor = floor.down();
                            }
                            context.assertTrue(pos.getY() - floor.getY() - 1 >= 3,
                                    type + " " + facing + " lantern needs three air cells above floor at " + pos);
                        }
                    }
                    context.assertTrue(count == (type == BuildingType.TOWN_HALL ? 3 : 2),
                            type + " must retain all hanging lanterns in " + facing);
                    if (type == BuildingType.TOWN_HALL) {
                        assertClearance(context, world, origin, type, facing, 5, 14);
                    } else {
                        assertClearance(context, world, origin, type, facing, 2, 3);
                        assertClearance(context, world, origin, type, facing, 6, 3);
                    }
                }
            }
            context.complete();
        });
    }

    private static void assertClearance(TestContext context, ServerWorld world, BlockPos origin,
                                        BuildingType type, Direction facing, int x, int z) {
        int[] dims = StructureRotationUtil.getBuildingDimensions(type);
        BlockPos floor = StructureRotationUtil.transformPos(origin, x, -1, z, dims[0], dims[2], facing);
        context.assertTrue(!world.getBlockState(floor).getCollisionShape(world, floor).isEmpty(),
                "Measured lantern must be above a walking surface: " + floor);
        for (int y = 1; y <= 3; y++) {
            context.assertTrue(world.getBlockState(floor.up(y)).isAir(),
                    type + " " + facing + " measured passage must have three air cells at " + floor.up(y));
        }
        var lantern = world.getBlockState(floor.up(4));
        context.assertTrue(lantern.isOf(Blocks.LANTERN) && lantern.get(LanternBlock.HANGING)
                        && lantern.canPlaceAt(world, floor.up(4)),
                "Measured passage must retain its supported hanging lantern at " + floor.up(4));
    }
}
