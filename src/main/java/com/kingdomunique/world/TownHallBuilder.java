package com.kingdomunique.world;

import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/** 幅11 x 奥行13 の中世ファンタジー風タウンホールを建築する。 */
public final class TownHallBuilder {
    private static final int WIDTH = 11;
    private static final int DEPTH = 13;

    private TownHallBuilder() {
    }

    /** 設計図のローカル配置リストへの書き込み先。 */
    @FunctionalInterface
    public interface BlockSink {
        void set(BlockPos pos, BlockState state, int flags);
    }

    /** 設計図用：建物全体（軒・玄関階段を含む 11x15）を (0,-1,0) 起点のローカル座標で出力する。 */
    public static void generate(BlockSink sink) {
        build(new BlockPos(0, 0, 1), sink);
    }

    static BlockPos findVillageOrigin(ServerWorld world, int centerX, int centerZ) {
        BlockPos plot = VillageTerrain.findPlot(world, centerX - 5, centerZ - 19,
                WIDTH, DEPTH + 3, 24, List.of(new BlockBox(centerX - 4, 0, centerZ - 4,
                        centerX + 4, 0, centerZ + 4)));
        return plot == null ? null : plot.south();
    }

    static BlockBox villageFootprint(BlockPos origin) {
        return VillageTerrain.footprint(origin.north(), WIDTH, DEPTH + 3);
    }

    private static void build(BlockPos origin, BlockSink world) {
        int width = WIDTH;
        int depth = DEPTH;
        int wallHeight = 5;

        // 床面（Y = -1）
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                boolean isBorder = (dx == 0 || dx == width - 1 || dz == 0 || dz == depth - 1);
                Block floorBlock = isBorder ? Blocks.CHISELED_STONE_BRICKS : Blocks.SPRUCE_PLANKS;
                world.set(origin.add(dx, -1, dz), floorBlock.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
            }
        }

