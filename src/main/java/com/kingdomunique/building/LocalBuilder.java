package com.kingdomunique.building;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** ローカル座標（正面 = +Z）で設計図の配置リストを組み立てる小さなヘルパー。後から置いたブロックが優先される。 */
final class LocalBuilder {
    private final List<StructurePlacer.Placement> placements = new ArrayList<>();

    List<StructurePlacer.Placement> placements() {
        return placements;
    }

    void set(int x, int y, int z, BlockState state) {
        placements.add(new StructurePlacer.Placement(new BlockPos(x, y, z), state));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** 位置から決まる 0〜99 の値（石材の混ぜ合わせ用）。 */
    static int noise(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return Math.floorMod(h ^ (h >>> 13), 100);
    }

    /** 石レンガ6：ひび割れ2：苔1：安山岩1 の割合で混ぜる。 */
    static BlockState stoneMix(int x, int y, int z) {
        int n = noise(x, y, z);
        Block block = n < 60 ? net.minecraft.block.Blocks.STONE_BRICKS
                : n < 80 ? net.minecraft.block.Blocks.CRACKED_STONE_BRICKS
                : n < 90 ? net.minecraft.block.Blocks.MOSSY_STONE_BRICKS
                : net.minecraft.block.Blocks.ANDESITE;
        return block.getDefaultState();
    }

    /** 丸石主体に苔むした丸石を混ぜる。 */
    static BlockState cobbleMix(int x, int y, int z) {
        return (noise(x, y, z) < 75 ? net.minecraft.block.Blocks.COBBLESTONE
                : net.minecraft.block.Blocks.MOSSY_COBBLESTONE).getDefaultState();
    }

    static BlockState stairs(Block block, Direction facing) {
        return block.getDefaultState().with(StairsBlock.FACING, facing);
    }

    static BlockState stairsTop(Block block, Direction facing) {
        return stairs(block, facing).with(StairsBlock.HALF, BlockHalf.TOP);
    }

    static BlockState slab(Block block, SlabType type) {
        return block.getDefaultState().with(SlabBlock.TYPE, type);
    }

    static BlockState log(Block block, Direction.Axis axis) {
        return block.getDefaultState().with(PillarBlock.AXIS, axis);
    }

    static BlockState hangingLantern(Block lantern) {
        return lantern.getDefaultState().with(LanternBlock.HANGING, true);
    }

    /** 開いた状態で壁に貼り付く雨戸。facing は壁から離れる向き。 */
    static BlockState shutter(Block trapdoor, Direction facing) {
        return trapdoor.getDefaultState().with(TrapdoorBlock.FACING, facing).with(TrapdoorBlock.OPEN, true);
    }

    /** 扉（下段・上段）。facing は外から入るときに向く方向。 */
    void door(int x, int y, int z, Block door, Direction facing, DoorHinge hinge) {
        BlockState lower = door.getDefaultState().with(DoorBlock.FACING, facing)
                .with(DoorBlock.HINGE, hinge).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** ベッド。(x, z) が枕側、headFacing は足元から枕へ向かう方向。 */
    void bed(int x, int y, int z, Block bed, Direction headFacing) {
        BlockState head = bed.getDefaultState().with(BedBlock.FACING, headFacing).with(BedBlock.PART, BedPart.HEAD);
        set(x - headFacing.getOffsetX(), y, z - headFacing.getOffsetZ(), head.with(BedBlock.PART, BedPart.FOOT));
        set(x, y, z, head);
    }

    /**
     * X 方向に棟が走る切妻屋根。壁の上端 wallTop の段に軒（zBack-1 と zFront+1）を置き、
     * 1段ごとに1マス内側へ上がる。両妻は1マス張り出し、妻壁 (gableWestX / gableEastX) を埋める。
     */
    void gableRoof(int xWest, int xEast, int zBack, int zFront, int wallTop,
                   Block stair, Block ridgeSlab, BlockState gable) {
        int zLow = zBack - 1;
        int zHigh = zFront + 1;
        for (int k = 0; zLow + k <= zHigh - k; k++) {
            int y = wallTop + k;
            int north = zLow + k;
            int south = zHigh - k;
            for (int x = xWest - 1; x <= xEast + 1; x++) {
                if (north == south) {
                    set(x, y, north, slab(ridgeSlab, SlabType.BOTTOM));
                } else {
                    set(x, y, north, stairs(stair, Direction.SOUTH));
                    set(x, y, south, stairs(stair, Direction.NORTH));
                }
            }
            if (k > 0) {
                for (int z = north + 1; z < south; z++) {
                    set(xWest, y, z, gable);
                    set(xEast, y, z, gable);
                }
            }
        }
    }
}
