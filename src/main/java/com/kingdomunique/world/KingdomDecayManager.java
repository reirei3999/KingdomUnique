package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 王国の維持費と緩やかな崩壊（Gemini 提示）。
 * 王がオンラインの間だけ20分ごとに維持費を徴収する。
 * 維持費は建物3棟につきエメラルド1個（最低1）＋国民1人につきパン1個。
 * 国庫（チェスト）から優先消費し、不足分は王のインベントリから補填する。
 */
public final class KingdomDecayManager {

    private static final long UPKEEP_INTERVAL_TICKS = 24000L;
    private static final Map<UUID, Long> ONLINE_TIMER = new HashMap<>();
    private static final Map<UUID, Integer> DECAY_LEVEL = new HashMap<>();

    private KingdomDecayManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(KingdomDecayManager::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        // 1秒（20tick）ごとにオンラインの王のタイマーを進める。オフライン中は増えない。
        if (server.getTicks() % 20L != 0L) {
            return;
        }
        for (ServerPlayerEntity king : server.getPlayerManager().getPlayerList()) {
            UUID id = king.getUuid();
            long elapsed = ONLINE_TIMER.getOrDefault(id, 0L) + 20L;
            if (elapsed >= UPKEEP_INTERVAL_TICKS) {
                ONLINE_TIMER.put(id, 0L);
                processUpkeep(king.getServerWorld(), king);
            } else {
                ONLINE_TIMER.put(id, elapsed);
            }
        }
    }

    private static void processUpkeep(ServerWorld world, ServerPlayerEntity king) {
        UUID kingId = king.getUuid();
        KingdomBuildingState buildings = KingdomBuildingState.get(world);
        long buildingCount = buildings.entries().stream()
                .filter(b -> b.ownerUuid.equals(kingId))
                .count();

        BlockPos center = king.getBlockPos();
        for (KingdomBuildingState.CompletedBuilding b : buildings.entries()) {
            if (b.ownerUuid.equals(kingId) && b.type == BuildingType.TOWN_HALL) {
                center = b.origin;
                break;
            }
        }

        List<VillagerEntity> citizens = world.getEntitiesByClass(
                VillagerEntity.class, new Box(center).expand(128.0D),
                v -> v.isAlive() && v instanceof CitizenVillager c
                        && kingId.toString().equals(c.ku$getOwnerUuid()));

        if (buildingCount == 0 && citizens.isEmpty()) {
            return;
        }

        // 維持費：建物3棟につきエメラルド1個（最低1）＋国民1人につきパン1個。
        int emeraldUpkeep = buildingCount > 0 ? Math.max(1, (int) (buildingCount / 3)) : 0;
        int breadUpkeep = citizens.size();

        boolean paidEmerald = KingdomTreasuryUtil.consumeFromTreasuryOrKing(
                world, king, Items.EMERALD, emeraldUpkeep);
        boolean paidBread = KingdomTreasuryUtil.consumeFromTreasuryOrKing(
                world, king, Items.BREAD, breadUpkeep);

        if (paidEmerald && paidBread) {
            int decay = DECAY_LEVEL.getOrDefault(kingId, 0);
            if (decay > 0) {
                DECAY_LEVEL.put(kingId, decay - 1);
            }
            king.sendMessage(Text.literal("§7[王国維持] 国庫から維持費（エメラルドx" + emeraldUpkeep
                    + " / パンx" + breadUpkeep + "）が徴収されました。"), false);
        } else {
            int decay = DECAY_LEVEL.getOrDefault(kingId, 0) + 1;
            DECAY_LEVEL.put(kingId, decay);
            king.sendMessage(Text.literal("§c[王国衰退] 国庫の維持費（エメラルドx" + emeraldUpkeep
                    + " / パンx" + breadUpkeep + "）が不足しています！荒廃度: " + decay), false);
            applyDecay(world, buildings, kingId, citizens, decay, king);
        }
    }

    private static void applyDecay(ServerWorld world, KingdomBuildingState state, UUID kingId,
                                   List<VillagerEntity> citizens, int decay, ServerPlayerEntity king) {
        Random random = world.getRandom();

        for (KingdomBuildingState.CompletedBuilding b : state.entries()) {
            if (!b.ownerUuid.equals(kingId)) {
                continue;
            }
            if (!world.isChunkLoaded(b.origin.getX() >> 4, b.origin.getZ() >> 4)) {
                continue;
            }
            BlockBox box = b.boundingBox;
            int attempts = Math.min(10, decay * 2);
            for (int i = 0; i < attempts; i++) {
                int rx = random.nextBetween(box.getMinX(), box.getMaxX());
                int ry = random.nextBetween(box.getMinY(), box.getMaxY());
                int rz = random.nextBetween(box.getMinZ(), box.getMaxZ());
                BlockPos target = new BlockPos(rx, ry, rz);
                BlockState current = world.getBlockState(target);
                if (current.isOf(Blocks.STONE_BRICKS)) {
                    Block weathered = random.nextBoolean()
                            ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS;
                    world.setBlockState(target, weathered.getDefaultState(), Block.NOTIFY_LISTENERS);
                } else if (current.isOf(Blocks.COBBLESTONE)) {
                    world.setBlockState(target, Blocks.MOSSY_COBBLESTONE.getDefaultState(),
                            Block.NOTIFY_LISTENERS);
                } else if (decay >= 4 && (current.isOf(Blocks.GLASS_PANE)
                        || current.isOf(Blocks.LANTERN))) {
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        for (VillagerEntity citizen : citizens) {
            citizen.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 6000, 0));
        }

        // 荒廃度5以上で国民が1人離反（既存 CitizenVillager データをクリア＋人口減）。
        if (decay >= 5 && !citizens.isEmpty()) {
            VillagerEntity deserter = citizens.get(random.nextInt(citizens.size()));
            if (deserter instanceof CitizenVillager c) {
                c.ku$setOwnerUuid("");
                c.ku$setRole("");
            }
            deserter.setCustomName(null);
            deserter.setCustomNameVisible(false);
            KingdomData.KingdomInfo info = KingdomData.getOrCreate(world).get(kingId);
            if (info != null) {
                KingdomManager.setPopulation(king, Math.max(0, info.population() - 1));
            }
            king.sendMessage(Text.literal("§c[王国衰退] 国民が1人王国を去りました。"), false);
        }
    }
}
