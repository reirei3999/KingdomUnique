package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.data.WorldUniqueData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * オーバーワールド初期化：ワールドボーダー設定、村の配置計画、
 * および「チャンクが読み込まれた時点で安全に村を生成」する遅延処理。
 */
public final class WorldInitializer {

    public static final double WORLD_SIZE = 5000.0;
    public static final int VILLAGE_COUNT = 10;
    private static final int MIN_DISTANCE = 800;
    private static final long SITE_SEED_XOR = 0x4B494E47_444F4DL;

    private WorldInitializer() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(WorldInitializer::onWorldTick);
    }

    /** ServerWorldEvents.LOAD から呼ばれる初期化エントリ。 */
    public static void initialize(ServerWorld world) {
        VillageGenState state = VillageGenState.get(world);
        if (state.initialized) {
            KingdomUniqueMod.LOGGER.info("[KingdomUnique] Village plan already exists. Skipping setup.");
            configureWorldBorder(world);
            return;
        }

        configureWorldBorder(world);

        List<BlockPos> planned = planVillagePositions(world.getSeed());
        List<WorldUniqueData.VillageSite> sites = new ArrayList<>();
        for (BlockPos pos : planned) {
            state.pendingVillages.add(new VillageGenState.VillageEntry(pos.getX(), pos.getZ(), false));
            sites.add(new WorldUniqueData.VillageSite(pos.getX(), pos.getZ()));
        }
        state.initialized = true;
        state.markDirty();

        // 他システム（トレジャー配布・人口調整・保護）互換のためのサイト一覧も保存する。
        WorldUniqueData unique = WorldUniqueData.getOrCreate(world);
        unique.setVillageSites(sites);
        unique.setInitialized(true);
        unique.markDirty();

        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Village plan created: {} sites (built on chunk load).",
                sites.size());
    }

    /** ボーダーを中心(0,0)・5000x5000に設定する。 */
    public static void configureWorldBorder(ServerWorld world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(WORLD_SIZE);
    }

    /** 毎ティック（1秒ごと）未生成の村をチェックし、チャンク読み込み後に生成する。 */
    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        if (world.getTime() % 20L != 0L) {
            return;
        }
        VillageGenState state = VillageGenState.get(world);
        boolean updated = false;
        for (VillageGenState.VillageEntry entry : state.pendingVillages) {
            if (entry.built) {
                continue;
            }
            ChunkPos center = new ChunkPos(entry.x >> 4, entry.z >> 4);
            if (!isAreaLoaded(world, center, 4)) {
                continue;
            }
            int surfaceY = findSafeSurfaceY(world, entry.x, entry.z);
            VillageBuilder.build(world, entry.x, entry.z);
            entry.built = true;
            updated = true;
            KingdomUniqueMod.LOGGER.info("[KingdomUnique] Generated deferred village at ({}, {}).",
                    entry.x, entry.z);
        }
        if (updated) {
            state.markDirty();
        }
        com.kingdomunique.item.unique.UniqueWeaponManager.distributeToVillages(world);
    }

    /** 周辺9x9チャンクが読み込まれているか（村の端が途切れないように）。 */
    private static boolean isAreaLoaded(ServerWorld world, ChunkPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded(center.x + dx, center.z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 海・マグマを避けた安全な地表Yを返す。 */
    private static int findSafeSurfaceY(ServerWorld world, int centerX, int centerZ) {
        for (int r = 0; r <= 32; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (world.getBiome(new BlockPos(x, 0, z)).isIn(BiomeTags.IS_OCEAN)) {
                        continue;
                    }
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos ground = new BlockPos(x, y - 1, z);
                    if (!world.getBlockState(ground).isOf(Blocks.WATER)
                            && !world.getBlockState(ground).isOf(Blocks.LAVA)
                            && world.getFluidState(ground).isEmpty()) {
                        return y;
                    }
                }
            }
        }
        return world.getSeaLevel() + 1;
    }

    /** 4x3=12グリッドから、互いに800マス以上離れた10箇所を選ぶ。 */
    private static List<BlockPos> planVillagePositions(long seed) {
        Random random = new Random(seed ^ SITE_SEED_XOR);
        int[] xs = {-1800, -600, 600, 1800};
        int[] zs = {-1600, 0, 1600};

        List<BlockPos> candidates = new ArrayList<>();
        for (int gx : xs) {
            for (int gz : zs) {
                candidates.add(new BlockPos(
                        gx + random.nextInt(401) - 200, 64, gz + random.nextInt(401) - 200));
            }
        }
        Collections.shuffle(candidates, random);

        List<BlockPos> selected = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (selected.size() >= VILLAGE_COUNT) {
                break;
            }
            boolean tooClose = false;
            for (BlockPos existing : selected) {
                if (candidate.getSquaredDistance(existing) < (double) MIN_DISTANCE * MIN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                selected.add(candidate);
            }
        }
        return selected;
    }
}