        // 2. 外壁と柱の構築
        for (int dy = 0; dy < wallHeight; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    boolean isEdgeX = (dx == 0 || dx == width - 1);
                    boolean isEdgeZ = (dz == 0 || dz == depth - 1);
                    if (!isEdgeX && !isEdgeZ) {
                        continue;
                    }

                    BlockPos pos = origin.add(dx, dy, dz);

                    // 四隅と中間柱はオークの原木
                    boolean isPillar = (dx == 0 || dx == 5 || dx == width - 1)
                            && (dz == 0 || dz == 6 || dz == depth - 1);
                    if (isPillar) {
                        world.set(pos, Blocks.OAK_LOG.getDefaultState(),
                                Block.NOTIFY_LISTENERS);
                        continue;
                    }

                    // 腰壁（dy == 0）は石レンガ
                    if (dy == 0) {
                        world.set(pos, Blocks.STONE_BRICKS.getDefaultState(),
                                Block.NOTIFY_LISTENERS);
                    }
                    // 梁（dy == 4）は横向き原木
                    else if (dy == wallHeight - 1) {
                        Direction.Axis axis = isEdgeX ? Direction.Axis.Z : Direction.Axis.X;
                        world.set(pos, Blocks.OAK_LOG.getDefaultState()
                                        .with(PillarBlock.AXIS, axis),
                                Block.NOTIFY_LISTENERS);
                    }
                    // 窓の配置（dy == 1, 2）
                    else if ((dy == 1 || dy == 2)
                            && (dx == 2 || dx == 3 || dx == 7 || dx == 8 || dz == 3 || dz == 9)) {
                        world.set(pos, Blocks.GLASS_PANE.getDefaultState(),
                                Block.NOTIFY_LISTENERS);
                    }
                    // その他の壁は方解石（漆喰風の白壁）
                    else {
                        world.set(pos, Blocks.CALCITE.getDefaultState(),
                                Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 3. 正面玄関（南側 Z = depth - 1, 中央 X = 4..6）
        for (int dx = 4; dx <= 6; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                world.set(origin.add(dx, dy, depth - 1), Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
            }
            // 玄関ポーチ階段
            world.set(origin.add(dx, -1, depth), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                            .with(StairsBlock.FACING, Direction.NORTH),
                    Block.NOTIFY_LISTENERS);
        }
        // 玄関のアーチ装飾とランタン
        world.set(origin.add(4, 2, depth - 1), Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP),
                Block.NOTIFY_LISTENERS);
        world.set(origin.add(6, 2, depth - 1), Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.TOP),
                Block.NOTIFY_LISTENERS);
        world.set(origin.add(5, 3, depth), Blocks.OAK_LOG.getDefaultState()
                .with(PillarBlock.AXIS, Direction.Axis.Z), Block.NOTIFY_LISTENERS);
        world.set(origin.add(5, 2, depth), Blocks.LANTERN.getDefaultState()
                .with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);

        // 4. 切妻屋根（深層岩タイルの階段 ＋ 縁取りの石レンガ階段）
        int roofBaseY = wallHeight;
        for (int step = 0; step <= 5; step++) {
            int leftX = step;
            int rightX = width - 1 - step;
            int y = roofBaseY + step;

            for (int dz = -1; dz <= depth; dz++) {
                boolean isTrim = (dz == -1 || dz == depth);
                Block stairBlock = isTrim ? Blocks.STONE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;

                if (leftX < rightX) {
                    world.set(origin.add(leftX, y, dz), stairBlock.getDefaultState()
                            .with(StairsBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
                    world.set(origin.add(rightX, y, dz), stairBlock.getDefaultState()
                            .with(StairsBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
                } else if (leftX == rightX) {
                    // 棟（最上部）
                    Block slabBlock = isTrim ? Blocks.STONE_BRICK_SLAB : Blocks.DEEPSLATE_TILE_SLAB;
                    world.set(origin.add(leftX, y, dz), slabBlock.getDefaultState()
                            .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
                }
            }

            // 妻壁（前後面の三角形の壁埋め）
            for (int fillX = leftX + 1; fillX < rightX; fillX++) {
                world.set(origin.add(fillX, y, 0), Blocks.CALCITE.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
                world.set(origin.add(fillX, y, depth - 1), Blocks.CALCITE.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
            }
        }

        // 5. 屋根上の鐘楼（ベルタワー）
        for (int dy = 10; dy <= 12; dy++) {
            world.set(origin.add(4, dy, 2), Blocks.OAK_FENCE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            world.set(origin.add(6, dy, 2), Blocks.OAK_FENCE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            world.set(origin.add(4, dy, 4), Blocks.OAK_FENCE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            world.set(origin.add(6, dy, 4), Blocks.OAK_FENCE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
        }
        for (int dx = 4; dx <= 6; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                world.set(origin.add(dx, 13, dz),
                        Blocks.DEEPSLATE_TILE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        world.set(origin.add(5, 11, 3), Blocks.BELL.getDefaultState(),
                Block.NOTIFY_LISTENERS);

        // 6. 内装（会議テーブル・玉座風チェア・書見台・カーペット・チェスト）
        // レッドカーペットの通路
        for (int dz = 2; dz <= depth - 2; dz++) {
            world.set(origin.add(5, 0, dz), Blocks.RED_CARPET.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
        }
        // 奥の演壇・玉座エリア（Z = 2）
        world.set(origin.add(5, 0, 2), Blocks.DARK_OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        world.set(origin.add(4, 0, 2), Blocks.LECTERN.getDefaultState()
                .with(LecternBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        world.set(origin.add(6, 0, 2), Blocks.CARTOGRAPHY_TABLE.getDefaultState(),
                Block.NOTIFY_LISTENERS);

        // 保管チェストと樽
        world.set(origin.add(1, 0, 1), Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        world.set(origin.add(2, 0, 1), Blocks.BARREL.getDefaultState(),
                Block.NOTIFY_LISTENERS);
        world.set(origin.add(width - 2, 0, 1), Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);

        // 室内照明（天井から吊るすランタン）
        for (int z : new int[]{4, 8}) {
            for (int x = 3; x <= 7; x++) {
                world.set(origin.add(x, 7, z), Blocks.OAK_LOG.getDefaultState()
                        .with(PillarBlock.AXIS, Direction.Axis.X), Block.NOTIFY_LISTENERS);
            }
            world.set(origin.add(5, 6, z), Blocks.LANTERN.getDefaultState()
                    .with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
        }
    }
}
