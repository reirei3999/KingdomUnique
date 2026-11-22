package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.StructurePlacer;
import com.kingdomunique.building.StructureRotationUtil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "palace_access")
    public void palaceTerracesAndTowerLandingsAreReachableInEveryFacing(TestContext context) {
        ServerWorld world = context.getWorld();
        Direction[] facings = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int i = 0; i < facings.length; i++) {
            StructurePlacer.place(world, new BlockPos(12512, 180, 12000 + i * 64),
                    BuildingType.ROYAL_PALACE, facings[i]);
        }
        context.waitAndRun(30, () -> {
            for (int i = 0; i < facings.length; i++) {
                BlockPos origin = new BlockPos(12512, 180, 12000 + i * 64);
                Direction facing = facings[i];
                BlockBox box = StructureRotationUtil.computeBoundingBox(origin, BuildingType.ROYAL_PALACE, facing);
                int ladders = 0;
                for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(),
                        box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                    var state = world.getBlockState(pos);
                    if (state.isOf(Blocks.LADDER)) {
                        ladders++;
                        context.assertTrue(state.canPlaceAt(world, pos), "Palace ladder needs backing at " + pos);
                    }
                }
                context.assertTrue(ladders == 48, "Palace must retain all six backed ladder runs in " + facing);
                Set<BlockPos> reached = reachable(world, box, palacePos(origin, facing, 9, 1, 9));
                for (int x : new int[]{2, 16}) {
                    BlockPos terrace = palacePos(origin, facing, x, 6, 7);
                    context.assertTrue(reached.contains(terrace), facing + " wing terrace unreachable: " + terrace);
                }
                for (int tx : new int[]{1, 15}) {
                    for (int tz : new int[]{1, 13}) {
                        BlockPos landing = palacePos(origin, facing, tx + 1, 9, tz == 1 ? 4 : 12);
                        context.assertTrue(reached.contains(landing), facing + " tower landing unreachable: " + landing);
                        context.assertTrue(canStand(world, landing), "Tower exit needs a supported clear landing");
                    }
                }
            }
            context.complete();
        });
    }

    private static BlockPos palacePos(BlockPos origin, Direction facing, int x, int y, int z) {
        return StructureRotationUtil.transformPos(origin, x, y, z, 19, 19, facing);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_roads_flat")
    public void villageRoadsConnectActualEntrancesToWellPlaza(TestContext context) {
        checkVillageRoads(context, new BlockPos(13064, 180, 13064), false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_roads_stepped")
    public void villageRoadsConnectAcrossOneBlockTerraces(TestContext context) {
        checkVillageRoads(context, new BlockPos(13320, 180, 13064), true);
    }

    private static void checkVillageRoads(TestContext context, BlockPos center, boolean stepped) {
        ServerWorld world = context.getWorld();
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                int height = stepped && x >= 4 ? 1 : 0;
                world.setBlockState(center.add(x, height, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        var plan = VillageBuilder.planVillage(world, center.getX(), center.getZ());
        context.assertTrue(plan != null && plan.well() != null, "Road fixture needs a well and village plan");
        var well = plan.well();
        BlockPos wellMin = well.template() == null ? well.plot() : well.plot().add(1, 0, 1);
        int width = well.template() == null ? 3 : well.template().getSize().getX();
        int depth = well.template() == null ? 3 : well.template().getSize().getZ();
        Set<BlockPos> plaza = new HashSet<>();
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                if (x == -1 || x == width || z == -1 || z == depth) {
                    plaza.add(new BlockPos(wellMin.getX() + x, well.plot().getY() - 1, wellMin.getZ() + z));
                }
            }
        }
        var buildings = VillageBuilder.generateVillage(world, center.getX(), center.getZ(), null).buildings();
        context.assertTrue(buildings.size() == 5, "Road fixture must include the hall, three houses and blacksmith");
        context.waitAndRun(30, () -> {
            for (BlockPos pos : plaza) {
                context.assertTrue(isRoad(world, pos), "Actual well must have a complete plaza ring at " + pos);
            }
            Set<BlockPos> connected = roadReachable(world, plaza.iterator().next());
            context.assertTrue(connected.containsAll(plaza), "The entire well plaza must be connected");
            Set<Integer> heights = new HashSet<>();
            for (BlockPos pos : connected) {
                heights.add(pos.getY());
                context.assertTrue(world.getBlockState(pos.up()).isAir() && world.getBlockState(pos.up(2)).isAir(),
                        "Every connected road cell needs two air cells above it: " + pos);
            }
            for (var building : buildings) {
                int[] dims = StructureRotationUtil.getBuildingDimensions(building.type());
                int x = building.type() == BuildingType.TOWN_HALL ? 5 : building.type() == BuildingType.HOUSE ? 4 : 3;
                int z = building.type() == BuildingType.TOWN_HALL ? 14 : 6;
                BlockPos front = StructureRotationUtil.transformPos(building.origin(), x, -1, z,
                        dims[0], dims[2], building.facing());
                context.assertTrue(connected.contains(front), building.type() + " " + building.facing()
                        + " actual door/porch front must connect through roads to the well plaza: " + front);
                if (building.type() != BuildingType.TOWN_HALL) {
                    BlockPos door = StructureRotationUtil.transformPos(building.origin(), x, 0, 5,
                            dims[0], dims[2], building.facing());
                    context.assertTrue(world.getBlockState(door).isOf(Blocks.SPRUCE_DOOR)
                                    && world.getBlockState(door.up()).isOf(Blocks.SPRUCE_DOOR),
                            "Roads must not cut into the door");
                }
            }
            if (stepped) {
                context.assertTrue(heights.size() >= 2, "Stepped fixture must actually exercise road elevation changes");
            }
            if (plan.farm() != null) {
                context.assertTrue(world.getBlockState(plan.farm().add(3, -1, 2)).isOf(Blocks.WATER),
                        "Roads must not overwrite farm irrigation");
            }
            context.complete();
        });
    }

    private static Set<BlockPos> roadReachable(ServerWorld world, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            for (Direction direction : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.offset(direction).up(dy);
                    if (isRoad(world, next) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return visited;
    }

    private static boolean isRoad(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isOf(Blocks.DIRT_PATH) || world.getBlockState(pos).isOf(Blocks.GRAVEL);
    }

    private static Set<BlockPos> reachable(ServerWorld world, BlockBox box, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (canStand(world, start)) {
            visited.add(start);
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            for (Direction direction : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.offset(direction).up(dy);
                    if (dy > 0 && !passable(world, pos.up(2))) {
                        continue;
                    }
                    if (dy < 0 && !passable(world, next.up(2))) {
                        continue;
                    }
                    if (box.contains(next) && canStand(world, next) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
            for (int dy : new int[]{-1, 1}) {
                BlockPos next = pos.up(dy);
                if ((world.getBlockState(pos).isOf(Blocks.LADDER) || world.getBlockState(next).isOf(Blocks.LADDER))
                        && box.contains(next) && canStand(world, next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private static boolean canStand(ServerWorld world, BlockPos feet) {
        return passable(world, feet) && passable(world, feet.up())
                && (world.getBlockState(feet).isOf(Blocks.LADDER)
                || world.getBlockState(feet.down()).isSideSolidFullSquare(world, feet.down(), Direction.UP));
    }

    private static boolean passable(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof LadderBlock || state.getBlock() instanceof CarpetBlock
                || (state.getCollisionShape(world, pos).isEmpty() && world.getFluidState(pos).isEmpty());
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
