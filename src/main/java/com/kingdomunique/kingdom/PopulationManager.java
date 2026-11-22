package com.kingdomunique.kingdom;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.effect.RelicEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 王国の人口自然増減（Gemini 提示を既存 CitizenVillager / KingdomManager へ接続）。
 * 住宅で上限増 → 食料＋穀物庫＋遺物で自然増加（15人で遺物解放）→ 戦死で減少。
 */
public final class PopulationManager {

    /** 10分 (12000 tick) ごとに人口増加判定。 */
    private static final long GROWTH_CHECK_INTERVAL = 12000L;
    private static final Map<UUID, Double> GROWTH_PROGRESS = new HashMap<>();

    private PopulationManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PopulationManager::onWorldTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(PopulationManager::onEntityDeath);
    }

    /** 保存された王国の人口上限を返す。 */
    public static int calculateMaxPopulation(ServerWorld world, UUID kingUuid) {
        return KingdomData.getOrCreate(world.getServer().getOverworld())
                .getOrCreate(kingUuid, world.getSpawnPos().getX(), world.getSpawnPos().getZ()).populationCap();
    }

    /** 生存中の国民エンティティ数を数える（既存 CitizenVillager の所有者判定）。 */
    public static int countLivingCitizens(ServerWorld world, UUID kingUuid, BlockPos center) {
        Box territory = new Box(center).expand(128.0D);
        List<VillagerEntity> citizens = world.getEntitiesByClass(
                VillagerEntity.class, territory,
                v -> v.isAlive() && v instanceof CitizenVillager c
                        && kingUuid.toString().equals(c.ku$getOwnerUuid()));
        return citizens.size();
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD
                || world.getTime() % GROWTH_CHECK_INTERVAL != 0L) {
            return;
        }
        // Gemini 修正: 夜の間だけ出生判定を行う。
        if (!world.isNight()) {
            return;
        }
        MinecraftServer server = world.getServer();
        KingdomBuildingState buildings = KingdomBuildingState.get(world);

        for (ServerPlayerEntity king : server.getPlayerManager().getPlayerList()) {
            if (king.getServerWorld() != world) {
                continue;
            }
            UUID kingId = king.getUuid();
            BlockPos center = findKingdomCenter(buildings, kingId, king.getBlockPos());
            if (!world.isChunkLoaded(center.getX() >> 4, center.getZ() >> 4)) {
                continue;
            }

            var info = KingdomManager.getOrCreate(king);
            int currentPop = info.population();
            int maxPop = info.populationCap();

            if (currentPop >= maxPop || currentPop < 2) {
                continue;
            }
            // Gemini 修正: 国庫（チェスト）から優先消費し、不足分は王が補填する。
            if (!com.kingdomunique.world.KingdomTreasuryUtil.consumeFromTreasuryOrKing(
                    world, king, Items.BREAD, 4)) {
                continue;
            }

            boolean hasGranary = buildings.hasCompletedBuilding(kingId, BuildingType.GRANARY);
            double multiplier = (hasGranary ? 1.3D : 1.0D) * RelicEffects.getPopulationGrowthMultiplier(king);
            double progress = GROWTH_PROGRESS.getOrDefault(kingId, 0.0D) + (0.5D * multiplier);
            if (progress >= 1.0D) {
                GROWTH_PROGRESS.put(kingId, progress - 1.0D);
                spawnNewCitizen(world, king, center, currentPop + 1, maxPop);
            } else {
                GROWTH_PROGRESS.put(kingId, progress);
            }
        }
    }

    /** 新しい国民を誕生させる。 */
    private static void spawnNewCitizen(ServerWorld world, ServerPlayerEntity king,
                                        BlockPos center, int newPop, int maxPop) {
        VillagerEntity citizen = EntityType.VILLAGER.create(world);
        if (citizen == null) {
            return;
        }
        double ox = (world.random.nextDouble() - 0.5D) * 6.0D;
        double oz = (world.random.nextDouble() - 0.5D) * 6.0D;
        citizen.refreshPositionAndAngles(center.getX() + ox, center.getY() + 1.0D,
                center.getZ() + oz, 0.0F, 0.0F);
        citizen.initialize(world, world.getLocalDifficulty(center), SpawnReason.BREEDING, null);

        VillagerData data = citizen.getVillagerData()
                .withType(VillagerType.PLAINS)
                .withProfession(VillagerProfession.NONE);
        citizen.setVillagerData(data);

        // 既存の国民データ方式で所有者・役職を付与（農民として誕生）。
        if (citizen instanceof CitizenVillager c) {
            c.ku$setOwnerUuid(king.getUuid().toString());
            c.ku$setRole(CitizenVillager.ROLE_FARMER);
        }
        citizen.setPersistent();
        if (!world.spawnEntity(citizen)) {
            return;
        }

        world.spawnParticles(ParticleTypes.HEART,
                citizen.getX(), citizen.getBodyY(0.8D), citizen.getZ(),
                8, 0.4D, 0.4D, 0.4D, 0.02D);
        king.sendMessage(Text.literal("§a[王国繁栄] 新たな国民が誕生しました！（人口: "
                + newPop + "/" + maxPop + "）"), false);

        KingdomManager.setPopulation(king, newPop);
        com.kingdomunique.status.StatusManager.syncPopulationToPlayer(king, world);
    }

    /** 国民・兵士が死亡したときの人口減少と訃報を通知する。 */
    private static void onEntityDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
        if (entity.getWorld().isClient() || !(entity instanceof VillagerEntity villager)
                || !(villager instanceof CitizenVillager citizen)) {
            return;
        }
        String owner = citizen.ku$getOwnerUuid();
        if (owner.isEmpty()) {
            return;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        ServerWorld world = (ServerWorld) villager.getWorld();
        ServerPlayerEntity king = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        KingdomData data = KingdomData.getOrCreate(world.getServer().getOverworld());
        var info = data.get(ownerUuid);
        if (info == null) {
            return;
        }
        int remaining = Math.max(0, info.population() - 1);
        int maxPop = info.populationCap();
        data.put(ownerUuid, new KingdomData.KingdomInfo(info.centerX(), info.centerZ(), remaining,
                maxPop, info.houses(), info.heroSummoned(), info.heroSummonCount(), info.centerSet(),
                info.territoryRadius(), info.villageGenerated(), info.kingdomName()));
        if (king != null) {
            String role = CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole()) ? "王国兵士" : "国民";
            king.sendMessage(Text.literal("§c[訃報] " + role + "が死亡しました…（残り人口: "
                    + remaining + "/" + maxPop + "）"), false);
            com.kingdomunique.status.StatusManager.syncPopulationToPlayer(king, world);
        }
    }

    private static BlockPos findKingdomCenter(KingdomBuildingState buildings, UUID kingUuid,
                                              BlockPos fallback) {
        for (KingdomBuildingState.CompletedBuilding b : buildings.entries()) {
            if (b.ownerUuid.equals(kingUuid) && b.type == BuildingType.TOWN_HALL) {
                return b.origin;
            }
        }
        return fallback;
    }
}
