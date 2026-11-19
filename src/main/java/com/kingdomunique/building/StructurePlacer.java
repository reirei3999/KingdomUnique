package com.kingdomunique.building;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** 完了時に設計図ブロックを中心として立体構造物を配置する。 */
public final class StructurePlacer {

    private StructurePlacer() {
    }

    public static void place(ServerWorld world, BlockPos center, BuildingType type) {
        clear(world, center, radius(type));
        switch (type) {
            case HOUSE -> house(world, center);
            case BARRACKS -> barracks(world, center);
            case BLACKSMITH -> blacksmith(world, center);
            case TOWN_HALL -> townHall(world, center);
        }
    }

    private static int radius(BuildingType type) {
        return switch (type) {
            case HOUSE -> 4;
            case BARRACKS -> 6;
            case BLACKSMITH -> 5;
            case TOWN_HALL -> 8;
        };
    }

    private static void clear(ServerWorld world, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = 0; y < 14; y++) {
                    BlockPos pos = center.add(dx, y, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    private static void wallBox(ServerWorld world, BlockPos origin, int radiusX, int radiusZ,
                                int height, net.minecraft.block.BlockState wall,
                                net.minecraft.block.BlockState floor) {
        for (int y = -1; y <= height; y++) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    boolean edge = Math.abs(dx) == radiusX || Math.abs(dz) == radiusZ;
                    if (y == -1) {
                        world.setBlockState(origin.add(dx, y, dz), floor);
                    } else if (y == height) {
                        world.setBlockState(origin.add(dx, y, dz), wall);
                    } else if (edge) {
                        boolean doorway = y <= 1 && dx == 0 && dz == radiusZ;
                        world.setBlockState(origin.add(dx, y, dz),
                                doorway ? Blocks.AIR.getDefaultState() : wall);
                    }
                }
            }
        }
    }

    private static void house(ServerWorld world, BlockPos center) {
        wallBox(world, center, 3, 3, 3, Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        world.setBlockState(center.add(0, 3, 0), Blocks.OAK_STAIRS.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(center.add(dx, 4, dz), Blocks.OAK_STAIRS.getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 1, 0), Blocks.BELL.getDefaultState());
    }

    private static void barracks(ServerWorld world, BlockPos center) {
        wallBox(world, center, 5, 5, 4, Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.setBlockState(center.add(dx, 5, dz), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        for (int x = -3; x <= 3; x += 3) {
            world.setBlockState(center.add(x, 1, 0), Blocks.CHEST.getDefaultState());
        }
    }

    private static void blacksmith(ServerWorld world, BlockPos center) {
        wallBox(world, center, 4, 4, 3, Blocks.BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        world.setBlockState(center.add(0, 1, 0), Blocks.FURNACE.getDefaultState());
        world.setBlockState(center.add(2, 1, 0), Blocks.ANVIL.getDefaultState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.setBlockState(center.add(dx, 4, dz), Blocks.COBBLESTONE.getDefaultState());
            }
        }
    }

    private static void townHall(ServerWorld world, BlockPos center) {
        wallBox(world, center, 7, 7, 5, Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                world.setBlockState(center.add(dx, 6, dz), Blocks.GOLD_BLOCK.getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 7, 0), Blocks.BEACON.getDefaultState());
        for (int x = -5; x <= 5; x += 5) {
            for (int z = -5; z <= 5; z += 5) {
                world.setBlockState(center.add(x, 6, z), Blocks.LANTERN.getDefaultState());
            }
        }
    }
}
