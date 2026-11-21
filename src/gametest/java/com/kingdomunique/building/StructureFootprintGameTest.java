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
