package com.kingdomunique.world;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

final class VillageSiteFinder {
    private VillageSiteFinder() {
    }

    static BlockPos suitableCenter(ServerWorld world, int x, int z) {
        BlockPos surface = VillageTerrain.plot(world, x, z, 1, 1);
        if (surface == null || surface.getY() < world.getSeaLevel()
                || world.getBlockState(surface.down()).isOf(Blocks.MAGMA_BLOCK)) {
            return null;
        }
        var biome = world.getBiome(surface);
        if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_DEEP_OCEAN)
                || biome.isIn(BiomeTags.IS_RIVER)
                || TownHallBuilder.findVillageOrigin(world, x, z) == null) {
            return null;
        }
        return surface;
    }

    static BlockPos findNearby(ServerWorld world, int x, int z, int radius, int step,
                               Predicate<BlockPos> accept) {
        for (int r = 0; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos candidate = suitableCenter(world, x + dx, z + dz);
                    if (candidate != null && accept.test(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}
