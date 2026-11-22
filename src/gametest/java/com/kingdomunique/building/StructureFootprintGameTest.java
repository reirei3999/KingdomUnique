package com.kingdomunique.building;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** 全設計図が4方向すべてでプレビュー枠の内側にだけ建つことを確かめる。 */
public final class StructureFootprintGameTest {
    private static final BlockState SENTINEL = Blocks.GLASS.getDefaultState();
    private static final Direction[] FACINGS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void playtestFixturesAndDefenseAccessSurviveUpdates(TestContext context) {
        ServerWorld world = context.getWorld();
        BuildingType[] types = {BuildingType.BLACKSMITH, BuildingType.TOWN_HALL,
                BuildingType.WATCHTOWER, BuildingType.GATE, BuildingType.WALL};
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < FACINGS.length; j++) {
                BuildingType type = types[i];
                Direction facing = FACINGS[j];
                BlockPos origin = context.getAbsolutePos(new BlockPos(i * 32, 100, 1000 + j * 32));
                StructurePlacer.place(world, origin, type, facing);
                if (type == BuildingType.BLACKSMITH) {
                    stableFixture(context, origin, type, facing, new BlockPos(3, 2, 6), Blocks.LANTERN);
                    stableFixture(context, origin, type, facing, new BlockPos(3, 2, 3), Blocks.LANTERN);
                    stableFixture(context, origin, type, facing, new BlockPos(8, 2, 3), Blocks.LANTERN);
                } else if (type == BuildingType.TOWN_HALL) {
                    stableFixture(context, origin, type, facing, new BlockPos(5, 11, 4), Blocks.BELL);
                } else if (type == BuildingType.WATCHTOWER) {
                    for (int x = 1; x <= 3; x++) {
                        for (int z = 1; z <= 3; z++) {
                            for (int y = 9; y <= 11; y++) {
                                context.assertTrue(world.getBlockState(local(origin, type, facing, x, y, z)).isAir(),
                                        "Watchtower needs three clear blocks above its floor, including under lights");
                            }
                        }
                    }
                } else {
                    int top = type == BuildingType.GATE ? 5 : 3;
                    for (int y = 0; y <= top; y++) {
                        stableFixture(context, origin, type, facing, new BlockPos(1, y, 0), Blocks.LADDER);
                    }
                    for (int y = top + 1; y <= top + 2; y++) {
                        context.assertTrue(world.getBlockState(local(origin, type, facing, 1, y, 1)).isAir(),
                                "Ladder must exit onto an unobstructed walkway");
                    }
                }
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void adjacentWallsAreAllowedButOverlapsAreRejected(TestContext context) {
        ServerWorld world = context.getWorld();
        var owner = java.util.UUID.randomUUID();
        for (int j = 0; j < FACINGS.length; j++) {
            Direction facing = FACINGS[j];
            BlockPos origin = context.getAbsolutePos(new BlockPos(j * 48, 100, 2000));
            BlockBox box = StructureRotationUtil.computeBoundingBox(origin, BuildingType.WALL, facing);
            world.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
            var state = KingdomBuildingState.get(world);
            state.add(owner, BuildingType.WALL, origin, facing, box, null);
            int width = box.getMaxX() - box.getMinX() + 1;
            for (int offset : new int[]{width + 1, width, width - 1}) {
                BlockPos next = origin.east(offset);
                BlockBox nextBox = StructureRotationUtil.computeBoundingBox(next, BuildingType.WALL, facing);
                for (int cx = nextBox.getMinX() >> 4; cx <= nextBox.getMaxX() >> 4; cx++) {
                    for (int cz = nextBox.getMinZ() >> 4; cz <= nextBox.getMaxZ() >> 4; cz++) {
                        world.getChunk(cx, cz);
                    }
                }
                var site = new BuildData.BuildSite(next, BuildingType.WALL, 0, false, owner, facing);
                context.assertEquals(BuildingValidator.validate(world, site).isEmpty(), offset >= width,
                        "Completed wall adjacency must use inclusive block bounds without padding: "
                                + BuildingValidator.validate(world, site));
            }
            state.entries().removeIf(b -> b.origin.equals(origin));
            var pending = new BuildData.BuildSite(origin, BuildingType.WALL, 0, false, owner, facing);
            BuildData.getOrCreate(world).add(pending);
            for (int offset : new int[]{width, width - 1}) {
                var site = new BuildData.BuildSite(origin.east(offset), BuildingType.WALL, 0, false, owner, facing);
                context.assertEquals(BuildingValidator.validate(world, site).isEmpty(), offset == width,
                        "Pending wall adjacency must not allow a one-block overlap");
            }
            BuildData.getOrCreate(world).remove(pending);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void foundationsReachDeepGroundAndStopAtWorldBottom(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(0, 40, 3000));
        for (int mode = 0; mode < 3; mode++) {
            BlockPos base = origin.east(mode * 32);
            world.getChunk(base.getX() >> 4, base.getZ() >> 4);
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 3; z++) {
                    for (int y = world.getBottomY(); y < base.getY(); y++) {
                        world.setBlockState(new BlockPos(base.getX() + x, y, base.getZ() + z),
                                Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
            int groundY = mode == 0 ? base.getY() - 17 : mode == 1 ? base.getY() - 2 : world.getBottomY() - 1;
            if (mode != 2) {
                world.setBlockState(new BlockPos(base.getX(), groundY, base.getZ()), Blocks.STONE.getDefaultState());
            }
            StructurePlacer.place(world, base, BuildingType.WALL);
            for (int y = base.getY() - 1; y > groundY; y--) {
                context.assertFalse(world.getBlockState(new BlockPos(base.getX(), y, base.getZ())).isAir(),
                        "No gap may remain below the foundation, even in a bottomless column");
            }
            if (mode != 2) {
                context.assertTrue(world.getBlockState(new BlockPos(base.getX(), groundY, base.getZ())).isOf(Blocks.STONE),
                        "Existing ground must not be overwritten");
            }
        }
        context.complete();
    }

    private static BlockPos local(BlockPos origin, BuildingType type, Direction facing, int x, int y, int z) {
        int[] dims = StructureRotationUtil.getBuildingDimensions(type);
        return StructureRotationUtil.transformPos(origin, x, y, z, dims[0], dims[2], facing);
    }

    private static void stableFixture(TestContext context, BlockPos origin, BuildingType type, Direction facing,
                                      BlockPos offset, net.minecraft.block.Block expected) {
        ServerWorld world = context.getWorld();
        BlockPos pos = local(origin, type, facing, offset.getX(), offset.getY(), offset.getZ());
        BlockState state = world.getBlockState(pos);
        context.assertTrue(state.isOf(expected) && state.canPlaceAt(world, pos), "Fixture must have valid support: " + pos);
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.offset(side);
            world.updateNeighborsAlways(neighbor, world.getBlockState(neighbor).getBlock());
            state = state.getStateForNeighborUpdate(side, world.getBlockState(neighbor), world, pos, neighbor);
        }
        context.assertTrue(state.isOf(expected) && net.minecraft.block.Block.postProcessState(state, world, pos).isOf(expected),
                "Fixture must survive support-side shape updates: " + pos);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyBlueprintStaysInsideItsPreviewBox(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 260, 0));
        BuildingType[] types = BuildingType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < FACINGS.length; j++) {
                checkFootprint(context, world, base.add(i * 24, 0, j * 24), types[i], FACINGS[j]);
            }
        }
        context.complete();
    }

