package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.kingdom.KingdomData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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

    /** 接続直後に積み、次のサーバーティックで処理するプレイヤー。 */
    private static final Queue<UUID> PENDING = new ArrayDeque<>();

    private StarterSpawn() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StarterSpawn::onServerTick);
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
        if (PENDING.isEmpty()) {
            return;
        }
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        int remaining = PENDING.size();
        while (remaining-- > 0 && !PENDING.isEmpty()) {
            UUID uuid = PENDING.poll();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            // 予約から次のティックまでに王国が作られていたら二重生成しない。
            if (KingdomData.getOrCreate(world).get(uuid) != null) {
                continue;
            }
            spawn(player, server, world);
        }
    }

    private static void spawn(ServerPlayerEntity player, MinecraftServer server, ServerWorld world) {
        BlockPos villageCenter = findSafeSpawn(world);
        if (villageCenter == null) {
            KingdomUniqueMod.LOGGER.warn("[KingdomUnique] Could not find a safe starter spawn for {}.",
                    player.getName().getString());
            return;
        }

        world.getChunk(villageCenter.getX() >> 4, villageCenter.getZ() >> 4);

        // まず村を生成し、その中（井戸の周囲）で安全な立ち位置を探す。
        VillageBuilder.generateStarterVillage(world, villageCenter.getX(), villageCenter.getZ());
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
        KingdomData.getOrCreate(world).put(player.getUuid(),
                new KingdomData.KingdomInfo(spawn.getX(), spawn.getZ(), 0, 10, 0, false, 0, true, 100));

        player.sendMessage(Text.literal("§6[KingdomUnique] この地があなたの王国となった。"
                + "村の中心に到着した。村人6人が出迎える。"), false);
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Starter spawn inside village ({}, {}) -> ({}, {}) for {}.",
                villageCenter.getX(), villageCenter.getZ(), spawn.getX(), spawn.getZ(),
                player.getName().getString());
    }

    /** 村の井戸周囲から、足元・頭上が空で地面が固体の安全な立ち位置を探す。 */
    private static BlockPos findVillageSpawn(ServerWorld world, BlockPos center) {
        int groundY = center.getY() + 1;
        for (int radius = 2; radius <= 8; radius++) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2.0D / 16.0D;
                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
                for (int y = groundY + 8; y >= groundY - 4; y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    net.minecraft.block.BlockState below = world.getBlockState(feet.down());
                    if (world.getBlockState(feet).isAir()
                            && world.getBlockState(feet.up()).isAir()
                            && !below.isAir()
                            && !below.isOf(Blocks.WATER)
                            && !below.isOf(Blocks.LAVA)) {
                        return feet;
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
            return top;
        }
        return null;
    }
}
