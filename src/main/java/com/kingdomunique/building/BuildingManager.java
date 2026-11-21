package com.kingdomunique.building;

import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;

/** 建築コスト／時間の倍率と自動建築の進行管理。 */
public final class BuildingManager {

    private BuildingManager() {
    }

    /** 必要資材コスト倍率（現場監督30%OFF、ヘファイストス20%OFF）。 */
    public static double costMultiplier(ServerPlayerEntity player) {
        double multiplier = 1.0D;
        if (unique(player, "site_supervisor")) {
            multiplier -= 0.30D;
        }
        if (hasHephaestus(player)) {
            multiplier -= 0.20D;
        }
        return Math.max(0.4D, multiplier);
    }

    /** 建築時間倍率（現場監督30%、設計盤50%、ヘファイストス30%短縮）。 */
    public static double timeMultiplier(ServerPlayerEntity player) {
        double multiplier = RelicEffects.getBuildTimeMultiplier(player)
                * com.kingdomunique.kingdom.PolicyManager.buildTimeMultiplier(
                        player.getServerWorld(), player.getUuid());
        if (unique(player, "site_supervisor")) {
            multiplier -= 0.30D;
        }
        if (hasHephaestus(player)) {
            multiplier -= 0.30D;
        }
        return Math.max(0.15D, multiplier);
    }

    /** 建築士の作業効率（設計盤2倍、ヘファイストス1.5倍）。 */
    public static double builderEfficiency(ServerPlayerEntity player) {
        double value = RelicEffects.getBuilderEfficiencyMultiplier(player);
        if (hasHephaestus(player)) {
            value *= 1.5D;
        }
        return value;
    }

    private static boolean unique(ServerPlayerEntity player, String id) {
        com.kingdomunique.data.WorldUniqueData data =
                com.kingdomunique.data.WorldUniqueData.getOrCreate(
                        player.getServer().getWorld(World.OVERWORLD));
        return data.hasId(player.getUuid(), id);
    }

    private static boolean hasHephaestus(ServerPlayerEntity player) {
        for (HeroEntity hero : player.getWorld().getEntitiesByClass(HeroEntity.class,
                player.getBoundingBox().expand(128.0D), h -> h.isAlive())) {
            if (hero.getHeroClass() == HeroClass.SMITH && player.getUuid().equals(hero.getOwnerUuid())) {
                return true;
            }
        }
        return false;
    }

