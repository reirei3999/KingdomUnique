package com.kingdomunique.building;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;

import java.util.List;

import static com.kingdomunique.building.LocalBuilder.*;

/** 民家・兵舎・鍛冶工房の独自デザイン（正面 = +Z、サバイバル入手可能ブロックのみ）。 */
final class HomeStructures {

    private HomeStructures() {
    }

    /** 民家 9x9：丸石の土台、トウヒの木組みと方解石の白壁、ダークオークの切妻屋根、暖炉の煙突。 */
    static List<StructurePlacer.Placement> house() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 7, zb = 1, zf = 5, top = 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = zb; z <= zf; z++) {
                boolean edge = x == x1 || x == x2 || z == zb || z == zf;
                b.set(x, -1, z, edge ? cobbleMix(x, -1, z) : Blocks.SPRUCE_PLANKS.getDefaultState());
            }
        }
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.SPRUCE_PLANKS.getDefaultState());
        b.fill(x1 + 2, -1, zb + 2, x2 - 2, -1, zf - 2, Blocks.OAK_PLANKS.getDefaultState());
        timberWalls(b, x1, x2, zb, zf, top, Blocks.SPRUCE_LOG, Blocks.CALCITE.getDefaultState(), 3);
        // 窓（1マス奥の白壁にガラス、外に雨戸）
        window(b, 2, 1, zf, Direction.SOUTH);
        window(b, 6, 1, zf, Direction.SOUTH);
        b.set(x1, 1, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 1, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(3, 1, zb, Blocks.GLASS_PANE.getDefaultState());
        b.door(4, 0, zf, Blocks.SPRUCE_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.gableRoof(x1, x2, zb, zf, top, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                Blocks.SPRUCE_PLANKS.getDefaultState());
        b.set(x1, 4, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 4, 3, Blocks.GLASS_PANE.getDefaultState());
        // 室内の梁と照明
        b.fill(x1 + 1, top, 3, x2 - 1, top, 3, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        b.set(4, 2, 3, hangingLantern(Blocks.LANTERN));
        // 暖炉と煙突（屋根を抜けて焚き火の煙）
        b.set(6, 0, 2, Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, Direction.SOUTH));
        b.fill(6, 1, 2, 6, 7, 2, Blocks.BRICKS.getDefaultState());
        b.set(6, 8, 2, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.SIGNAL_FIRE, false));
        // 家具
        b.bed(2, 0, 2, Blocks.RED_BED, Direction.NORTH);
        b.set(3, 0, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        b.set(5, 0, 2, Blocks.BARREL.getDefaultState());
        b.set(6, 0, 4, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
        b.set(3, 0, 4, Blocks.SPRUCE_STAIRS.getDefaultState()
                .with(net.minecraft.block.StairsBlock.FACING, Direction.EAST));
        b.set(2, 0, 4, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(2, 1, 4, Blocks.WHITE_CARPET.getDefaultState());
        b.set(3, 0, 3, Blocks.RED_CARPET.getDefaultState());
        b.set(4, 0, 3, Blocks.RED_CARPET.getDefaultState());
        // 玄関：軒下ランタンと小道
        b.set(4, 2, zf + 1, hangingLantern(Blocks.LANTERN));
        b.set(4, -1, 6, Blocks.DIRT_PATH.getDefaultState());
        b.set(4, -1, 7, Blocks.DIRT_PATH.getDefaultState());
        b.set(4, -1, 8, Blocks.GRAVEL.getDefaultState());
        b.set(1, 0, 6, Blocks.FLOWERING_AZALEA.getDefaultState());
        b.set(7, 0, 6, Blocks.POTTED_RED_TULIP.getDefaultState());
        return b.placements();
    }

    /** 兵舎 11x11：石レンガの外壁と木組み、深層岩タイルの大屋根、二段のベッド列と武具置き場、王国旗。 */
    static List<StructurePlacer.Placement> barracks() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 9, zb = 1, zf = 7, top = 3;
        b.fill(x1, -1, zb, x2, -1, zf, Blocks.POLISHED_ANDESITE.getDefaultState());
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int y = 0; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = zb; z <= zf; z++) {
                    if (x == x1 || x == x2 || z == zb || z == zf) {
                        b.set(x, y, z, stoneMix(x, y, z));
                    }
                }
            }
        }
        for (int x : new int[]{x1, 3, 7, x2}) {
            for (int z : new int[]{zb, zf}) {
                b.fill(x, 0, z, x, top - 1, z, Blocks.SPRUCE_LOG.getDefaultState());
            }
        }
        for (int z : new int[]{zb, 4, zf}) {
            b.fill(x1, 0, z, x1, top - 1, z, Blocks.SPRUCE_LOG.getDefaultState());
            b.fill(x2, 0, z, x2, top - 1, z, Blocks.SPRUCE_LOG.getDefaultState());
        }
        ringBeam(b, x1, x2, zb, zf, top, Blocks.SPRUCE_LOG);
        window(b, 2, 1, zf, Direction.SOUTH);
        window(b, 8, 1, zf, Direction.SOUTH);
        for (int z : new int[]{2, 6}) {
            b.set(x1, 1, z, Blocks.GLASS_PANE.getDefaultState());
            b.set(x2, 1, z, Blocks.GLASS_PANE.getDefaultState());
        }
        for (int x : new int[]{3, 5, 7}) {
            b.set(x, 2, zb, Blocks.GLASS_PANE.getDefaultState());
        }
        b.door(5, 0, zf, Blocks.SPRUCE_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.set(5, 2, zf, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        b.set(4, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.set(6, 2, zf + 1, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        b.gableRoof(x1, x2, zb, zf, top, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
                Blocks.SPRUCE_PLANKS.getDefaultState());
        b.set(x1, 5, 4, Blocks.GLASS_PANE.getDefaultState());
        b.set(x2, 5, 4, Blocks.GLASS_PANE.getDefaultState());
        // 棟の王国旗
        b.set(5, 8, 4, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(5, 9, 4, Blocks.RED_BANNER.getDefaultState().with(BannerBlock.ROTATION, 8));
        // 室内：梁と照明
        b.fill(x1 + 1, top, 4, x2 - 1, top, 4, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        b.set(3, 2, 4, hangingLantern(Blocks.LANTERN));
        b.set(7, 2, 4, hangingLantern(Blocks.LANTERN));
        // 寝台列と私物箱
        for (int x : new int[]{2, 4, 6, 8}) {
            b.bed(x, 0, 2, Blocks.WHITE_BED, Direction.NORTH);
        }
        b.set(3, 0, 2, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        b.set(5, 0, 2, Blocks.BARREL.getDefaultState());
        b.set(7, 0, 2, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        // 作戦机
        b.set(5, 0, 4, Blocks.DARK_OAK_FENCE.getDefaultState());
        b.set(5, 1, 4, Blocks.RED_CARPET.getDefaultState());
        b.set(4, 0, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
        b.set(6, 0, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        // 武具置き場
        b.set(2, 0, 6, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        b.set(2, 0, 5, Blocks.BARREL.getDefaultState());
        b.set(8, 0, 6, Blocks.GRINDSTONE.getDefaultState()
                .with(GrindstoneBlock.FACE, BlockFace.FLOOR).with(GrindstoneBlock.FACING, Direction.WEST));
        b.set(8, 0, 5, Blocks.FLETCHING_TABLE.getDefaultState());
        b.set(5, 2, zf + 1, hangingLantern(Blocks.LANTERN));
        for (int z = zf + 1; z <= 10; z++) {
            b.set(5, -1, z, Blocks.DIRT_PATH.getDefaultState());
        }
        b.set(1, 0, 9, Blocks.TARGET.getDefaultState());
        b.set(9, 0, 9, Blocks.HAY_BLOCK.getDefaultState());
        return b.placements();
    }

    /** 鍛冶工房 11x9：木組みの工房と、溶鉱炉・溶岩の大釜・金床を備えた屋根付きの炉場。 */
    static List<StructurePlacer.Placement> blacksmith() {
        LocalBuilder b = new LocalBuilder();
        int x1 = 1, x2 = 5, zb = 1, zf = 5, top = 3;
        b.fill(x1, -1, zb, x2, -1, zf, Blocks.COBBLED_DEEPSLATE.getDefaultState());
        b.fill(x1 + 1, -1, zb + 1, x2 - 1, -1, zf - 1, Blocks.OAK_PLANKS.getDefaultState());
        b.fill(6, -1, 0, 9, -1, 6, Blocks.POLISHED_ANDESITE.getDefaultState());
        timberWalls(b, x1, x2, zb, zf, top, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS.getDefaultState(), 1);
        b.set(2, 1, zf, Blocks.GLASS_PANE.getDefaultState());
        b.set(2, 0, zf + 1, stairsTop(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        b.set(x1, 1, 3, Blocks.GLASS_PANE.getDefaultState());
        b.set(3, 1, zb, Blocks.GLASS_PANE.getDefaultState());
        b.door(3, 0, zf, Blocks.SPRUCE_DOOR, Direction.NORTH, DoorHinge.RIGHT);
        // 炉場への開口
        b.set(x2, 0, 3, Blocks.AIR.getDefaultState());
        b.set(x2, 1, 3, Blocks.AIR.getDefaultState());
        b.set(x2, 2, 3, stairsTop(Blocks.SPRUCE_STAIRS, Direction.WEST));
        b.gableRoof(x1, x2, zb, zf, top, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
                Blocks.SPRUCE_PLANKS.getDefaultState());
        // 炉場の柱と片流れ屋根
        for (int z : new int[]{1, 5}) {
            b.fill(9, 0, z, 9, 2, z, Blocks.STONE_BRICK_WALL.getDefaultState());
        }
        b.fill(7, 3, 0, 9, 3, 6, slab(Blocks.DEEPSLATE_BRICK_SLAB, SlabType.BOTTOM));
        b.fill(10, 3, 0, 10, 3, 6, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST));
        b.set(8, 2, 3, hangingLantern(Blocks.LANTERN));
        // 溶鉱炉とレンガの煙突
        b.set(7, 0, 1, Blocks.BLAST_FURNACE.getDefaultState().with(FurnaceBlock.FACING, Direction.SOUTH));
        b.fill(7, 1, 1, 7, 5, 1, Blocks.BRICKS.getDefaultState());
        b.set(7, 6, 1, Blocks.CAMPFIRE.getDefaultState());
        b.set(8, 0, 1, Blocks.LAVA_CAULDRON.getDefaultState());
        b.set(6, 0, 1, Blocks.COAL_BLOCK.getDefaultState());
        b.set(8, 0, 4, Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.EAST));
        b.set(9, 0, 3, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        b.set(6, 0, 5, Blocks.GRINDSTONE.getDefaultState()
                .with(GrindstoneBlock.FACE, BlockFace.FLOOR).with(GrindstoneBlock.FACING, Direction.SOUTH));
        // 工房の内装
        b.set(2, 0, 2, Blocks.SMITHING_TABLE.getDefaultState());
        b.set(3, 0, 2, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        b.set(4, 0, 2, Blocks.BARREL.getDefaultState());
        b.set(2, 0, 4, Blocks.STONECUTTER.getDefaultState());
        b.set(3, 2, 3, hangingLantern(Blocks.LANTERN));
        b.set(3, 2, zf + 1, hangingLantern(Blocks.LANTERN));
        b.set(3, -1, 6, Blocks.DIRT_PATH.getDefaultState());
        b.set(3, -1, 7, Blocks.DIRT_PATH.getDefaultState());
        b.set(3, -1, 8, Blocks.GRAVEL.getDefaultState());
        b.set(9, 0, 7, Blocks.BARREL.getDefaultState());
        b.set(8, 0, 7, Blocks.HAY_BLOCK.getDefaultState());
        return b.placements();
    }

    /** 柱（角＋spacing ごと）・下端の丸石帯・壁・上端の梁の木組み外壁。 */
    private static void timberWalls(LocalBuilder b, int x1, int x2, int zb, int zf, int top,
                                    net.minecraft.block.Block post, BlockState wall, int spacing) {
        for (int y = 0; y < top; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = zb; z <= zf; z++) {
                    boolean edgeX = x == x1 || x == x2;
                    boolean edgeZ = z == zb || z == zf;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    boolean isPost = (edgeX && edgeZ)
                            || (edgeZ && (x - x1) % spacing == 0 && spacing > 1 && x != x1 + (x2 - x1) / 2)
                            || (edgeX && (z - zb) % 4 == 0);
                    b.set(x, y, z, isPost ? post.getDefaultState() : y == 0 ? cobbleMix(x, y, z) : wall);
                }
            }
        }
        ringBeam(b, x1, x2, zb, zf, top, post);
    }

    private static void ringBeam(LocalBuilder b, int x1, int x2, int zb, int zf, int top,
                                 net.minecraft.block.Block log) {
        b.fill(x1, top, zb, x2, top, zb, log(log, Direction.Axis.X));
        b.fill(x1, top, zf, x2, top, zf, log(log, Direction.Axis.X));
        b.fill(x1, top, zb + 1, x1, top, zf - 1, log(log, Direction.Axis.Z));
        b.fill(x2, top, zb + 1, x2, top, zf - 1, log(log, Direction.Axis.Z));
    }

    /** 正面の窓：ガラス、外側に開いた雨戸、下に窓台。 */
    private static void window(LocalBuilder b, int x, int y, int z, Direction out) {
        b.set(x, y, z, Blocks.GLASS_PANE.getDefaultState());
        int ox = out.getOffsetX(), oz = out.getOffsetZ();
        Direction left = out.rotateYClockwise();
        b.set(x + left.getOffsetX() + ox, y, z + left.getOffsetZ() + oz, shutter(Blocks.SPRUCE_TRAPDOOR, out));
        b.set(x - left.getOffsetX() + ox, y, z - left.getOffsetZ() + oz, shutter(Blocks.SPRUCE_TRAPDOOR, out));
        b.set(x + ox, y - 1, z + oz, stairsTop(Blocks.SPRUCE_STAIRS, out.getOpposite()));
    }
}
