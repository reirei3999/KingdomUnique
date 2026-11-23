package com.kingdomunique.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.List;

final class VillageTerrain {
    private VillageTerrain() {
    }

    static BlockPos findPlot(ServerWorld world, int x, int z, int width, int depth, int radius,
                             List<BlockBox> occupied) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int minX = x + dx;
                    int minZ = z + dz;
                    if (occupied.stream().anyMatch(box -> box.intersectsXZ(
                            minX, minZ, minX + width - 1, minZ + depth - 1))) {
                        continue;
                    }
                    BlockPos plot = plot(world, minX, minZ, width, depth);
                    if (plot != null) {
                        return plot;
                    }
                }
            }
        }
        return null;
    }

    static void clearPlants(ServerWorld world, BlockPos origin, int width, int height, int depth) {
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (world.getBlockState(pos).getBlock() instanceof PlantBlock) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                    }
                }
            }
        }
    }

    static boolean obstructed(ServerWorld world, AnimalEntity animal) {
        Box box = animal.getBoundingBox();
        return world.getBlockCollisions(animal, box).iterator().hasNext() || world.containsFluid(box);
    }

    static void freeObstructedAnimals(ServerWorld world, List<AnimalEntity> animals) {
        for (AnimalEntity animal : animals) {
            if (!obstructed(world, animal)) {
                continue;
            }
            BlockPos feet = safeAnimalPosition(world, animal, false);
            if (feet == null) {
                feet = safeAnimalPosition(world, animal, true);
            }
            if (feet != null) {
                animal.requestTeleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            }
        }
    }

    private static BlockPos safeAnimalPosition(ServerWorld world, AnimalEntity animal, boolean surface) {
        BlockPos start = animal.getBlockPos();
        for (int r = 0; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = start.getX() + dx;
                    int z = start.getZ() + dz;
                    int y = surface ? world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : start.getY();
                    BlockPos pos = new BlockPos(x, y, z);
                    Box box = animal.getBoundingBox().offset(
                            x + 0.5 - animal.getX(), y - animal.getY(), z + 0.5 - animal.getZ());
                    BlockPos below = pos.down();
                    if (world.getWorldBorder().contains(box)
                            && world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)
                            && world.isSpaceEmpty(animal, box) && !world.containsFluid(box)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    static BlockBox footprint(BlockPos plot, int width, int depth) {
        return new BlockBox(plot.getX(), plot.getY(), plot.getZ(),
                plot.getX() + width - 1, plot.getY(), plot.getZ() + depth - 1);
    }

    static BlockPos plot(ServerWorld world, int x, int z, int width, int depth) {
        for (int cx = x >> 4; cx <= (x + width - 1) >> 4; cx++) {
            for (int cz = z >> 4; cz <= (z + depth - 1) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunk(cx, cz);
                }
            }
        }
        int minY = world.getTopY();
        int maxY = world.getBottomY();
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                if (y <= world.getBottomY() || y >= world.getTopY() - 1) {
                    return null;
                }
                BlockPos ground = new BlockPos(x + dx, y - 1, z + dz);
                BlockState state = world.getBlockState(ground);
                if (!state.getFluidState().isEmpty() || state.isIn(BlockTags.LOGS)
                        || !state.isSideSolidFullSquare(world, ground, Direction.UP)
                        || !world.getBlockState(ground.up()).isReplaceable()
                        || !world.getBlockState(ground.up(2)).isReplaceable()) {
                    return null;
                }
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (maxY - minY > 1) {
                    return null;
                }
            }
        }
        return new BlockPos(x, maxY, z);
    }
}
