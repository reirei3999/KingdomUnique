package com.kingdomunique.building;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;

import java.util.List;

import static com.kingdomunique.building.LocalBuilder.*;

/** 穀物庫・救護院・交易場・軍事庫・訓練場の設計（正面 = +Z、サバイバル入手可能ブロックのみ）。 */
final class CivicStructures {

    private CivicStructures() {
    }

    /** 王立穀物庫 9x7x9：石の腰壁とオークの木組み、干し草の妻壁、トウヒの大屋根。中は樽と干し草の蔵。 */
    static List<StructurePlacer.Placement> granary() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 7, zb = 1, zf = 7, top = 3;
        b.fill(x1, -1, zb, x2, -1, zf, Blocks.STONE_BRICKS.getDefaultState());
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.SPRUCE_PLANKS.getDefaultState());
        frame(b, x1, x2, zb, zf, top, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_PLANKS.getDefaultState(), Blocks.OAK_LOG);
        for (int x : new int[]{2, 6}) {
            b.set(x, 1, zf, Blocks.GLASS_PANE.getDefaultState());
            b.set(x, 1, zb, Blocks.GLASS_PANE.getDefaultState());
        }
        b.set(x1, 1, 4, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 1, 4, Blocks.GLASS_PANE.getDefaultState());
        b.door(4, 0, zf, Blocks.SPRUCE_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.gableRoof(x1, x2, zb, zf, top, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.HAY_BLOCK.getDefaultState());
        b.fill(x1 + 1, top, 4, x2 - 1, top, 4, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        b.set(4, 2, 4, hangingLantern(Blocks.LANTERN));
        b.set(4, 2, zf + 1, hangingLantern(Blocks.LANTERN));
        for (int x = 2; x <= 6; x++) {
            b.set(x, 0, 2, x % 2 == 0 ? Blocks.BARREL.getDefaultState() : Blocks.HAY_BLOCK.getDefaultState());
            b.set(x, 1, 2, x == 4 ? Blocks.HAY_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        b.fill(2, 0, 4, 2, 0, 5, Blocks.HAY_BLOCK.getDefaultState());
        b.set(6, 0, 4, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
        b.set(6, 0, 5, Blocks.COMPOSTER.getDefaultState());
        b.set(1, 0, 8, Blocks.HAY_BLOCK.getDefaultState());
        b.set(2, 0, 8, Blocks.HAY_BLOCK.getDefaultState());
        b.set(1, 1, 8, Blocks.HAY_BLOCK.getDefaultState());
        b.set(7, 0, 8, Blocks.BARREL.getDefaultState());
        b.set(4, -1, 8, Blocks.DIRT_PATH.getDefaultState());
        return b.placements();
    }

    /** 王立救護院 9x7x7：方解石の白壁とシラカバの木組み、赤い屋根、病床3つと薬棚、入口の赤い旗。 */
    static List<StructurePlacer.Placement> infirmary() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 7, zb = 1, zf = 5, top = 3;
        b.fill(x1, -1, zb, x2, -1, zf, Blocks.POLISHED_DIORITE.getDefaultState());
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.BIRCH_PLANKS.getDefaultState());
        frame(b, x1, x2, zb, zf, top, Blocks.BIRCH_LOG, Blocks.CALCITE.getDefaultState(), Blocks.STRIPPED_BIRCH_LOG);
        for (int x : new int[]{2, 6}) {
            shutteredWindow(b, x, 1, zf, Blocks.BIRCH_TRAPDOOR);
        }
        b.set(3, 1, zb, Blocks.GLASS_PANE.getDefaultState());
        b.set(5, 1, zb, Blocks.GLASS_PANE.getDefaultState());
        b.set(x1, 1, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 1, 3, Blocks.GLASS_PANE.getDefaultState());
        b.door(4, 0, zf, Blocks.BIRCH_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.gableRoof(x1, x2, zb, zf, top, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB,
                Blocks.BIRCH_PLANKS.getDefaultState());
        b.set(x1, 4, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 4, 3, Blocks.GLASS_PANE.getDefaultState());
        b.fill(x1 + 1, top, 3, x2 - 1, top, 3, log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.X));
        b.set(3, 2, 3, hangingLantern(Blocks.LANTERN));
        b.set(5, 2, 3, hangingLantern(Blocks.LANTERN));
        for (int x : new int[]{2, 4, 6}) {
            b.bed(x, 0, 2, Blocks.WHITE_BED, Direction.NORTH);
        }
        b.set(3, 0, 2, Blocks.POTTED_LILY_OF_THE_VALLEY.getDefaultState());
        b.set(5, 0, 2, Blocks.BREWING_STAND.getDefaultState());
        b.set(2, 0, 4, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        b.set(6, 0, 4, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
        b.set(3, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.set(5, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.set(1, 0, 6, Blocks.POTTED_RED_TULIP.getDefaultState());
        b.set(7, 0, 6, Blocks.POTTED_WHITE_TULIP.getDefaultState());
        b.set(4, -1, 6, Blocks.DIRT_PATH.getDefaultState());
        return b.placements();
    }

    /** 王都交易場 9x6x7：柱と梁だけの吹き放しの市場ホール。露店の台、商品の樽や作物、中央の市の鐘。 */
    static List<StructurePlacer.Placement> market() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 7, zb = 1, zf = 5, top = 3;
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 6; z++) {
                int n = noise(x, -1, z);
                b.set(x, -1, z, (n < 50 ? Blocks.STONE_BRICKS : n < 80 ? Blocks.POLISHED_ANDESITE
                        : Blocks.COBBLESTONE).getDefaultState());
            }
        }
        for (int x : new int[]{x1, 4, x2}) {
            for (int z : new int[]{zb, zf}) {
                b.fill(x, 0, z, x, top - 1, z, Blocks.SPRUCE_LOG.getDefaultState());
            }
        }
        b.fill(x1, 0, 3, x1, top - 1, 3, Blocks.SPRUCE_LOG.getDefaultState());
        b.fill(x2, 0, 3, x2, top - 1, 3, Blocks.SPRUCE_LOG.getDefaultState());
        b.fill(x1, top, zb, x2, top, zb, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.fill(x1, top, zf, x2, top, zf, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.fill(x1, top, zb + 1, x1, top, zf - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        b.fill(x2, top, zb + 1, x2, top, zf - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        b.fill(x1 + 1, top, 3, x2 - 1, top, 3, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        for (int x : new int[]{2, 3, 5, 6}) {
            b.set(x, top - 1, zf, stairsTop(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            b.set(x, top - 1, zb, stairsTop(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
        b.gableRoof(x1, x2, zb, zf, top, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                Blocks.SPRUCE_PLANKS.getDefaultState());
        // 露店の台（西：農産物、東：工芸品）
        b.fill(2, 0, 2, 3, 0, 2, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
        b.set(2, 1, 2, Blocks.MELON.getDefaultState());
        b.set(3, 1, 2, Blocks.PUMPKIN.getDefaultState());
        b.fill(5, 0, 2, 6, 0, 2, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
        b.set(5, 1, 2, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        b.set(6, 1, 2, Blocks.LOOM.getDefaultState());
        b.set(2, 0, 4, Blocks.BARREL.getDefaultState());
        b.set(3, 0, 4, Blocks.HAY_BLOCK.getDefaultState());
        b.set(6, 0, 4, Blocks.BARREL.getDefaultState());
        b.set(5, 0, 4, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        b.set(4, 2, 3, Blocks.BELL.getDefaultState().with(BellBlock.ATTACHMENT, Attachment.CEILING)
                .with(BellBlock.FACING, Direction.SOUTH));
        b.set(2, 2, 3, hangingLantern(Blocks.LANTERN));
        b.set(6, 2, 3, hangingLantern(Blocks.LANTERN));
        b.set(0, 0, 6, Blocks.BARREL.getDefaultState());
        b.set(8, 0, 6, Blocks.COMPOSTER.getDefaultState());
        b.set(8, 0, 0, Blocks.HAY_BLOCK.getDefaultState());
        return b.placements();
    }

    /** 王立軍事庫 9x6x7：深層岩レンガの要塞倉庫。平屋根に胸壁、鉄格子の窓、武具の保管庫と手入れ台。 */
    static List<StructurePlacer.Placement> arsenal() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 7, zb = 1, zf = 5, top = 4;
        b.fill(0, -1, 0, 8, -1, 6, Blocks.COBBLED_DEEPSLATE.getDefaultState());
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.POLISHED_DEEPSLATE.getDefaultState());
        for (int y = 0; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = zb; z <= zf; z++) {
                    boolean edgeX = x == x1 || x == x2;
                    boolean edgeZ = z == zb || z == zf;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    int n = noise(x, y, z);
                    BlockState wall = y == 0 ? Blocks.COBBLED_DEEPSLATE.getDefaultState()
                            : (n < 75 ? Blocks.DEEPSLATE_BRICKS : Blocks.CRACKED_DEEPSLATE_BRICKS).getDefaultState();
                    b.set(x, y, z, edgeX && edgeZ ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : wall);
                }
            }
        }
        b.fill(x1, top, zb, x2, top, zf, Blocks.DEEPSLATE_TILES.getDefaultState());
        for (int x = x1; x <= x2; x++) {
            for (int z = zb; z <= zf; z++) {
                boolean edgeX = x == x1 || x == x2;
                boolean edgeZ = z == zb || z == zf;
                if (edgeX && edgeZ) {
                    b.set(x, top + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
                } else if (edgeX || edgeZ) {
                    b.set(x, top + 1, z, (x + z) % 2 == 0 ? Blocks.DEEPSLATE_BRICKS.getDefaultState()
                            : Blocks.DEEPSLATE_BRICK_WALL.getDefaultState());
                }
            }
        }
        // 入口のアーチと旗
        b.door(4, 0, zf, Blocks.DARK_OAK_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.set(4, 2, zf, Blocks.CHISELED_DEEPSLATE.getDefaultState());
        b.set(3, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.set(5, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.set(3, 0, zf + 1, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
        b.set(3, 1, zf + 1, Blocks.SOUL_LANTERN.getDefaultState());
        b.set(5, 0, zf + 1, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
        b.set(5, 1, zf + 1, Blocks.SOUL_LANTERN.getDefaultState());
        for (int x : new int[]{2, 6}) {
            b.set(x, 2, zf, Blocks.IRON_BARS.getDefaultState());
            b.set(x, 2, zb, Blocks.IRON_BARS.getDefaultState());
        }
        b.set(x1, 2, 3, Blocks.IRON_BARS.getDefaultState());
        b.set(x2, 2, 3, Blocks.IRON_BARS.getDefaultState());
        // 保管庫
        for (int x = 2; x <= 6; x++) {
            b.set(x, 0, 2, x % 2 == 0 ? Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH)
                    : Blocks.BARREL.getDefaultState());
        }
        b.set(2, 0, 4, Blocks.SMITHING_TABLE.getDefaultState());
        b.set(6, 0, 4, Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.NORTH));
        b.set(6, 0, 3, Blocks.GRINDSTONE.getDefaultState()
                .with(GrindstoneBlock.FACE, BlockFace.FLOOR).with(GrindstoneBlock.FACING, Direction.NORTH));
        b.set(2, 0, 3, Blocks.FLETCHING_TABLE.getDefaultState());
        b.set(4, 3, 3, hangingLantern(Blocks.LANTERN));
        return b.placements();
    }

    /** 英雄訓練場 11x4x11：柵で囲った土と砂の練兵場。的、わら人形、武具棚、休憩所。 */
    static List<StructurePlacer.Placement> trainingGround() {
        LocalBuilder b = new LocalBuilder();
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
                int n = noise(x, -1, z);
                b.set(x, -1, z, edge ? Blocks.STONE_BRICKS.getDefaultState()
                        : (n < 45 ? Blocks.COARSE_DIRT : n < 75 ? Blocks.PACKED_MUD : n < 90 ? Blocks.GRAVEL
                        : Blocks.SAND).getDefaultState());
                if (!edge) {
                    continue;
                }
                boolean post = (x == 0 || x == 10 || x == 3 || x == 7) && (z == 0 || z == 10)
                        || (x == 0 || x == 10) && (z == 5);
                if (post) {
                    b.fill(x, 0, z, x, 1, z, Blocks.SPRUCE_LOG.getDefaultState());
                    b.set(x, 2, z, Blocks.LANTERN.getDefaultState());
                } else if (z == 10 && x >= 4 && x <= 6) {
                    b.set(x, 0, z, Blocks.SPRUCE_FENCE_GATE.getDefaultState()
                            .with(FenceGateBlock.FACING, Direction.SOUTH).with(FenceGateBlock.OPEN, true));
                } else {
                    b.set(x, 0, z, Blocks.SPRUCE_FENCE.getDefaultState());
                }
            }
        }
        for (int z = 6; z <= 10; z++) {
            b.set(5, -1, z, Blocks.DIRT_PATH.getDefaultState());
        }
        // 弓の的
        for (int x : new int[]{2, 5, 8}) {
            b.set(x, 0, 1, Blocks.HAY_BLOCK.getDefaultState());
            b.set(x, 1, 1, Blocks.TARGET.getDefaultState());
        }
        // わら人形
        for (int x : new int[]{3, 7}) {
            b.set(x, 0, 5, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(x, 1, 5, Blocks.HAY_BLOCK.getDefaultState());
            b.set(x, 2, 5, Blocks.CARVED_PUMPKIN.getDefaultState().with(CarvedPumpkinBlock.FACING, Direction.SOUTH));
        }
        // 武具棚
        b.set(1, 0, 3, Blocks.BARREL.getDefaultState());
        b.set(1, 0, 4, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        b.set(1, 0, 6, Blocks.GRINDSTONE.getDefaultState()
                .with(GrindstoneBlock.FACE, BlockFace.FLOOR).with(GrindstoneBlock.FACING, Direction.EAST));
        b.set(1, 0, 7, Blocks.FLETCHING_TABLE.getDefaultState());
        // 休憩所
        b.set(9, 0, 6, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        b.set(9, 0, 7, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        b.set(9, 0, 8, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        b.set(9, 0, 3, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(9, 1, 3, Blocks.RED_BANNER.getDefaultState());
        return b.placements();
    }

    /** 柱・腰壁・壁・上端の梁。腰壁は丸石混ぜ。 */
    private static void frame(LocalBuilder b, int x1, int x2, int zb, int zf, int top,
                              Block post, BlockState wall, Block beam) {
        for (int y = 0; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = zb; z <= zf; z++) {
                    boolean edgeX = x == x1 || x == x2;
                    boolean edgeZ = z == zb || z == zf;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    boolean isPost = (edgeX && edgeZ) || (edgeZ && (x == x1 + 2 || x == x2 - 2) && x2 - x1 >= 6)
                            || (edgeX && (z - zb) % 3 == 0);
                    b.set(x, y, z, isPost ? post.getDefaultState() : y == 0 ? cobbleMix(x, y, z) : wall);
                }
            }
        }
        b.fill(x1, top, zb, x2, top, zb, log(beam, Direction.Axis.X));
        b.fill(x1, top, zf, x2, top, zf, log(beam, Direction.Axis.X));
        b.fill(x1, top, zb + 1, x1, top, zf - 1, log(beam, Direction.Axis.Z));
        b.fill(x2, top, zb + 1, x2, top, zf - 1, log(beam, Direction.Axis.Z));
    }

    private static void shutteredWindow(LocalBuilder b, int x, int y, int z, Block trapdoor) {
        b.set(x, y, z, Blocks.GLASS_PANE.getDefaultState());
        b.set(x - 1, y, z + 1, shutter(trapdoor, Direction.SOUTH));
        b.set(x + 1, y, z + 1, shutter(trapdoor, Direction.SOUTH));
    }
}
