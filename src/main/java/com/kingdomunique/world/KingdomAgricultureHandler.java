package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.entity.CitizenVillager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.UUID;

/** 豊穣・繁栄の揺りかごの作物バフを王国領内の畑と農民にも適用する（Gemini 提示）。 */
public final class KingdomAgricultureHandler {

    private KingdomAgricultureHandler() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(KingdomAgricultureHandler::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getTime() % 100L != 0L) {
            return;
        }
        MinecraftServer server = world.getServer();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        KingdomBuildingState buildings = KingdomBuildingState.get(world);

        for (ServerPlayerEntity king : server.getPlayerManager().getPlayerList()) {
            if (king.getServerWorld() != world) {
                continue;
            }
            UUID kingId = king.getUuid();
            boolean hasFertility = data.hasId(kingId, "fertility");
            boolean hasCradle = data.hasId(kingId, "cradle_of_prosperity");
            if (!hasFertility && !hasCradle) {
                continue;
            }

            for (KingdomBuildingState.CompletedBuilding b : buildings.entries()) {
                if (!b.ownerUuid.equals(kingId)) {
                    continue;
                }
                if (b.type != BuildingType.GRANARY && b.type != BuildingType.TOWN_HALL) {
                    continue;
                }
                if (!world.isChunkLoaded(b.origin.getX() >> 4, b.origin.getZ() >> 4)) {
                    continue;
                }
                boostCrops(world, b.origin, 32, hasFertility ? 18 : 8);
                if (hasFertility && world.getTime() % 1200L == 0L) {
                    depositHarvest(world, kingId, b.origin);
                }
            }
        }
    }

    /** 指定座標周辺の未成熟作物をランダムに成長させる。 */
    private static void boostCrops(ServerWorld world, BlockPos center, int radius, int attempts) {
        Random random = world.getRandom();
        for (int i = 0; i < attempts; i++) {
            int dx = random.nextBetween(-radius, radius);
            int dy = random.nextBetween(-4, 6);
            int dz = random.nextBetween(-radius, radius);
            BlockPos pos = center.add(dx, dy, dz);
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                crop.applyGrowth(world, pos, state);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D,
                        2, 0.2D, 0.1D, 0.2D, 0.0D);
            }
        }
    }

    /** 国内の農民数×2個のパンを国庫チェストへ自動納品する。 */
    private static void depositHarvest(ServerWorld world, UUID kingId, BlockPos center) {
        List<VillagerEntity> farmers = world.getEntitiesByClass(
                VillagerEntity.class, new Box(center).expand(32.0D),
                v -> v.isAlive() && v instanceof CitizenVillager c
                        && CitizenVillager.ROLE_FARMER.equals(c.ku$getRole())
                        && kingId.toString().equals(c.ku$getOwnerUuid()));
        if (farmers.isEmpty()) {
            return;
        }
        ItemStack bread = new ItemStack(Items.BREAD, farmers.size() * 2);
        for (Inventory inv : KingdomTreasuryUtil.getKingdomStorages(world, kingId)) {
            for (int slot = 0; slot < inv.size(); slot++) {
                ItemStack existing = inv.getStack(slot);
                if (existing.isEmpty()) {
                    inv.setStack(slot, bread.copy());
                    inv.markDirty();
                    return;
                } else if (existing.isOf(Items.BREAD) && existing.getCount() < existing.getMaxCount()) {
                    int add = Math.min(existing.getMaxCount() - existing.getCount(), bread.getCount());
                    existing.increment(add);
                    bread.decrement(add);
                    inv.markDirty();
                    if (bread.isEmpty()) {
                        return;
                    }
                }
            }
        }
    }
}
