package com.kingdomunique.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Gemini 提示の内政・戦争・英雄建築の構造生成（下から順に配置）。 */
public final class AdditionalStructures {

    private AdditionalStructures() {
    }

    public record BlockPlacement(BlockPos pos, BlockState state) {
    }

    /** ローカル配置リストを生成（回転処理からも利用する）。 */
    public static List<BlockPlacement> generatePlacements(BuildingType type, BlockPos origin) {
        List<BlockPlacement> list = new ArrayList<>();
        switch (type) {
            case GRANARY -> granary(origin.add(1, 0, 1), list);
            case INFIRMARY -> infirmary(origin, list);
            case MARKET -> market(origin, list);
            case ARSENAL -> arsenal(origin, list);
            case TRAINING_GROUND -> trainingGround(origin, list);
            case ROYAL_PALACE -> royalPalace(origin, list);
            default -> {
            }
        }
        list.sort(Comparator.comparingInt(p -> p.pos().getY()));
        return list;
    }

    public static void build(ServerWorld world, BuildingType type, BlockPos origin) {
        for (BlockPlacement p : generatePlacements(type, origin)) {
            world.setBlockState(p.pos(), p.state(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    private static void add(List<BlockPlacement> list, BlockPos pos, BlockState state) {
        list.add(new BlockPlacement(pos, state));
    }

    /** 王立穀物庫（本体 7x7、軒と入口階段を含め 9x9）。 */
    private static void granary(BlockPos o, List<BlockPlacement> l) {
        int w = 7, d = 7;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean pillar = (dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1);
                add(l, o.add(dx, -1, dz), (pillar ? Blocks.COBBLESTONE : Blocks.DIRT_PATH).getDefaultState());
                if (pillar) {
                    add(l, o.add(dx, 0, dz), Blocks.OAK_LOG.getDefaultState());
                }
                add(l, o.add(dx, 1, dz), Blocks.OAK_PLANKS.getDefaultState());
            }
        }
        add(l, o.add(3, 0, d), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
        add(l, o.add(3, 1, d - 1), Blocks.AIR.getDefaultState());
        for (int dy = 2; dy <= 4; dy++) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    boolean edgeX = dx == 0 || dx == w - 1;
                    boolean edgeZ = dz == 0 || dz == d - 1;
                    if (!edgeX && !edgeZ) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (dz == d - 1 && dx == 3 && (dy == 2 || dy == 3)) {
                        add(l, p, Blocks.AIR.getDefaultState());
                    } else if (edgeX && edgeZ) {
                        add(l, p, Blocks.OAK_LOG.getDefaultState());
                    } else {
                        add(l, p, Blocks.SPRUCE_PLANKS.getDefaultState());
                    }
                }
            }
        }
        for (int dx = -1; dx <= w; dx++) {
            for (int dz = -1; dz <= d; dz++) {
                add(l, o.add(dx, 5, dz), Blocks.OAK_SLAB.getDefaultState());
            }
        }
        add(l, o.add(1, 2, 1), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(1, 3, 1), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(2, 2, 1), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(5, 2, 1), Blocks.BARREL.getDefaultState());
        add(l, o.add(5, 3, 1), Blocks.BARREL.getDefaultState());
        add(l, o.add(5, 2, 2), Blocks.COMPOSTER.getDefaultState());
        add(l, o.add(3, 4, 3), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
    }

    /** 王立救護院 (9x7)。 */
    private static void infirmary(BlockPos o, List<BlockPlacement> l) {
        int w = 9, d = 7;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                add(l, o.add(dx, -1, dz), Blocks.SMOOTH_STONE.getDefaultState());
                boolean edgeX = dx == 0 || dx == w - 1;
                boolean edgeZ = dz == 0 || dz == d - 1;
                for (int dy = 0; dy < 3; dy++) {
                    if (!edgeX && !edgeZ) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (dz == d - 1 && dx == 4 && (dy == 0 || dy == 1)) {
                        add(l, p, Blocks.AIR.getDefaultState());
                    } else if (edgeX && edgeZ) {
                        add(l, p, Blocks.BIRCH_LOG.getDefaultState());
                    } else if (dy == 1 && (dx == 2 || dx == 6)) {
                        add(l, p, Blocks.GLASS_PANE.getDefaultState());
                    } else {
                        add(l, p, Blocks.CALCITE.getDefaultState());
                    }
                }
                add(l, o.add(dx, 3, dz), Blocks.QUARTZ_SLAB.getDefaultState());
            }
        }
        add(l, o.add(4, 4, d - 1), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(4, 5, d - 1), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(3, 5, d - 1), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(5, 5, d - 1), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(4, 6, d - 1), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(4, 0, 1), Blocks.BREWING_STAND.getDefaultState());
        add(l, o.add(3, 0, 1), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        add(l, o.add(1, 0, 2), Blocks.WHITE_WOOL.getDefaultState());
        add(l, o.add(1, 0, 3), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(7, 0, 2), Blocks.WHITE_WOOL.getDefaultState());
        add(l, o.add(7, 0, 3), Blocks.RED_WOOL.getDefaultState());
        add(l, o.add(4, 2, 3), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
    }

    /** 王都交易場 (9x7)。 */
    private static void market(BlockPos o, List<BlockPlacement> l) {
        int w = 9, d = 7;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                Block floor = (dx + dz) % 2 == 0 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
                add(l, o.add(dx, -1, dz), floor.getDefaultState());
            }
        }
        for (int x : new int[]{0, 4, 8}) {
            for (int z : new int[]{0, 6}) {
                for (int dy = 0; dy <= 3; dy++) {
                    add(l, o.add(x, dy, z), Blocks.OAK_FENCE.getDefaultState());
                }
            }
        }
        for (int dx = 0; dx < w; dx++) {
            Block wool = dx % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL;
            for (int dz = 0; dz < d; dz++) {
                int y = dz == 0 || dz == d - 1 ? 3 : 4;
                add(l, o.add(dx, y, dz), wool.getDefaultState());
            }
        }
        for (int dx = 1; dx <= 3; dx++) {
            add(l, o.add(dx, 0, 2), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        }
        for (int dx = 5; dx <= 7; dx++) {
            add(l, o.add(dx, 0, 2), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        }
        add(l, o.add(2, 1, 2), Blocks.EMERALD_BLOCK.getDefaultState());
        add(l, o.add(6, 1, 2), Blocks.LANTERN.getDefaultState());
        add(l, o.add(1, 0, 1), Blocks.BARREL.getDefaultState());
        add(l, o.add(7, 0, 1), Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.SOUTH));
    }

