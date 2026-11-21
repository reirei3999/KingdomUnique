package com.kingdomunique.world;

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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 王国の維持費と緩やかな崩壊（Gemini 提示）。
 * 1日（24000tick）ごとに維持費を徴収し、滞納すると建物の風化・国民の疲弊・離反が進む。
 */
public final class KingdomDecayManager {

    private static final long UPKEEP_INTERVAL_TICKS = 24000L;
    private static final Map<UUID, Integer> DECAY_LEVEL = new HashMap<>();

    private KingdomDecayManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(KingdomDecayManager::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        if (world.getTime() % UPKEEP_INTERVAL_TICKS != 0L) {
            return;
        }
        MinecraftServer server = world.getServer();
        KingdomBuildingState buildingState = KingdomBuildingState.get(world);

        for (ServerPlayerEntity king : server.getPlayerManager().getPlayerList()) {
            if (king.getServerWorld() != world) {
                continue;
            }
            UUID kingId = king.getUuid();
            long buildingCount = buildingState.entries().stream()
                    .filter(b -> b.ownerUuid.equals(kingId))
                    .count();
            if (buildingCount == 0) {
                continue;
            }

            List<VillagerEntity> citizens = world.getEntitiesByClass(
                    VillagerEntity.class,
                    new Box(king.getBlockPos()).expand(96.0D),
                    v -> v instanceof CitizenVillager c && kingId.toString().equals(c.ku$getOwnerUuid()));

            int emeraldUpkeep = (int) buildingCount;
            int breadUpkeep = Math.max(1, citizens.size() / 2);

            boolean paidEmerald = consumeFromPlayer(king, Items.EMERALD, emeraldUpkeep);
            boolean paidFood = consumeFromPlayer(king, Items.BREAD, breadUpkeep);

            if (paidEmerald && paidFood) {
                int current = DECAY_LEVEL.getOrDefault(kingId, 0);
                if (current > 0) {
                    DECAY_LEVEL.put(kingId, current - 1);
                }
                king.sendMessage(Text.literal("§7[王国維持] 維持費（エメラルドx" + emeraldUpkeep
                        + " / パンx" + breadUpkeep + "）を消費しました。"), false);
            } else {
                int decay = DECAY_LEVEL.getOrDefault(kingId, 0) + 1;
                DECAY_LEVEL.put(kingId, decay);
                king.sendMessage(Text.literal("§c[王国衰退] 維持費（エメラルドx" + emeraldUpkeep
                        + " / パンx" + breadUpkeep + "）が不足しています！荒廃度: " + decay), false);
                applyGradualDecay(world, king, buildingState, kingId, citizens, decay);
            }
        }
    }

    /** 建物の風化と国民の疲弊・離反を適用する。 */
    private static void applyGradualDecay(ServerWorld world, ServerPlayerEntity king,
                                          KingdomBuildingState state, UUID kingId,
                                          List<VillagerEntity> citizens, int decay) {
        Random random = world.getRandom();

        for (KingdomBuildingState.CompletedBuilding b : state.entries()) {
            if (!b.ownerUuid.equals(kingId)) {
                continue;
            }
            if (!world.isChunkLoaded(b.origin.getX() >> 4, b.origin.getZ() >> 4)) {
                continue;
            }
            BlockBox box = b.boundingBox;
            int attempts = Math.min(12, decay * 2);
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

        // 荒廃度5以上で国民が1人見限って一般村人へ戻る（人口も減らす）。
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
            king.sendMessage(Text.literal("§c[王国衰退] 国民が1人王国を去りました（現在人口: "
                    + Math.max(0, (info == null ? 0 : info.population() - 1)) + "）。"), false);
        }
    }

    private static boolean consumeFromPlayer(ServerPlayerEntity player, Item item, int amount) {
        int available = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) {
                available += s.getCount();
            }
        }
        if (available < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        return true;
    }
}
