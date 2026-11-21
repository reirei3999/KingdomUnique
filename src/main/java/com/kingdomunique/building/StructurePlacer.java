package com.kingdomunique.building;

import com.kingdomunique.world.HeroSanctuaryBuilder;
import com.kingdomunique.world.TownHallBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 完了時に設計図の構造物を配置する。
 * すべての建物は origin を北西角とするプレビュー枠
 * ({@link StructureRotationUtil#computeBoundingBox}) の内側にだけ建ち、正面を facing に向ける。
 */
public final class StructurePlacer {

    /** 門扉（閉じているときのブロック）のローカル位置：x=3..5, y=0..2, z=2。 */
    public static final int GATE_LEAF_MIN_X = 3;
    public static final int GATE_LEAF_MAX_X = 5;
    public static final int GATE_LEAF_HEIGHT = 3;
    public static final int GATE_LEAF_Z = 2;

    private static final int SUPPORT_DEPTH = 8;

    private StructurePlacer() {
    }

    /** ローカル座標（正面 = +Z）でのブロック配置。 */
    public record Placement(BlockPos local, BlockState state) {
    }

    public static void place(ServerWorld world, BlockPos origin, BuildingType type) {
        place(world, origin, type, Direction.SOUTH);
    }

    /** 建物の正面を facing に向けて配置する。 */
    public static void place(ServerWorld world, BlockPos origin, BuildingType type, Direction facing) {
        BlockBox box = StructureRotationUtil.computeBoundingBox(origin, type, facing);
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }
        prepareSite(world, box);
        int[] d = StructureRotationUtil.getBuildingDimensions(type);
        for (Placement p : localPlacements(type)) {
            world.setBlockState(
                    StructureRotationUtil.transformPos(origin, p.local().getX(), p.local().getY(),
                            p.local().getZ(), d[0], d[2], facing),
                    StructureRotationUtil.transformState(p.state(), facing),
                    Block.NOTIFY_LISTENERS);
        }
    }

    /** 建物のローカル配置（下の段から順、同じ段では後から追加したものが優先）。 */
    public static List<Placement> localPlacements(BuildingType type) {
        List<Placement> list = new ArrayList<>();
        switch (type) {
            case HOUSE -> list.addAll(HomeStructures.house());
            case BARRACKS -> list.addAll(HomeStructures.barracks());
            case BLACKSMITH -> list.addAll(HomeStructures.blacksmith());
            case TOWN_HALL -> TownHallBuilder.generate((pos, state, flags) -> list.add(new Placement(pos, state)));
            case WATCHTOWER -> watchtower(list);
            case GATE -> gate(list);
            case WALL -> wall(list);
            case HERO_SANCTUARY -> {
                for (var p : HeroSanctuaryBuilder.generatePlacements(BlockPos.ORIGIN)) {
                    list.add(new Placement(p.pos(), p.state()));
                }
            }
            case GRANARY, INFIRMARY, MARKET, ARSENAL, TRAINING_GROUND, ROYAL_PALACE -> {
                for (var p : AdditionalStructures.generatePlacements(type, BlockPos.ORIGIN)) {
                    list.add(new Placement(p.pos(), p.state()));
                }
            }
        }
        list.sort(Comparator.comparingInt(p -> p.local().getY()));
        return list;
    }

    /** 閉じた門扉のワールド座標。 */
    public static List<BlockPos> gateLeafPositions(BlockPos origin, Direction facing) {
        int[] d = StructureRotationUtil.getBuildingDimensions(BuildingType.GATE);
        List<BlockPos> leaves = new ArrayList<>();
        for (int x = GATE_LEAF_MIN_X; x <= GATE_LEAF_MAX_X; x++) {
            for (int y = 0; y < GATE_LEAF_HEIGHT; y++) {
                leaves.add(StructureRotationUtil.transformPos(origin, x, y, GATE_LEAF_Z, d[0], d[2], facing));
            }
        }
        return leaves;
    }

    /**
     * 枠内の地上部分を空にし、床の段（box.minY）とその下の空洞・水を土と丸石で埋める。
     * 枠の外には一切手を付けない（下方向の支えのみ枠の真下に入れる）。
     */
    private static void prepareSite(ServerWorld world, BlockBox box) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                for (int y = box.getMaxY(); y > box.getMinY(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        // 枠外の隣接ブロック（ベッドの片側など）を連鎖で壊さないよう形状更新を止める。
                        world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                    }
                }
                for (int y = box.getMinY(); y > box.getMinY() - SUPPORT_DEPTH; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isReplaceable()) {
                        break;
                    }
                    world.setBlockState(pos, (y == box.getMinY() ? Blocks.DIRT : Blocks.COBBLESTONE)
                            .getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void put(List<Placement> list, int x, int y, int z, BlockState state) {
        list.add(new Placement(new BlockPos(x, y, z), state));
    }

    /** 国境監視塔（5x5 枠の中央 3x3）：石レンガの柱と木の見張り台。 */
    private static void watchtower(List<Placement> l) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                put(l, x, -1, z, Blocks.COBBLESTONE.getDefaultState());
                put(l, x, 7, z, Blocks.OAK_PLANKS.getDefaultState());
                put(l, x, 9, z, Blocks.STONE_BRICKS.getDefaultState());
                boolean corner = (x == 1 || x == 3) && (z == 1 || z == 3);
                if (corner) {
                    for (int y = 0; y <= 6; y++) {
                        put(l, x, y, z, Blocks.STONE_BRICKS.getDefaultState());
                    }
                }
                if (x != 2 || z != 2) {
                    put(l, x, 8, z, Blocks.OAK_PLANKS.getDefaultState());
                }
            }
        }
        put(l, 2, 8, 2, Blocks.LANTERN.getDefaultState());
    }

    /** 国の門（9x5 枠の中央の列）：中央の門扉は DefenseSystem が開閉する。 */
    private static void gate(List<Placement> l) {
        int z = GATE_LEAF_Z;
        for (int x = GATE_LEAF_MIN_X - 1; x <= GATE_LEAF_MAX_X + 1; x++) {
            put(l, x, -1, z, Blocks.COBBLESTONE.getDefaultState());
            put(l, x, 4, z, Blocks.STONE_BRICKS.getDefaultState());
        }
        for (int y = 0; y <= 3; y++) {
            put(l, GATE_LEAF_MIN_X - 1, y, z, Blocks.STONE_BRICKS.getDefaultState());
            put(l, GATE_LEAF_MAX_X + 1, y, z, Blocks.STONE_BRICKS.getDefaultState());
        }
        for (int x = GATE_LEAF_MIN_X; x <= GATE_LEAF_MAX_X; x++) {
            for (int y = 0; y < GATE_LEAF_HEIGHT; y++) {
                put(l, x, y, z, Blocks.IRON_BLOCK.getDefaultState());
            }
        }
    }

    /** 防壁（7x3 枠の中央の列）：幅5・高さ5の石レンガモジュール。 */
    private static void wall(List<Placement> l) {
        for (int x = 1; x <= 5; x++) {
            put(l, x, -1, 1, Blocks.COBBLESTONE.getDefaultState());
            for (int y = 0; y <= 4; y++) {
                put(l, x, y, 1, Blocks.STONE_BRICKS.getDefaultState());
            }
            if (x % 2 == 1) {
                put(l, x, 5, 1, Blocks.STONE_BRICK_WALL.getDefaultState());
            }
        }
    }
}
