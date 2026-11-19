package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.kingdom.KingdomData;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/** 初回ログイン時の 4000x4000 ランダムスポーンと初期村生成。 */
public final class StarterSpawn {

    private static final int RANGE = 2000;
    private static final int MIN_DISTANCE = 500;
    private static final int MAX_ATTEMPTS = 256;
    private static final int VILLAGE_OFFSET = 12;

    private StarterSpawn() {
    }

    public static void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        PlayerSpawnData data = PlayerSpawnData.getOrCreate(world);
        if (data.has(player.getUuid())) {
            return;
        }

        BlockPos spawn = findSafeSpawn(world, data);
        if (spawn == null) {
            KingdomUniqueMod.LOGGER.warn("[KingdomUnique] Could not find a safe starter spawn for {}.",
                    player.getName().getString());
            return;
        }

        // チャンクを確実にロードしてからテレポート
        world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        player.teleport(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, false);
        player.setSpawnPoint(World.OVERWORLD, spawn.up(), 0.0F, false, false);
        data.put(player.getUuid(), spawn);

        // スポーン地点そのものを王国の中心（初期領土半径100）として即時登録する。
        com.kingdomunique.kingdom.KingdomManager.setKingdomCenter(player, spawn, true);

        // 村はプレイヤーの目の前〜半径15ブロック以内に生成する（ズレをなくす）。
        int vx = spawn.getX() + VILLAGE_OFFSET;
        int vz = spawn.getZ() + VILLAGE_OFFSET;
        VillageBuilder.generateStarterVillage(world, vx, vz);
        player.sendMessage(Text.literal("§6[KingdomUnique] この地があなたの王国となった。"
                + "すぐそばに初期村が築かれた。"), false);
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Starter spawn {} -> village ({}, {}) for {}.",
                spawn.getX() + "," + spawn.getZ(), vx, vz, player.getName().getString());
    }

    /** 海・マグマを避け、既存スポーンから600マス以上離れた地上座標を探す。 */
    private static BlockPos findSafeSpawn(ServerWorld world, PlayerSpawnData data) {
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
            // 既存プレイヤーのスポーン座標との距離。
            for (BlockPos existing : data.all()) {
                double dx = existing.getX() - x;
                double dz = existing.getZ() - z;
                if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) {
                    tooClose = true;
                    break;
                }
            }
            // 既存王国の中心座標との距離（こちらも最低500マス離す）。
            if (!tooClose) {
                for (KingdomData.KingdomInfo info
                        : KingdomData.getOrCreate(world).all().values()) {
                    double dx = info.centerX() - x;
                    double dz = info.centerZ() - z;
                    if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) {
                        tooClose = true;
                        break;
                    }
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
            return top;
        }
        return null;
    }
}
