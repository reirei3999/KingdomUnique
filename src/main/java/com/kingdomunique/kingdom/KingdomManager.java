package com.kingdomunique.kingdom;

import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.selection.SelectionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 王国データ・国民・英雄の管理ロジック。 */
public final class KingdomManager {

    /** プレイヤーUUID -> 憑依中の英雄UUID。 */
    private static final Map<UUID, UUID> POSSESSING = new HashMap<>();
    private static final int INITIAL_CAP = 10;
    private static final int MAX_CAP = 40;
    private static final long GROWTH_INTERVAL = 400L;

    private KingdomManager() {
    }

    public static KingdomData.KingdomInfo getOrCreate(ServerPlayerEntity player) {
        ServerWorld world = player.getServer().getWorld(World.OVERWORLD);
        KingdomData data = KingdomData.getOrCreate(world);
        BlockPos spawn = player.getServerWorld().getSpawnPos();
        return data.getOrCreate(player.getUuid(), spawn.getX(), spawn.getZ());
    }

    /** 住宅1軒ごとに人口上限を+5（最大40）。 */
    public static void addHouse(ServerPlayerEntity player) {
        KingdomData data = KingdomData.getOrCreate(player.getServer().getWorld(World.OVERWORLD));
        KingdomData.KingdomInfo info = getOrCreate(player);
        int houses = info.houses() + 1;
        int cap = Math.min(MAX_CAP, INITIAL_CAP + houses * 5);
        data.put(player.getUuid(), copy(info, info.population(), cap, houses,
                info.heroSummoned(), info.heroSummonCount()));
    }

    /** 建物による人口上限ボーナスを加算する（最大40）。 */
    public static void addCapBonus(ServerPlayerEntity player, int bonus) {
        KingdomData data = KingdomData.getOrCreate(player.getServer().getWorld(World.OVERWORLD));
        KingdomData.KingdomInfo info = getOrCreate(player);
        int cap = Math.min(MAX_CAP, info.populationCap() + bonus);
        data.put(player.getUuid(), copy(info, info.population(), cap, info.houses(),
                info.heroSummoned(), info.heroSummonCount()));
    }

    /** 国民数を更新し、15人到達時に遺物選択トリガーを呼ぶ。 */
    public static void setPopulation(ServerPlayerEntity player, int population) {
        KingdomData data = KingdomData.getOrCreate(player.getServer().getWorld(World.OVERWORLD));
        KingdomData.KingdomInfo info = getOrCreate(player);
        int capped = Math.min(Math.max(0, population), info.populationCap());
        data.put(player.getUuid(), copy(info, capped, info.populationCap(), info.houses(),
                info.heroSummoned(), info.heroSummonCount()));
        if (capped >= 15) {
            SelectionManager.onCitizenCountReached(player, capped);
        }
    }

