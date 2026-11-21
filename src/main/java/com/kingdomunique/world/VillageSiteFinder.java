package com.kingdomunique.world;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
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
        for (BlockPos offset : nearbyOffsets(radius, step)) {
            BlockPos candidate = suitableCenter(world, x + offset.getX(), z + offset.getZ());
            if (candidate != null && accept.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static List<BlockPos> nearbyOffsets(int radius, int step) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int r = 0; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        return offsets;
    }
}