    private static void checkFootprint(TestContext context, ServerWorld world, BlockPos origin,
                                       BuildingType type, Direction facing) {
        BlockBox box = StructureRotationUtil.computeBoundingBox(origin, type, facing);
        BlockBox shell = box.expand(1);
        for (int cx = shell.getMinX() >> 4; cx <= shell.getMaxX() >> 4; cx++) {
            for (int cz = shell.getMinZ() >> 4; cz <= shell.getMaxZ() >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }
        forEachOutside(box, pos -> world.setBlockState(pos, SENTINEL));

        StructurePlacer.place(world, origin, type, facing);

        forEachOutside(box, pos -> {
            if (!world.getBlockState(pos).equals(SENTINEL)) {
                throw new AssertionError(type + " facing " + facing + " changed " + pos
                        + " outside its preview box " + box + ": " + world.getBlockState(pos));
            }
        });
        int built = 0;
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY() + 1, box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            if (!world.getBlockState(pos).isAir()) {
                built++;
            }
        }
        context.assertTrue(built > 0, type + " facing " + facing + " placed nothing above the floor");

        if (type == BuildingType.HOUSE || type == BuildingType.BARRACKS || type == BuildingType.BLACKSMITH) {
            boolean frontDoor = false;
            for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(),
                    box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock
                        && state.get(DoorBlock.FACING) == facing.getOpposite()) {
                    frontDoor = true;
                    break;
                }
            }
            context.assertTrue(frontDoor, type + " facing " + facing + " has no door opening toward the front");
        }
        if (type == BuildingType.HERO_SANCTUARY) {
            BlockPos c = com.kingdomunique.world.HeroSanctuaryBuilder.CORE_OFFSET;
            int[] d = StructureRotationUtil.getBuildingDimensions(type);
            BlockPos core = StructureRotationUtil.transformPos(origin, c.getX(), c.getY(), c.getZ(), d[0], d[2], facing);
            context.assertTrue(world.getBlockState(core).isOf(net.minecraft.block.Blocks.LODESTONE),
                    "HERO_SANCTUARY facing " + facing + " has no lodestone at its core");
        }
        if (type == BuildingType.GATE) {
            for (BlockPos leaf : StructurePlacer.gateLeafPositions(origin, facing)) {
                context.assertTrue(world.getBlockState(leaf).isOf(StructurePlacer.GATE_LEAF_CLOSED.getBlock()),
                        "Gate leaf missing at " + leaf + " facing " + facing);
            }
        }
    }

    /** 枠の側面1マスと上面1マス（床の段より下は支えが入るので対象外）。 */
    private static void forEachOutside(BlockBox box, java.util.function.Consumer<BlockPos> action) {
        for (int x = box.getMinX() - 1; x <= box.getMaxX() + 1; x++) {
            for (int z = box.getMinZ() - 1; z <= box.getMaxZ() + 1; z++) {
                for (int y = box.getMinY(); y <= box.getMaxY() + 1; y++) {
                    if (!box.contains(x, y, z)) {
                        action.accept(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }
}