    /** 隣接チェストから資材を消費する。 */
    public static boolean consumeMaterials(Inventory chest, BuildingType type, ServerPlayerEntity player) {
        double multiplier = costMultiplier(player);
        for (Map.Entry<Item, Integer> entry : type.materials().entrySet()) {
            int needed = Math.max(1, (int) Math.ceil(entry.getValue() * multiplier));
            int available = 0;
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(entry.getKey())) {
                    available += stack.getCount();
                }
            }
            if (available < needed) {
                player.sendMessage(Text.literal("§c[設計図] " + entry.getKey().getName().getString()
                        + " が " + needed + " 個必要です。"), false);
                return false;
            }
            int remaining = needed;
            for (int i = 0; i < chest.size() && remaining > 0; i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(entry.getKey())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.decrement(take);
                    remaining -= take;
                }
            }
            chest.markDirty();
        }
        return true;
    }

    /** 建築現場に割り当てた建築士（dimension|pos -> villager UUID）。 */
    private static final java.util.Map<String, java.util.UUID> ASSIGNED_BUILDERS =
            new java.util.HashMap<>();

    private static String siteKey(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() + "|" + pos.asLong();
    }

    /** 毎Tickの建築進行。 */
    public static void onServerTick(MinecraftServer server) {
        BuildingEffectHandler.tick(server);
        for (ServerWorld world : server.getWorlds()) {
            BuildData data = BuildData.getOrCreate(world);
            for (BuildData.BuildSite site : new java.util.ArrayList<>(data.sites())) {
                if (!site.ready()) {
                    continue;
                }
                if (!world.isChunkLoaded(site.pos())) {
                    continue;
                }
                ServerPlayerEntity owner = null;
                if (world.getEntity(site.owner()) instanceof ServerPlayerEntity player) {
                    owner = player;
                }
                if (owner == null) {
                    continue;
                }

                // 建築士を現場へ向かわせ、到着するまで進行させない。
                net.minecraft.entity.passive.VillagerEntity builder =
                        guideBuilder(world, site, owner);
                int workers = countBuildersNearby(world, site.pos(), owner);
                if (builder == null || workers == 0) {
                    continue;
                }

                // Gemini 修正: 現場の建築士人数に応じて建築速度を加速（1人につき+1 tick/時）。
                int progressPerTick = 1 + (workers - 1);
                // 到着済み建築士に作業モーションを periodic に見せる。
                if (world.getTime() % 20L == 0L) {
                    playBuilderWorkAnimation(world, site, owner);
                }

                int required = (int) Math.max(20L, site.type().getBaseBuildTicks() * timeMultiplier(owner));
                int progress = site.progress() + progressPerTick;
                if (progress % 20 == 0) {
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            site.pos().getX() + 0.5, site.pos().getY() + 1.0, site.pos().getZ() + 0.5,
                            8, 0.5, 0.5, 0.5, 0.05);
                    world.playSound(null, site.pos(), SoundEvents.BLOCK_ANVIL_USE,
                            SoundCategory.BLOCKS, 0.6F, 1.0F);
                }
                if (progress >= required) {
                    completeNow(world, site, owner);
                } else {
                    data.sites().remove(site);
                    data.add(site.withProgress(progress));
                }
            }
        }
    }

    /** 建築士を選択し、現場へ誘導する。到着後はその場に留まらせる。 */
    private static net.minecraft.entity.passive.VillagerEntity guideBuilder(
            ServerWorld world, BuildData.BuildSite site, ServerPlayerEntity owner) {
        String key = siteKey(world, site.pos());
        net.minecraft.entity.passive.VillagerEntity builder = null;
        java.util.UUID assigned = ASSIGNED_BUILDERS.get(key);
        if (assigned != null && world.getEntity(assigned)
                instanceof net.minecraft.entity.passive.VillagerEntity found && found.isAlive()) {
            builder = found;
        }
        if (builder == null) {
            builder = findNearestBuilder(world, site.pos(), owner);
            if (builder == null) {
                return null;
            }
            ASSIGNED_BUILDERS.put(key, builder.getUuid());
            owner.sendMessage(Text.literal("§6[設計図] 建築士が現場へ向かいました。"), false);
        }

        double dx = builder.getX() - (site.pos().getX() + 0.5D);
        double dz = builder.getZ() - (site.pos().getZ() + 0.5D);
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq <= 4.0D) {
            // 現場に到着：徘徊させずその場に留める。
            builder.getNavigation().stop();
            builder.setYaw((float) (Math.atan2(-dx, dz) * 180.0D / Math.PI));
        } else {
            builder.getNavigation().startMovingTo(
                    site.pos().getX() + 0.5D, site.pos().getY(), site.pos().getZ() + 0.5D, 0.7D);
        }
        return builder;
    }

    private static net.minecraft.entity.passive.VillagerEntity findNearestBuilder(
            ServerWorld world, BlockPos pos, ServerPlayerEntity owner) {
        net.minecraft.entity.passive.VillagerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (net.minecraft.entity.passive.VillagerEntity villager : world.getEntitiesByClass(
                net.minecraft.entity.passive.VillagerEntity.class,
                new net.minecraft.util.math.Box(pos).expand(128.0D),
                v -> v.isAlive())) {
            if (!(villager instanceof CitizenVillager citizen)
                    || !owner.getUuid().toString().equals(citizen.ku$getOwnerUuid())
                    || !CitizenVillager.ROLE_BUILDER.equals(citizen.ku$getRole())) {
                continue;
            }
            double d = villager.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = villager;
            }
        }
        return best;
    }

    private static void releaseBuilder(ServerWorld world, BuildData.BuildSite site) {
        java.util.UUID uuid = ASSIGNED_BUILDERS.remove(siteKey(world, site.pos()));
        if (uuid != null && world.getEntity(uuid)
                instanceof net.minecraft.entity.passive.VillagerEntity villager) {
            villager.getNavigation().stop();
        }
    }
    /** 建築を即座に完了させる（通常進行と OP クリエイティブの即時完了から共通利用）。 */
    public static boolean completeNow(ServerWorld world, BuildData.BuildSite site,
                                      ServerPlayerEntity owner) {
        if (!canPlaceSafely(world, site.pos())) {
            return false;
        }
        StructurePlacer.place(world, site.pos(), site.type(), site.facing());
        net.minecraft.util.math.Direction facing = site.facing();
        net.minecraft.util.math.BlockBox box = StructureRotationUtil.computeBoundingBox(site.pos(), site.type(), facing);
        net.minecraft.util.math.BlockPos core = site.type() == BuildingType.HERO_SANCTUARY
                ? sanctuaryCore(site.pos(), facing) : null;
        KingdomBuildingState.get(world).add(owner.getUuid(), site.type(), site.pos(), facing, box, core);
        BuildingEffectHandler.onCompleted(world, owner.getUuid(), site.type(), site.pos());
        CompletedBuildingsData.getOrCreate(world).add(site.pos(), site.type(), owner.getUuid());
        DefenseData.register(world, site.pos(), site.type(), owner.getUuid(), facing);
        if (site.type() == BuildingType.TOWN_HALL) {
            KingdomManager.markTownHall(owner, site.pos());
            com.kingdomunique.kingdom.PolicyManager.sendPolicyOffer(owner);
        } else if (site.type() == BuildingType.HERO_SANCTUARY) {
            net.minecraft.util.math.BlockPos sanctuaryCore = core != null ? core
                    : sanctuaryCore(site.pos(), net.minecraft.util.math.Direction.SOUTH);
            com.kingdomunique.kingdom.SanctuaryData.getOrCreate(world)
                    .register(sanctuaryCore, owner.getUuid());
            owner.sendMessage(Text.literal("§6[英雄召喚の祭壇] 完成！中央のロードストーンを右クリックして英雄を召喚できます。"), false);
        } else if (site.type().getPopulationCapBonus() > 0) {
            KingdomManager.addCapBonus(owner, site.type().getPopulationCapBonus());
        }
        world.playSound(null, site.pos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        owner.sendMessage(Text.literal("§6[設計図] " + site.type().getDisplayName()
                + " が完成しました！"), false);
        BuildData data = BuildData.getOrCreate(world);
        data.sites().remove(site);
        data.markDirty();
        releaseBuilder(world, site);
        com.kingdomunique.status.StatusManager.syncBuildingBoxesToWorld(world);
        return true;
    }
    /** 設計図を使わずに建った建物（初期村など）を、完成済みの王国建物として登録する。 */
    public static void registerExisting(ServerWorld world, ServerPlayerEntity owner, BuildingType type,
                                        net.minecraft.util.math.BlockPos origin,
                                        net.minecraft.util.math.Direction facing) {
        KingdomBuildingState state = KingdomBuildingState.get(world);
        boolean known = state.entries().stream()
                .anyMatch(b -> b.origin.equals(origin) && b.type == type && b.ownerUuid.equals(owner.getUuid()));
        net.minecraft.util.math.BlockBox box = StructureRotationUtil.computeBoundingBox(origin, type, facing);
        state.add(owner.getUuid(), type, origin, facing, box, null);
        CompletedBuildingsData.getOrCreate(world).add(origin, type, owner.getUuid());
        DefenseData.register(world, origin, type, owner.getUuid(), facing);
        if (!known && type != BuildingType.TOWN_HALL && type != BuildingType.HERO_SANCTUARY
                && type.getPopulationCapBonus() > 0) {
            KingdomManager.addCapBonus(owner, type.getPopulationCapBonus());
        }
    }
    /** チャンク未ロード・高度範囲外での配置を防ぐ（Gemini 提示のバグ③対策）。 */
    public static boolean canPlaceSafely(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                && world.isInBuildLimit(pos);
    }
    /** 建築サイトの完了までに必要なティック数（依頼者の倍率を反映）。 */
    public static int requiredTicks(ServerWorld world, BuildData.BuildSite site) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(site.owner());
        double multiplier = owner != null ? timeMultiplier(owner) : 1.0D;
        return (int) Math.max(20L, site.type().getBaseBuildTicks() * multiplier);
    }

    /** 現場周辺（半径5マス）にいる自国の建築士数を数える（Gemini 提示の速度ボーナス用）。 */
    private static int countBuildersNearby(ServerWorld world, BlockPos pos, ServerPlayerEntity owner) {
        int count = 0;
        for (net.minecraft.entity.passive.VillagerEntity villager : world.getEntitiesByClass(
                net.minecraft.entity.passive.VillagerEntity.class,
                new net.minecraft.util.math.Box(pos).expand(5.0D),
                v -> v.isAlive() && v instanceof CitizenVillager)) {
            CitizenVillager citizen = (CitizenVillager) villager;
            if (owner.getUuid().toString().equals(citizen.ku$getOwnerUuid())
                    && CitizenVillager.ROLE_BUILDER.equals(citizen.ku$getRole())) {
                count++;
            }
        }
        return count;
    }

    /** 到着済み建築士に作業モーションとパーティクルを Periodic に再生する（Gemini 提示）。 */
    private static void playBuilderWorkAnimation(ServerWorld world, BuildData.BuildSite site,
                                                 ServerPlayerEntity owner) {
        for (net.minecraft.entity.passive.VillagerEntity villager : world.getEntitiesByClass(
                net.minecraft.entity.passive.VillagerEntity.class,
                new net.minecraft.util.math.Box(site.pos()).expand(5.0D),
                v -> v.isAlive() && v instanceof CitizenVillager)) {
            CitizenVillager citizen = (CitizenVillager) villager;
            if (!owner.getUuid().toString().equals(citizen.ku$getOwnerUuid())
                    || !CitizenVillager.ROLE_BUILDER.equals(citizen.ku$getRole())) {
                continue;
            }
            villager.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getBodyY(0.8D), villager.getZ(),
                    2, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }

    private static net.minecraft.util.math.BlockPos sanctuaryCore(net.minecraft.util.math.BlockPos origin,
                                                                  net.minecraft.util.math.Direction facing) {
        net.minecraft.util.math.BlockPos c = com.kingdomunique.world.HeroSanctuaryBuilder.CORE_OFFSET;
        int[] d = StructureRotationUtil.getBuildingDimensions(BuildingType.HERO_SANCTUARY);
        return StructureRotationUtil.transformPos(origin, c.getX(), c.getY(), c.getZ(), d[0], d[2], facing);
    }
}
