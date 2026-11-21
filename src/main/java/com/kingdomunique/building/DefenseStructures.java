package com.kingdomunique.building;

import net.minecraft.block.BellBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;

import java.util.List;

import static com.kingdomunique.building.LocalBuilder.*;

/** 監視塔・門・防壁の設計（正面 = +Z が外敵側、サバイバル入手可能ブロックのみ）。 */
final class DefenseStructures {

    private DefenseStructures() {
    }

    /** 監視塔 5x5：すそ広がりの石の塔身、はしごで登る張り出した見張り台、警鐘と寄棟の屋根。 */
    static List<StructurePlacer.Placement> watchtower() {
        LocalBuilder b = new LocalBuilder();
        b.fill(0, -1, 0, 4, -1, 4, Blocks.COBBLESTONE.getDefaultState());
        // すそ：塔身の周りに内向きの階段
        for (int i = 1; i <= 3; i++) {
            b.set(i, 0, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            if (i != 2) {
                b.set(i, 0, 4, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            }
            b.set(0, 0, i, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
            b.set(4, 0, i, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        }
        // 塔身 3x3（中は1マスのはしご穴）
        for (int y = 0; y <= 7; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    if (x == 2 && z == 2) {
                        continue;
                    }
                    boolean corner = (x != 2) && (z != 2);
                    b.set(x, y, z, y <= 1 ? cobbleMix(x, y, z)
                            : corner && y >= 6 ? Blocks.POLISHED_ANDESITE.getDefaultState()
                            : stoneMix(x, y, z));
                }
            }
        }
        b.door(2, 0, 3, Blocks.SPRUCE_DOOR, Direction.NORTH, DoorHinge.LEFT);
        b.set(2, 2, 3, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        for (int y = 0; y <= 8; y++) {
            b.set(2, y, 2, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
        }
        // 矢狭間
        b.set(1, 4, 2, Blocks.IRON_BARS.getDefaultState());
        b.set(3, 4, 2, Blocks.IRON_BARS.getDefaultState());
        b.set(2, 5, 3, Blocks.IRON_BARS.getDefaultState());
        b.set(2, 6, 4, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        // 張り出しを支える持ち送り
        for (int i = 1; i <= 3; i++) {
            b.set(i, 7, 0, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            b.set(i, 7, 4, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            b.set(0, 7, i, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
            b.set(4, 7, i, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        }
        // 見張り台
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 2 && z == 2) {
                    continue;
                }
                b.set(x, 8, z, Blocks.SPRUCE_PLANKS.getDefaultState());
                boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                if (corner) {
                    b.fill(x, 9, z, x, 10, z, Blocks.SPRUCE_LOG.getDefaultState());
                } else if (edge) {
                    b.set(x, 9, z, Blocks.SPRUCE_FENCE.getDefaultState());
                }
            }
        }
        // 屋根（軒の段 → 中段 → 頂）
        for (int i = 0; i <= 4; i++) {
            b.set(i, 11, 0, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
            b.set(i, 11, 4, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
        }
        for (int i = 1; i <= 3; i++) {
            b.set(0, 11, i, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
            b.set(4, 11, i, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
        }
        b.fill(1, 11, 1, 3, 11, 3, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int i = 1; i <= 3; i++) {
            b.set(i, 12, 1, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
            b.set(i, 12, 3, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
        }
        b.set(1, 12, 2, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
        b.set(3, 12, 2, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
        b.set(2, 12, 2, slab(Blocks.DEEPSLATE_TILE_SLAB, SlabType.BOTTOM));
        b.set(2, 10, 2, Blocks.BELL.getDefaultState().with(BellBlock.ATTACHMENT, Attachment.CEILING)
                .with(BellBlock.FACING, Direction.SOUTH));
        b.set(1, 10, 1, hangingLantern(Blocks.LANTERN));
        b.set(3, 10, 3, hangingLantern(Blocks.LANTERN));
        return b.placements();
    }

    /** 門 9x5：左右の塔とアーチの通路、上に歩廊。中央の落とし格子（鉄格子）を DefenseSystem が開閉する。 */
    static List<StructurePlacer.Placement> gate() {
        LocalBuilder b = new LocalBuilder();
        int lo = StructurePlacer.GATE_LEAF_MIN_X;
        int hi = StructurePlacer.GATE_LEAF_MAX_X;
        b.fill(0, -1, 0, 8, -1, 4, Blocks.COBBLESTONE.getDefaultState());
        b.fill(lo, -1, 0, hi, -1, 4, Blocks.STONE_BRICKS.getDefaultState());
        // 左右の塔（3x3、通路側は z1..3）
        for (int tx : new int[]{0, hi + 1}) {
            for (int y = 0; y <= 5; y++) {
                for (int x = tx; x <= tx + 2; x++) {
                    for (int z = 1; z <= 3; z++) {
                        b.set(x, y, z, y <= 1 ? cobbleMix(x, y, z) : stoneMix(x, y, z));
                    }
                }
            }
            // 胸壁
            for (int x = tx; x <= tx + 2; x++) {
                for (int z = 1; z <= 3; z++) {
                    boolean corner = (x != tx + 1) && (z != 2);
                    boolean edge = x != tx + 1 || z != 2;
                    if (corner) {
                        b.set(x, 6, z, Blocks.STONE_BRICKS.getDefaultState());
                        b.set(x, 7, z, slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
                    } else if (edge) {
                        b.set(x, 6, z, Blocks.STONE_BRICK_WALL.getDefaultState());
                    }
                }
            }
            // 塔のすそと矢狭間
            b.fill(tx, 0, 0, tx + 2, 0, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            b.fill(tx, 0, 4, tx + 2, 0, 4, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
            b.set(tx + 1, 3, 3, Blocks.IRON_BARS.getDefaultState());
            b.set(tx + 1, 3, 1, Blocks.IRON_BARS.getDefaultState());
            b.set(tx + 1, 4, 4, Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH));
        }
        // アーチと歩廊
        for (int z = 1; z <= 3; z++) {
            b.set(lo, 3, z, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
            b.set(hi, 3, z, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
            b.set(lo + 1, 3, z, z == 2 ? stoneMix(lo + 1, 3, z) : Blocks.CHISELED_STONE_BRICKS.getDefaultState());
            for (int x = lo; x <= hi; x++) {
                b.set(x, 4, z, stoneMix(x, 4, z));
            }
        }
        for (int x = lo; x <= hi; x++) {
            b.set(x, 5, 1, Blocks.STONE_BRICK_WALL.getDefaultState());
            b.set(x, 5, 3, x == lo + 1 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICK_WALL.getDefaultState());
        }
        b.set(lo + 1, 6, 3, Blocks.RED_BANNER.getDefaultState());
        // 通路の照明と落とし格子
        b.set(lo + 1, 2, 1, hangingLantern(Blocks.LANTERN));
        b.set(lo + 1, 2, 3, hangingLantern(Blocks.LANTERN));
        for (int x = lo; x <= hi; x++) {
            for (int y = 0; y < StructurePlacer.GATE_LEAF_HEIGHT; y++) {
                b.set(x, y, StructurePlacer.GATE_LEAF_Z, StructurePlacer.GATE_LEAF_CLOSED);
            }
        }
        return b.placements();
    }

    /** 防壁 7x3：外側（+Z）の胸壁と狭間、内側の歩廊と控え壁。隣に並べると一続きの城壁になる。 */
    static List<StructurePlacer.Placement> wall() {
        LocalBuilder b = new LocalBuilder();
        b.fill(0, -1, 0, 6, -1, 2, Blocks.COBBLESTONE.getDefaultState());
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                b.set(x, y, 2, y <= 1 ? cobbleMix(x, y, 2) : stoneMix(x, y, 2));
            }
            for (int y = 0; y <= 2; y++) {
                b.set(x, y, 1, stoneMix(x, y, 1));
            }
            b.set(x, 3, 1, Blocks.STONE_BRICKS.getDefaultState());
            b.set(x, 3, 0, slab(Blocks.STONE_BRICK_SLAB, SlabType.TOP));
            if (x % 2 == 1) {
                b.set(x, 5, 2, Blocks.STONE_BRICKS.getDefaultState());
            }
        }
        // 控え壁と、その間の持ち送り
        for (int x = 0; x <= 6; x++) {
            if (x == 0 || x == 3 || x == 6) {
                b.fill(x, 0, 0, x, 2, 0, Blocks.STONE_BRICKS.getDefaultState());
                b.set(x, 0, 0, Blocks.COBBLESTONE.getDefaultState());
            } else {
                b.set(x, 2, 0, stairsTop(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
            }
        }
        b.set(3, 4, 2, Blocks.IRON_BARS.getDefaultState());
        b.set(3, 2, 2, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        b.set(3, 4, 0, Blocks.LANTERN.getDefaultState());
        return b.placements();
    }
}