    /** 既存の10村の初期村人を6人に調整する（ワールド初期化時に呼ぶ）。 */
    public static void adjustVillagePopulations(ServerWorld world) {
        for (com.kingdomunique.data.WorldUniqueData.VillageSite site
                : com.kingdomunique.data.WorldUniqueData.getOrCreate(world).getVillageSites()) {
            int centerY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, site.x(), site.z());
            BlockPos center = new BlockPos(site.x(), centerY, site.z());
            java.util.List<VillagerEntity> villagers = world.getEntitiesByClass(
                    VillagerEntity.class,
                    new net.minecraft.util.math.Box(center.getX() - 48, center.getY() - 16, center.getZ() - 48,
                            center.getX() + 48, center.getY() + 16, center.getZ() + 48),
                    v -> v.isAlive());
            int missing = 6 - villagers.size();
            for (int i = 0; i < missing; i++) {
                VillagerEntity villager = net.minecraft.entity.EntityType.VILLAGER.create(world);
                if (villager == null) {
                    continue;
                }
                double x = site.x() + ThreadLocalRandom.current().nextInt(9) - 4;
                double z = site.z() + ThreadLocalRandom.current().nextInt(9) - 4;
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                        (int) x, (int) z) + 1;
                villager.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
                world.spawnEntity(villager);
            }
            for (int i = 0; i < villagers.size() - 6 && i < villagers.size(); i++) {
                villagers.get(i).discard();
            }
        }
    }

    /** 村人を国民化する。背徳の契約書所持時はコスト0。 */
    public static void enlistVillager(ServerPlayerEntity player, LivingEntity entity, String preferredRole) {
        if (!(entity instanceof VillagerEntity villager) || !(entity instanceof CitizenVillager citizen)) {
            return;
        }
        KingdomData.KingdomInfo info = getOrCreate(player);
        if (info.population() >= info.populationCap()) {
            player.sendMessage(Text.literal("§c[王国] 人口上限（" + info.populationCap() + "人）に達しています。"), false);
            return;
        }
        if (!"".equals(citizen.ku$getOwnerUuid())) {
            return;
        }
        if (!CurseEffects.has(player, CurseRegistry.CONTRACT)) {
            if (!consume(player, Items.EMERALD, 1) && !consume(player, Items.BREAD, 2)) {
                player.sendMessage(Text.literal("§c[王国] エメラルド1個かパン2個が必要です。"), false);
                return;
            }
        }

        citizen.ku$setOwnerUuid(player.getUuid().toString());
        String role = preferredRole != null ? preferredRole : roleFromHand(player);
        citizen.ku$setRole(role);
        villager.setCustomName(Text.literal("§a[" + roleName(role) + "] 国民"));
        villager.setCustomNameVisible(true);
        villager.setPersistent();

        ItemStack held = player.getMainHandStack();
        if (CitizenVillager.ROLE_SOLDIER.equals(role) && !held.isEmpty()
                && com.kingdomunique.util.EnhancementUtil.isEnhanceable(held)) {
            villager.equipStack(EquipmentSlot.MAINHAND, held.copy());
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        setPopulation(player, info.population() + 1);
        player.sendMessage(Text.literal("§6[王国] 新しい国民（§f" + roleName(role) + "§6）が加わりました。"), false);
    }

    private static boolean consume(ServerPlayerEntity player, Item item, int count) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item) && stack.getCount() >= count) {
                stack.decrement(count);
                return true;
            }
        }
        return false;
    }

    private static String roleFromHand(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isOf(Items.WHEAT) || stack.isOf(Items.CARROT)) {
            return CitizenVillager.ROLE_FARMER;
        }
        if (stack.isOf(Items.OAK_PLANKS) || stack.isOf(Items.STONE)) {
            return CitizenVillager.ROLE_BUILDER;
        }
        if (stack.isOf(Items.IRON_INGOT) || stack.isOf(Items.DIAMOND)) {
            return CitizenVillager.ROLE_SMITH;
        }
        if (com.kingdomunique.util.EnhancementUtil.isEnhanceable(stack)) {
            return CitizenVillager.ROLE_SOLDIER;
        }
        return CitizenVillager.ROLE_FARMER;
    }

    private static String roleName(String role) {
        return switch (role) {
            case CitizenVillager.ROLE_BUILDER -> "建築士";
            case CitizenVillager.ROLE_SMITH -> "鍛冶屋";
            case CitizenVillager.ROLE_SOLDIER -> "兵士";
            default -> "農民";
        };
    }

    /** 国民の自然増加（ベッド・食料充足度と繁栄の揺りかごを反映）。 */
    public static void tickGrowth(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            KingdomData.KingdomInfo info = getOrCreate(player);
            if (info.population() >= info.populationCap()) {
                continue;
            }
            if (player.getServerWorld().getTime() % GROWTH_INTERVAL != 0L) {
                continue;
            }
            double rate = RelicEffects.getPopulationGrowthMultiplier(player);
            if (ThreadLocalRandom.current().nextDouble() > 0.25D * rate) {
                continue;
            }
            setPopulation(player, info.population() + 1);
        }
    }

    /** 国民死亡時の背徳の契約書ペナルティ。 */
    public static void onCitizenDeath(ServerPlayerEntity owner) {
        if (!CurseEffects.has(owner, CurseRegistry.CONTRACT)) {
            return;
        }
        float curse = Math.max(owner.getHealth(), owner.getMaxHealth()) / 2.0F;
        owner.damage(owner.getDamageSources().magic(), curse);
    }

    /** 英雄召喚（1王国1回、軍旗で2回まで）。 */
    public static void summonHero(ServerPlayerEntity player) {
        KingdomData data = KingdomData.getOrCreate(player.getServer().getWorld(World.OVERWORLD));
        KingdomData.KingdomInfo info = getOrCreate(player);
        int max = RelicEffects.has(player, com.kingdomunique.registry.RelicRegistry.BANNER) ? 2 : 1;
        if (info.heroSummonCount() >= max) {
            player.sendMessage(Text.literal("§c[英雄] この王国ではこれ以上召喚できません。"), false);
            return;
        }

        HeroClass[] classes = HeroClass.values();
        HeroClass chosen = classes[ThreadLocalRandom.current().nextInt(classes.length)];
        ServerWorld world = player.getServerWorld();
        HeroEntity hero = ModEntities.HERO.create(world);
        if (hero == null) {
            return;
        }
        double x = player.getX() + player.getRotationVec(1.0F).x * 2.0D;
        double z = player.getZ() + player.getRotationVec(1.0F).z * 2.0D;
        hero.refreshPositionAndAngles(x, player.getY(), z, player.getYaw(), 0.0F);
        hero.setOwnerUuid(player.getUuid());
        hero.setHeroClass(chosen);
        world.spawnEntity(hero);

        world.setWeather(0, 200, true, true);
        player.getServer().getPlayerManager().broadcast(
                Text.literal("§6§l【王国の英雄降臨】§r " + player.getName().getString()
                        + " が「" + chosen.getDisplayName() + "」を召喚した！"), false);

        data.put(player.getUuid(), copy(info, info.population(), info.populationCap(), info.houses(),
                true, info.heroSummonCount() + 1));
    }

    /** Gキー／オーブによる刺客英雄への憑依切替。 */
    public static void togglePossession(ServerPlayerEntity player) {
        UUID current = POSSESSING.get(player.getUuid());
        if (current != null) {
            player.setCameraEntity(player);
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
            POSSESSING.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[憑依] 本体へ意識を戻しました。"), true);
            return;
        }
        Entity found = null;
        for (Entity entity : player.getWorld().getEntitiesByClass(HeroEntity.class,
                player.getBoundingBox().expand(16.0D), h -> h.isAlive()
                        && player.getUuid().equals(h.getOwnerUuid())
                        && h.getHeroClass() == HeroClass.ASSASSIN)) {
            found = entity;
            break;
        }
        if (found == null) {
            player.sendMessage(Text.literal("§c[憑依] 近くに刺客英雄がいません。"), true);
            return;
        }
        POSSESSING.put(player.getUuid(), found.getUuid());
        player.setCameraEntity(found);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 200000, 0, true, false));
        player.sendMessage(Text.literal("§8[憑依] " + found.getName().getString() + " に乗り移りました。Gで解除。"), true);
    }

    public static HeroEntity possessedHero(ServerPlayerEntity player) {
        UUID uuid = POSSESSING.get(player.getUuid());
        if (uuid == null) {
            return null;
        }
        if (!(player.getServerWorld().getEntity(uuid) instanceof HeroEntity hero) || !hero.isAlive()) {
            POSSESSING.remove(player.getUuid());
            player.setCameraEntity(player);
            return null;
        }
        return hero;
    }

    public static void clearPossession(ServerPlayerEntity player) {
        POSSESSING.remove(player.getUuid());
    }

    private static KingdomData.KingdomInfo copy(KingdomData.KingdomInfo info, int population, int cap,
                                                int houses, boolean summoned, int count) {
        return new KingdomData.KingdomInfo(info.centerX(), info.centerZ(), population, cap,
                houses, summoned, count);
    }
}
