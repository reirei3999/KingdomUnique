package com.kingdomunique.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
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
