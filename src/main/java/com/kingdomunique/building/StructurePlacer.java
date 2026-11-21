package com.kingdomunique.building;

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
    /** 閉じた門の落とし格子。 */
    public static final BlockState GATE_LEAF_CLOSED = Blocks.IRON_BARS.getDefaultState();

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
        List<BlockPos> placed = new ArrayList<>();
        for (Placement p : localPlacements(type)) {
            BlockPos pos = StructureRotationUtil.transformPos(origin, p.local().getX(), p.local().getY(),
                    p.local().getZ(), d[0], d[2], facing);
            // 扉の両半分などが揃うまでは、隣接ブロックの形状を更新しない。
            world.setBlockState(pos, StructureRotationUtil.transformState(p.state(), facing),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            placed.add(pos);
        }
        // 板ガラス・フェンス・階段などを、後から置かれた隣のブロックに合わせてつなぐ。
        for (BlockPos pos : placed) {
            BlockState state = world.getBlockState(pos);
            BlockState connected = Block.postProcessState(state, world, pos);
            if (connected != state && !connected.isAir()) {
                world.setBlockState(pos, connected, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
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
            case WATCHTOWER -> list.addAll(DefenseStructures.watchtower());
            case GATE -> list.addAll(DefenseStructures.gate());
            case WALL -> list.addAll(DefenseStructures.wall());
            case HERO_SANCTUARY -> list.addAll(GrandStructures.heroSanctuary());
            case GRANARY -> list.addAll(CivicStructures.granary());
            case INFIRMARY -> list.addAll(CivicStructures.infirmary());
            case MARKET -> list.addAll(CivicStructures.market());
            case ARSENAL -> list.addAll(CivicStructures.arsenal());
            case TRAINING_GROUND -> list.addAll(CivicStructures.trainingGround());
            case ROYAL_PALACE -> list.addAll(GrandStructures.royalPalace());
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
}
