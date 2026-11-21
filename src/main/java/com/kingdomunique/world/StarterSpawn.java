package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.kingdom.KingdomData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 初回ログイン時の 4000x4000 ランダムスポーンと初期村生成。 */
public final class StarterSpawn {

    private static final int RANGE = 2000;
    private static final int MIN_DISTANCE = 500;
    private static final int MAX_ATTEMPTS = 256;
    private static final int SPAWN_SEARCH_RADIUS = 8;

    /** 次ティックで処理するプレイヤー（JOIN 契機に加え、毎ティックの検出でも積む）。 */
    private static final Queue<UUID> PENDING = new ArrayDeque<>();

    private StarterSpawn() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> restoreVillageChunks(server.getOverworld()));
        ServerTickEvents.END_SERVER_TICK.register(StarterSpawn::onServerTick);
    }

    private static void restoreVillageChunks(ServerWorld world) {
        PlayerSpawnData spawns = PlayerSpawnData.getOrCreate(world);
        KingdomData.getOrCreate(world).all().forEach((owner, info) -> {
            if (info.villageGenerated()) {
                BlockPos spawn = spawns.get(owner);
                keepVillageLoaded(world, spawn != null ? spawn : new BlockPos(info.centerX(), 0, info.centerZ()));
            }
        });
    }

    static void registerVillage(ServerWorld world, KingdomData data, UUID owner, KingdomData.KingdomInfo village) {
        data.put(owner, village);
        keepVillageLoaded(world, new BlockPos(village.centerX(), 0, village.centerZ()));
    }

    private static void keepVillageLoaded(ServerWorld world, BlockPos spawn) {
        int radius = VillageBuilder.MAX_VILLAGE_RADIUS + SPAWN_SEARCH_RADIUS;
        var forcedChunks = world.getForcedChunks();
        for (int x = (spawn.getX() - radius) >> 4; x <= (spawn.getX() + radius) >> 4; x++) {
            for (int z = (spawn.getZ() - radius) >> 4; z <= (spawn.getZ() + radius) >> 4; z++) {
                if (!forcedChunks.contains(ChunkPos.toLong(x, z))) {
                    world.setChunkForced(x, z, true);
                }
            }
        }
    }

    /** 接続直後: 王国が未登録なら次ティックのランダムスポーンを予約する。 */
    public static void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        KingdomData data = KingdomData.getOrCreate(world);
        // 初回参加判定は「このプレイヤーの王国が KingdomData にまだ無いこと」。
        if (data.get(player.getUuid()) != null) {
            return;
        }
        PENDING.add(player.getUuid());
    }

    private static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        KingdomData kingdomData = KingdomData.getOrCreate(world);

        // シングルプレイ（統合サーバー）では JOIN イベントの順番が環境に依存するため、
        // 毎ティック「王国が無い」または「王国はあるが村未生成」のプレイヤーを検出する。
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            KingdomData.KingdomInfo info = kingdomData.get(uuid);
            boolean needsVillage = info == null || !info.villageGenerated();
            if (needsVillage && !PENDING.contains(uuid)) {
                PENDING.add(uuid);
            }
        }

        int remaining = PENDING.size();
        while (remaining-- > 0 && !PENDING.isEmpty()) {
            UUID uuid = PENDING.poll();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            KingdomData.KingdomInfo info = kingdomData.get(uuid);
            if (info != null && info.villageGenerated()) {
                continue;
            }
            if (info == null) {
                spawn(player, server, world);
            } else {
                ensureVillage(player, world, kingdomData, info);
            }
        }
    }

    /** 既存王国に村が無い場合: 王国中心に村を生成し、プレイヤーをその中へ移す。 */
    private static void ensureVillage(ServerPlayerEntity player, ServerWorld world,
                                      KingdomData kingdomData, KingdomData.KingdomInfo info) {
        BlockPos center = (info.centerSet())
                ? new BlockPos(info.centerX(), player.getBlockPos().getY(), info.centerZ())
                : player.getBlockPos();
        if (TownHallBuilder.findVillageOrigin(world, center.getX(), center.getZ()) == null) {
            BlockPos alternate = findNearbyStarterSite(world, center);
            if (alternate == null && !info.centerSet()) {
                alternate = findSafeSpawn(world);
            }
            if (alternate != null) {
                center = alternate;
            }
        }

        world.getChunk(center.getX() >> 4, center.getZ() >> 4);
        var starterBuildings = VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ(),
                player.getUuid());
        com.kingdomunique.kingdom.KingdomManager.setPopulation(player, 6);

        BlockPos spawn = findVillageSpawn(world, center);
        if (spawn == null) {
            spawn = center.up();
        }
        player.teleport(world, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                java.util.Set.<PositionFlag>of(), 0.0F, 0.0F);
        player.setSpawnPoint(World.OVERWORLD, spawn, 0.0F, false, false);
        PlayerSpawnData.getOrCreate(world).put(player.getUuid(), spawn);

        registerVillage(world, kingdomData, player.getUuid(), new KingdomData.KingdomInfo(
                spawn.getX(), spawn.getZ(),
                info.population(), info.populationCap(), info.houses(),
                info.heroSummoned(), info.heroSummonCount(), true, info.territoryRadius(),
                true, info.kingdomName()));
        VillageBuilder.registerStarterBuildings(world, player, starterBuildings, spawn);
        player.sendMessage(Text.literal("§6[KingdomUnique] 王国の村が完成した。村の中心に到着した。"), false);
        if (info.kingdomName() == null || info.kingdomName().isEmpty()) {
            askKingdomName(player);
        }
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Generated missing village for {} at ({}, {}).",
                player.getName().getString(), spawn.getX(), spawn.getZ());
    }

    /** 初回建国時に国名入力画面を開かせる。 */
    private static void askKingdomName(ServerPlayerEntity player) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,
                com.kingdomunique.network.RequestKingdomNamePayload.ID)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new com.kingdomunique.network.RequestKingdomNamePayload());
        }
    }
    private static void spawn(ServerPlayerEntity player, MinecraftServer server, ServerWorld world) {
        BlockPos villageCenter = findSafeSpawn(world);
        if (villageCenter == null) {
            // 安全地点が見つからなくても王国は必ず作成し、再予約ループを防ぐ。
            villageCenter = player.getBlockPos();
            KingdomUniqueMod.LOGGER.warn(
                    "[KingdomUnique] No safe random spot for {}; using current position as village center.",
                    player.getName().getString());
        }

        world.getChunk(villageCenter.getX() >> 4, villageCenter.getZ() >> 4);

        // まず村を生成し、その中（井戸の周囲）で安全な立ち位置を探す。
        var starterBuildings = VillageBuilder.generateStarterVillage(world, villageCenter.getX(),
                villageCenter.getZ(), player.getUuid());
        com.kingdomunique.kingdom.KingdomManager.setPopulation(player, 6);
        BlockPos spawn = findVillageSpawn(world, villageCenter);
        if (spawn == null) {
            KingdomUniqueMod.LOGGER.warn("[KingdomUnique] No free spot in village, using center for {}.",
                    player.getName().getString());
            spawn = villageCenter.up();
        }

        // 指定シグネチャで確実にテレポートする（村の中へ）。
        player.teleport(world, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                java.util.Set.<PositionFlag>of(), 0.0F, 0.0F);
        player.setSpawnPoint(World.OVERWORLD, spawn, 0.0F, false, false);
        PlayerSpawnData.getOrCreate(world).put(player.getUuid(), spawn);

        // テレポート地点そのものを王国の中心（初期領土半径100）として保存する。
        registerVillage(world, KingdomData.getOrCreate(world), player.getUuid(),
                new KingdomData.KingdomInfo(spawn.getX(), spawn.getZ(), 0, 10, 0, false, 0,
                        true, 100, true, ""));
        VillageBuilder.registerStarterBuildings(world, player, starterBuildings, spawn);

        player.sendMessage(Text.literal("§6[KingdomUnique] この地があなたの王国となった。"
                + "村の中心に到着した。村人6人が出迎える。"), false);
        askKingdomName(player);
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Starter spawn inside village ({}, {}) -> ({}, {}) for {}.",
                villageCenter.getX(), villageCenter.getZ(), spawn.getX(), spawn.getZ(),
                player.getName().getString());
    }

    /** 村の井戸周囲から、足元・頭上が空で地面が固体の安全な立ち位置を探す。 */
    private static BlockPos findVillageSpawn(ServerWorld world, BlockPos center) {
        for (int radius = 2; radius <= SPAWN_SEARCH_RADIUS; radius++) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2.0D / 16.0D;
                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                net.minecraft.block.BlockState below = world.getBlockState(feet.down());
                if (world.getBlockState(feet).isAir()
                        && world.getBlockState(feet.up()).isAir()
                        && !below.isAir()
                        && below.getFluidState().isEmpty()) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static BlockPos findNearbyStarterSite(ServerWorld world, BlockPos center) {
        for (int radius = 24; radius <= 72; radius += 24) {
            for (int dx = -radius; dx <= radius; dx += 24) {
                for (int dz = -radius; dz <= radius; dz += 24) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = VillageTerrain.plot(world, center.getX() + dx,
                            center.getZ() + dz, 1, 1);
                    if (candidate != null && candidate.getY() >= world.getSeaLevel()
                            && TownHallBuilder.findVillageOrigin(world, candidate.getX(), candidate.getZ()) != null) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** 海・マグマを避け、他プレイヤーの王国から500マス以上離れた地上座標を探す。 */
    private static BlockPos findSafeSpawn(ServerWorld world) {
        KingdomData kingdomData = KingdomData.getOrCreate(world);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-RANGE, RANGE + 1);
            int z = ThreadLocalRandom.current().nextInt(-RANGE, RANGE + 1);
            BlockPos raw = new BlockPos(x, 0, z);

            if (world.getBiome(raw).isIn(BiomeTags.IS_OCEAN)
                    || world.getBiome(raw).isIn(BiomeTags.IS_DEEP_OCEAN)) {
                continue;
            }
            if (world.getBlockState(raw.down()).isOf(Blocks.MAGMA_BLOCK)) {
                continue;
            }
            boolean tooClose = false;
            for (KingdomData.KingdomInfo info : kingdomData.all().values()) {
                double dx = info.centerX() - x;
                double dz = info.centerZ() - z;
                if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }

            world.getChunk(x >> 4, z >> 4);
            BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, raw);
            if (top.getY() < world.getSeaLevel()) {
                continue;
            }
            if (TownHallBuilder.findVillageOrigin(world, x, z) == null) {
                continue;
            }
            return top;
        }
        return null;
    }
}
