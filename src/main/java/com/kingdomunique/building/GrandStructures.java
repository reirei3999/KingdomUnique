package com.kingdomunique.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;

import java.util.List;

import static com.kingdomunique.building.LocalBuilder.*;

/** 英雄召喚の祭壇と王宮。王国の象徴なので一回り大きく作り込む（正面 = +Z）。 */
final class GrandStructures {

    private GrandStructures() {
    }

    /**
     * 英雄召喚の祭壇 17x13x17：三段の白い基壇（ジッグラト）、七本の光の柱、水盤、
     * 正面の神門、中央の天蓋とその下の金の台座・ロードストーン。
     */
    static List<StructurePlacer.Placement> heroSanctuary() {
        LocalBuilder b = new LocalBuilder();
        b.fill(0, -1, 0, 16, -1, 16, Blocks.CALCITE.getDefaultState());

        // 一段目：外周の階段と白い参道
        stairRing(b, 0, 16, 0, Blocks.QUARTZ_STAIRS, false);
        for (int x = 1; x <= 15; x++) {
            for (int z = 1; z <= 15; z++) {
                b.set(x, 0, z, (x == 8 || z == 8 ? Blocks.QUARTZ_BRICKS
                        : (x + z) % 4 == 0 ? Blocks.CALCITE : Blocks.SMOOTH_QUARTZ).getDefaultState());
            }
        }
        // 四方の水盤（一段目の外縁、柱のあいだ）
        for (int i : new int[]{5, 6, 7, 9, 10, 11}) {
            b.set(1, 0, i, Blocks.WATER.getDefaultState());
            b.set(15, 0, i, Blocks.WATER.getDefaultState());
            b.set(i, 0, 1, Blocks.WATER.getDefaultState());
        }

        // 二段目
        stairRing(b, 3, 13, 1, Blocks.QUARTZ_STAIRS, false);
        b.fill(4, 1, 4, 12, 1, 12, Blocks.SMOOTH_QUARTZ.getDefaultState());
        for (int i = 4; i <= 12; i++) {
            for (int[] c : new int[][]{{i, 4}, {i, 12}, {4, i}, {12, i}}) {
                b.set(c[0], 1, c[1], Blocks.QUARTZ_BRICKS.getDefaultState());
            }
        }

        // 三段目（台座）
        stairRing(b, 5, 11, 2, Blocks.QUARTZ_STAIRS, false);
        b.fill(6, 2, 6, 10, 2, 10, Blocks.SMOOTH_QUARTZ.getDefaultState());
        b.fill(7, 2, 7, 9, 2, 9, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
        b.set(8, 2, 8, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(8, 3, 8, Blocks.LODESTONE.getDefaultState());
        for (int[] c : new int[][]{{7, 7}, {9, 7}, {7, 9}, {9, 9}}) {
            b.set(c[0], 3, c[1], Blocks.WHITE_CANDLE.getDefaultState()
                    .with(CandleBlock.CANDLES, 4).with(CandleBlock.LIT, true));
        }
        b.set(6, 3, 8, Blocks.AMETHYST_CLUSTER.getDefaultState());
        b.set(10, 3, 8, Blocks.AMETHYST_CLUSTER.getDefaultState());
        b.set(8, 3, 6, Blocks.AMETHYST_CLUSTER.getDefaultState());

        // 天蓋：四本柱、軒の蛇腹、三段のドーム、頂の金と光
        for (int x : new int[]{6, 10}) {
            for (int z : new int[]{6, 10}) {
                b.fill(x, 3, z, x, 7, z, Blocks.QUARTZ_PILLAR.getDefaultState());
            }
        }
        stairRing(b, 5, 11, 8, Blocks.QUARTZ_STAIRS, true);
        for (int i = 6; i <= 10; i++) {
            for (int[] c : new int[][]{{i, 6}, {i, 10}, {6, i}, {10, i}}) {
                b.set(c[0], 8, c[1], Blocks.SMOOTH_QUARTZ.getDefaultState());
            }
        }
        for (int x : new int[]{6, 10}) {
            for (int z : new int[]{6, 10}) {
                b.set(x, 8, z, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            }
        }
        b.fill(6, 9, 6, 10, 9, 10, Blocks.SMOOTH_QUARTZ.getDefaultState());
        stairRing(b, 5, 11, 9, Blocks.QUARTZ_STAIRS, false);
        stairRing(b, 6, 10, 10, Blocks.QUARTZ_STAIRS, false);
        b.fill(7, 10, 7, 9, 10, 9, Blocks.SMOOTH_QUARTZ.getDefaultState());
        stairRing(b, 7, 9, 11, Blocks.QUARTZ_STAIRS, false);
        b.set(8, 11, 8, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(8, 12, 8, Blocks.END_ROD.getDefaultState());
        b.set(8, 13, 8, Blocks.END_ROD.getDefaultState());
        for (int[] c : new int[][]{{8, 5}, {8, 11}, {5, 8}, {11, 8}}) {
            b.set(c[0], 9, c[1], Blocks.GOLD_BLOCK.getDefaultState());
        }
        b.set(8, 8, 8, Blocks.CHAIN.getDefaultState());
        b.set(8, 7, 8, hangingLantern(Blocks.LANTERN));

        // 光の柱（オベリスク）7本。正面の中央は神門
        for (int[] c : new int[][]{{2, 2}, {8, 2}, {14, 2}, {2, 8}, {14, 8}, {2, 14}, {14, 14}}) {
            b.set(c[0], 1, c[1], Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            b.fill(c[0], 2, c[1], c[0], 5, c[1], Blocks.QUARTZ_PILLAR.getDefaultState());
            b.set(c[0], 6, c[1], Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            b.set(c[0], 7, c[1], Blocks.SEA_LANTERN.getDefaultState());
            b.set(c[0], 8, c[1], Blocks.END_ROD.getDefaultState());
        }

        // 神門
        for (int x : new int[]{6, 10}) {
            b.set(x, 1, 15, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            b.fill(x, 2, 15, x, 5, 15, Blocks.QUARTZ_PILLAR.getDefaultState());
        }
        b.fill(5, 6, 15, 11, 6, 15, Blocks.SMOOTH_QUARTZ.getDefaultState());
        b.set(8, 6, 15, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(5, 6, 15, stairsTop(Blocks.QUARTZ_STAIRS, Direction.EAST));
        b.set(11, 6, 15, stairsTop(Blocks.QUARTZ_STAIRS, Direction.WEST));
        b.fill(5, 7, 15, 11, 7, 15, slab(Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM));
        b.set(4, 7, 15, stairs(Blocks.QUARTZ_STAIRS, Direction.EAST));
        b.set(12, 7, 15, stairs(Blocks.QUARTZ_STAIRS, Direction.WEST));
        b.set(7, 5, 15, hangingLantern(Blocks.SOUL_LANTERN));
        b.set(9, 5, 15, hangingLantern(Blocks.SOUL_LANTERN));

        // 白い花
        for (int[] c : new int[][]{{4, 14}, {12, 14}, {4, 2}, {12, 2}, {2, 4}, {14, 4}, {2, 12}, {14, 12}}) {
            b.set(c[0], 1, c[1], (c[1] == 14 ? Blocks.POTTED_WHITE_TULIP : Blocks.POTTED_LILY_OF_THE_VALLEY)
                    .getDefaultState());
        }
        return b.placements();
    }

    /**
     * 王宮 19x16x19：石の基壇と大階段、正面の柱廊と破風、四隅の尖塔、両翼の回廊とテラス、
     * 玉座の間（赤絨毯・金の玉座・シャンデリア・旗）、噴水と生垣の前庭。
     */
    static List<StructurePlacer.Placement> royalPalace() {
        LocalBuilder b = new LocalBuilder();
        for (int x = 0; x <= 18; x++) {
            for (int z = 0; z <= 18; z++) {
                b.set(x, -1, z, stoneMix(x, -1, z));
            }
        }
        b.fill(1, 0, 1, 17, 0, 16, Blocks.POLISHED_ANDESITE.getDefaultState());
        b.fill(5, 0, 17, 13, 0, 17, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        for (int x = 5; x <= 13; x++) {
            for (int z = 4; z <= 15; z++) {
                b.set(x, 0, z, ((x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_BLACKSTONE)
                        .getDefaultState());
            }
        }

        // 大広間 x4..14 z3..13、壁 y1..6、上端の帯 y7
        int x1 = 4, x2 = 14, zb = 3, zf = 13, top = 7;
        for (int y = 1; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = zb; z <= zf; z++) {
                    boolean edgeX = x == x1 || x == x2;
                    boolean edgeZ = z == zb || z == zf;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    boolean pilaster = (edgeX && (z - zb) % 3 == 0) || (edgeZ && (x - x1) % 2 == 0);
                    b.set(x, y, z, y == 1 ? Blocks.STONE_BRICKS.getDefaultState()
                            : pilaster ? Blocks.QUARTZ_PILLAR.getDefaultState()
                            : Blocks.QUARTZ_BRICKS.getDefaultState());
                }
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = zb; z <= zf; z++) {
                if (x == x1 || x == x2 || z == zb || z == zf) {
                    boolean pilaster = ((x == x1 || x == x2) && (z - zb) % 3 == 0)
                            || ((z == zb || z == zf) && (x - x1) % 2 == 0);
                    b.set(x, top, z, (pilaster ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ).getDefaultState());
                }
            }
        }
        // 高窓（両翼の屋上テラスより上）と正面の窓
        for (int z : new int[]{5, 8, 11}) {
            b.fill(x1, 5, z, x1, 6, z, Blocks.GLASS_PANE.getDefaultState());
            b.fill(x2, 5, z, x2, 6, z, Blocks.GLASS_PANE.getDefaultState());
        }
        for (int x : new int[]{7, 11}) {
            b.fill(x, 2, zf, x, 5, zf, Blocks.GLASS_PANE.getDefaultState());
            b.fill(x, 3, zb, x, 5, zb, Blocks.GLASS_PANE.getDefaultState());
        }
        // 正面の扉と金縁のアーチ
        b.door(9, 1, zf, Blocks.DARK_OAK_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.set(9, 3, zf, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(8, 3, zf, stairsTop(Blocks.QUARTZ_STAIRS, Direction.WEST));
        b.set(10, 3, zf, stairsTop(Blocks.QUARTZ_STAIRS, Direction.EAST));
        b.set(9, 4, zf, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
        // 両翼への出入口
        b.fill(x1, 1, 8, x1, 2, 8, Blocks.AIR.getDefaultState());
        b.fill(x2, 1, 8, x2, 2, 8, Blocks.AIR.getDefaultState());

        // 屋根：正面の柱廊まで伸ばし、正面妻を破風にする
        b.gableRoofZ(x1, x2, zb, 15, top, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB,
                Blocks.QUARTZ_BRICKS.getDefaultState());
        b.set(9, 10, 15, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(8, 9, 15, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
        b.set(10, 9, 15, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
        b.set(9, 9, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(9, 10, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(9, 14, 16, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(9, 15, 16, Blocks.RED_BANNER.getDefaultState());

        // 柱廊 z14..15
        b.fill(x1, top, 15, x2, top, 15, Blocks.SMOOTH_QUARTZ.getDefaultState());
        for (int x : new int[]{5, 7, 11, 13}) {
            b.set(x, 1, 15, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            b.fill(x, 2, 15, x, 6, 15, Blocks.QUARTZ_PILLAR.getDefaultState());
            b.set(x, top, 15, Blocks.GOLD_BLOCK.getDefaultState());
        }
        for (int x : new int[]{6, 9, 12}) {
            b.set(x, 6, 15, hangingLantern(Blocks.LANTERN));
        }

        // 両翼：回廊（壁 y1..4）と屋上テラス
        for (int side = 0; side < 2; side++) {
            int outer = side == 0 ? 1 : 17;
            int inner = side == 0 ? 3 : 15;
            int lo = Math.min(outer, inner);
            int hi = Math.max(outer, inner);
            for (int y = 1; y <= 4; y++) {
                for (int z = 4; z <= 12; z++) {
                    b.set(outer, y, z, y == 1 ? Blocks.STONE_BRICKS.getDefaultState()
                            : (z % 4 == 0 ? Blocks.QUARTZ_PILLAR : Blocks.QUARTZ_BRICKS).getDefaultState());
                }
            }
            for (int z : new int[]{6, 10}) {
                b.fill(outer, 2, z, outer, 3, z, Blocks.GLASS_PANE.getDefaultState());
                b.set(outer, 4, z, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            }
            b.fill(lo, 5, 4, hi, 5, 12, Blocks.SMOOTH_QUARTZ.getDefaultState());
            b.fill(outer, 6, 4, outer, 6, 12, Blocks.DIORITE_WALL.getDefaultState());
            for (int z : new int[]{4, 8, 12}) {
                b.set(outer, 7, z, Blocks.LANTERN.getDefaultState());
            }
            b.set(lo + 1, 4, 8, hangingLantern(Blocks.LANTERN));
            b.set((lo + hi) / 2, 1, 5, Blocks.BOOKSHELF.getDefaultState());
            b.set((lo + hi) / 2, 1, 11, Blocks.BOOKSHELF.getDefaultState());
        }

        // 四隅の塔と尖塔
        for (int tx : new int[]{1, 15}) {
            for (int tz : new int[]{1, 13}) {
                tower(b, tx, tz);
            }
        }

        // 玉座の間
        b.fill(7, 1, 4, 11, 1, 5, Blocks.QUARTZ_BRICKS.getDefaultState());
        b.fill(7, 1, 6, 11, 1, 6, stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        b.set(9, 2, 4, stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        b.set(8, 2, 4, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(10, 2, 4, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(7, 2, 4, Blocks.LANTERN.getDefaultState());
        b.set(11, 2, 4, Blocks.LANTERN.getDefaultState());
        b.fill(8, 2, 5, 10, 2, 5, Blocks.RED_CARPET.getDefaultState());
        b.set(9, 3, zb, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(9, 4, zb, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
        b.set(9, 5, zb, Blocks.GOLD_BLOCK.getDefaultState());
        for (int x : new int[]{6, 12}) {
            b.set(x, 4, zb + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
            b.fill(x, 1, zb + 1, x, 2, zb + 1, Blocks.BOOKSHELF.getDefaultState());
        }
        for (int z = 7; z <= 16; z++) {
            if (z != zf) {
                b.set(9, 1, z, Blocks.RED_CARPET.getDefaultState());
            }
        }
        for (int z : new int[]{7, 10}) {
            for (int x : new int[]{6, 12}) {
                b.fill(x, 1, z, x, 5, z, Blocks.QUARTZ_PILLAR.getDefaultState());
                b.set(x, 6, z, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            }
            b.set(5, 4, z + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.EAST));
            b.set(13, 4, z + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.WEST));
            // シャンデリア
            b.fill(9, 9, z, 9, 12, z, Blocks.CHAIN.getDefaultState());
            b.set(9, 8, z, Blocks.GOLD_BLOCK.getDefaultState());
            b.set(9, 7, z, hangingLantern(Blocks.LANTERN));
            for (int x : new int[]{7, 11}) {
                b.fill(x, 9, z, x, 10, z, Blocks.CHAIN.getDefaultState());
                b.set(x, 8, z, hangingLantern(Blocks.LANTERN));
            }
        }
        b.set(5, 1, 12, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        b.set(13, 1, 12, Blocks.LECTERN.getDefaultState());

        // 前庭：噴水、生垣、灯籠
        BlockState hedge = Blocks.AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        BlockState bloom = Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int cx : new int[]{2, 16}) {
            b.set(cx, 0, 17, Blocks.WATER.getDefaultState());
            b.set(cx - 1, 0, 17, Blocks.QUARTZ_BRICKS.getDefaultState());
            b.set(cx + 1, 0, 17, Blocks.QUARTZ_BRICKS.getDefaultState());
            b.set(cx, 0, 18, Blocks.QUARTZ_BRICKS.getDefaultState());
            b.set(cx, 1, 16, Blocks.QUARTZ_PILLAR.getDefaultState());
            b.set(cx, 2, 16, Blocks.SEA_LANTERN.getDefaultState());
            b.set(cx - 1, 0, 18, (cx == 2 ? hedge : bloom));
            b.set(cx + 1, 0, 18, (cx == 2 ? bloom : hedge));
        }
        for (int x : new int[]{4, 14}) {
            b.fill(x, 0, 17, x, 1, 17, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
            b.set(x, 2, 17, Blocks.LANTERN.getDefaultState());
            b.set(x, 0, 18, bloom);
        }
        for (int x = 5; x <= 13; x++) {
            b.set(x, -1, 18, Blocks.POLISHED_ANDESITE.getDefaultState());
        }
        return b.placements();
    }

    /** 3x3 の塔（x..x+2, z..z+2）。y1..10 の壁、張り出した蛇腹、尖塔、金の頂と旗。 */
    private static void tower(LocalBuilder b, int tx, int tz) {
        for (int y = 1; y <= 10; y++) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) {
                    if (dx == 1 && dz == 1) {
                        b.set(tx + dx, y, tz + dz, Blocks.AIR.getDefaultState());
                        continue;
                    }
                    boolean corner = dx != 1 && dz != 1;
                    b.set(tx + dx, y, tz + dz, y <= 2 ? stoneMix(tx + dx, y, tz + dz)
                            : (corner ? Blocks.QUARTZ_PILLAR : Blocks.QUARTZ_BRICKS).getDefaultState());
                }
            }
        }
        for (int y : new int[]{4, 5, 8}) {
            b.set(tx + 1, y, tz, Blocks.GLASS_PANE.getDefaultState());
            b.set(tx + 1, y, tz + 2, Blocks.GLASS_PANE.getDefaultState());
            b.set(tx, y, tz + 1, Blocks.GLASS_PANE.getDefaultState());
            b.set(tx + 2, y, tz + 1, Blocks.GLASS_PANE.getDefaultState());
        }
        squareStairRing(b, tx - 1, tx + 3, tz - 1, tz + 3, 10, Blocks.QUARTZ_STAIRS, true);
        b.fill(tx, 11, tz, tx + 2, 11, tz + 2, Blocks.QUARTZ_BRICKS.getDefaultState());
        squareStairRing(b, tx - 1, tx + 3, tz - 1, tz + 3, 11, Blocks.DARK_PRISMARINE_STAIRS, false);
        squareStairRing(b, tx, tx + 2, tz, tz + 2, 12, Blocks.DARK_PRISMARINE_STAIRS, false);
        b.set(tx + 1, 12, tz + 1, Blocks.DARK_PRISMARINE.getDefaultState());
        b.set(tx + 1, 13, tz + 1, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(tx + 1, 14, tz + 1, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(tx + 1, 15, tz + 1, Blocks.RED_BANNER.getDefaultState());
    }

    /** 正方形の外周に、中心へ向かって登る階段を並べる。top なら逆さ（軒の蛇腹）。 */
    private static void stairRing(LocalBuilder b, int lo, int hi, int y, Block stair, boolean top) {
        squareStairRing(b, lo, hi, lo, hi, y, stair, top);
    }

    private static void squareStairRing(LocalBuilder b, int x1, int x2, int z1, int z2, int y, Block stair,
                                        boolean top) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, y, z1, top ? stairsTop(stair, Direction.SOUTH) : stairs(stair, Direction.SOUTH));
            b.set(x, y, z2, top ? stairsTop(stair, Direction.NORTH) : stairs(stair, Direction.NORTH));
        }
        for (int z = z1 + 1; z < z2; z++) {
            b.set(x1, y, z, top ? stairsTop(stair, Direction.EAST) : stairs(stair, Direction.EAST));
            b.set(x2, y, z, top ? stairsTop(stair, Direction.WEST) : stairs(stair, Direction.WEST));
        }
    }
}
