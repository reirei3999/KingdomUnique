package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.data.WorldUniqueData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

    /** 毎ティック1候補ずつ村の用地を探し、チャンク読み込み後に生成する。 */
    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        VillageGenState state = VillageGenState.get(world);
        for (VillageGenState.VillageEntry entry : state.pendingVillages) {
            if (entry.built || world.getTime() < entry.retryAfter) {
                continue;
            }
            ChunkPos center = new ChunkPos(entry.x >> 4, entry.z >> 4);
            if (!isAreaLoaded(world, center, 4)) {
                continue;
            }
            tryBuildVillage(world, entry, 256);
            break;
        }
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

    static int tryBuildVillage(ServerWorld world, VillageGenState.VillageEntry entry, int searchRadius) {
        if (entry.built || world.getTime() < entry.retryAfter) {
            return 0;
        }
        int plannedX = entry.x;
        int plannedZ = entry.z;
        List<BlockPos> offsets = VillageSiteFinder.nearbyOffsets(searchRadius, 32);
        BlockPos offset = offsets.get(entry.searchCursor++);
        BlockPos center = VillageSiteFinder.suitableCenter(world,
                plannedX + offset.getX(), plannedZ + offset.getZ());
        int placed = center == null ? 0
                : VillageBuilder.generateVillage(world, center.getX(), center.getZ(), null).placedBuildings();
        if (placed == 0) {
            if (entry.searchCursor == offsets.size()) {
                entry.searchCursor = 0;
                entry.retryAfter = world.getTime() + 1200;
                KingdomUniqueMod.LOGGER.warn(
                        "[KingdomUnique] No suitable village site within {} blocks of ({}, {}); retrying in 60 seconds.",
                        searchRadius, plannedX, plannedZ);
            }
            return 0;
        }
        BlockPos plannedSurface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(plannedX, 0, plannedZ));
        String plannedBiome = world.getBiome(plannedSurface).getKey()
                .map(key -> key.getValue().toString()).orElse("unknown");
        entry.x = center.getX();
        entry.z = center.getZ();
        entry.built = true;
        VillageGenState.get(world).markDirty();
        WorldUniqueData unique = WorldUniqueData.getOrCreate(world);
        unique.setVillageSites(unique.getVillageSites().stream()
                .map(site -> site.x() == plannedX && site.z() == plannedZ
                        ? new WorldUniqueData.VillageSite(entry.x, entry.z) : site).toList());
        KingdomUniqueMod.LOGGER.info(
                "[KingdomUnique] Generated deferred village: planned ({}, {}) [{}], actual ({}, {}) [{}], {} buildings.",
                plannedX, plannedZ, plannedBiome, entry.x, entry.z,
                world.getBiome(center).getKey().map(key -> key.getValue().toString()).orElse("unknown"), placed);
        return placed;
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