    /** 王立軍事庫 (9x7)。 */
    private static void arsenal(BlockPos o, List<BlockPlacement> l) {
        int w = 9, d = 7;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                add(l, o.add(dx, -1, dz), Blocks.POLISHED_ANDESITE.getDefaultState());
                boolean edgeX = dx == 0 || dx == w - 1;
                boolean edgeZ = dz == 0 || dz == d - 1;
                for (int dy = 0; dy <= 3; dy++) {
                    if (!edgeX && !edgeZ) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (dz == d - 1 && dx == 4 && (dy == 0 || dy == 1)) {
                        add(l, p, Blocks.AIR.getDefaultState());
                    } else if (edgeX && edgeZ) {
                        add(l, p, Blocks.DEEPSLATE_BRICKS.getDefaultState());
                    } else if (dy == 2 && (dx == 2 || dx == 6)) {
                        add(l, p, Blocks.IRON_BARS.getDefaultState());
                    } else {
                        add(l, p, Blocks.STONE_BRICKS.getDefaultState());
                    }
                }
                add(l, o.add(dx, 4, dz), Blocks.DEEPSLATE_TILE_SLAB.getDefaultState());
                if ((edgeX || edgeZ) && (dx + dz) % 2 == 0) {
                    add(l, o.add(dx, 5, dz), Blocks.STONE_BRICK_WALL.getDefaultState());
                }
            }
        }
        add(l, o.add(1, 0, 1), Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.SOUTH));
        add(l, o.add(2, 0, 1), Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.SOUTH));
        add(l, o.add(6, 0, 1), Blocks.FLETCHING_TABLE.getDefaultState());
        add(l, o.add(7, 0, 1), Blocks.SMITHING_TABLE.getDefaultState());
        add(l, o.add(4, 3, 3), Blocks.SOUL_LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
    }

    /** 英雄訓練場 (11x11)。 */
    private static void trainingGround(BlockPos o, List<BlockPlacement> l) {
        int size = 11;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                boolean border = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1;
                if (border) {
                    add(l, o.add(dx, -1, dz), Blocks.COBBLESTONE.getDefaultState());
                    if (!(dz == size - 1 && dx == 5)) {
                        add(l, o.add(dx, 0, dz), Blocks.OAK_FENCE.getDefaultState());
                    }
                    if ((dx == 0 || dx == size - 1) && (dz == 0 || dz == size - 1)) {
                        add(l, o.add(dx, 1, dz), Blocks.OAK_FENCE.getDefaultState());
                        add(l, o.add(dx, 2, dz), Blocks.LANTERN.getDefaultState());
                    }
                } else {
                    Block ground = (dx + dz) % 3 == 0 ? Blocks.COARSE_DIRT : Blocks.SAND;
                    add(l, o.add(dx, -1, dz), ground.getDefaultState());
                }
            }
        }
        add(l, o.add(3, 0, 1), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(3, 1, 1), Blocks.TARGET.getDefaultState());
        add(l, o.add(7, 0, 1), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(7, 1, 1), Blocks.TARGET.getDefaultState());
        add(l, o.add(5, 0, 4), Blocks.OAK_FENCE.getDefaultState());
        add(l, o.add(5, 1, 4), Blocks.HAY_BLOCK.getDefaultState());
        add(l, o.add(5, 2, 4), Blocks.CARVED_PUMPKIN.getDefaultState()
                .with(net.minecraft.block.CarvedPumpkinBlock.FACING, Direction.SOUTH));
    }

    /** 王宮 (13x15)。 */
    private static void royalPalace(BlockPos o, List<BlockPlacement> l) {
        int w = 13, d = 15, h = 6;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean goldTrim = (dx == 2 || dx == w - 3) && dz >= 2 && dz <= d - 3;
                add(l, o.add(dx, -1, dz), (goldTrim ? Blocks.GOLD_BLOCK : Blocks.POLISHED_DIORITE).getDefaultState());
                boolean edgeX = dx == 0 || dx == w - 1;
                boolean edgeZ = dz == 0 || dz == d - 1;
                for (int dy = 0; dy < h; dy++) {
                    if (!edgeX && !edgeZ) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (dz == d - 1 && dx >= 5 && dx <= 7 && dy <= 3) {
                        add(l, p, Blocks.AIR.getDefaultState());
                    } else if ((dx == 0 || dx == 4 || dx == 8 || dx == w - 1)
                            && (dz == 0 || dz == 7 || dz == d - 1)) {
                        add(l, p, Blocks.QUARTZ_PILLAR.getDefaultState());
                    } else if (dy >= 1 && dy <= 3 && (dx == 2 || dx == 10 || dz == 4 || dz == 10)) {
                        add(l, p, Blocks.YELLOW_STAINED_GLASS_PANE.getDefaultState());
                    } else {
                        add(l, p, Blocks.QUARTZ_BRICKS.getDefaultState());
                    }
                }
                Block roof = dx == 6 && (dz == 4 || dz == 8 || dz == 12)
                        ? Blocks.SEA_LANTERN : Blocks.QUARTZ_SLAB;
                add(l, o.add(dx, h, dz), roof.getDefaultState());
            }
        }
        for (int dz = 3; dz <= d - 2; dz++) {
            add(l, o.add(6, 0, dz), Blocks.RED_CARPET.getDefaultState());
        }
        for (int dx = 4; dx <= 8; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                add(l, o.add(dx, 0, dz), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
            }
        }
        add(l, o.add(6, 1, 2), Blocks.QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
        add(l, o.add(5, 1, 2), Blocks.GOLD_BLOCK.getDefaultState());
        add(l, o.add(7, 1, 2), Blocks.GOLD_BLOCK.getDefaultState());
    }
}
