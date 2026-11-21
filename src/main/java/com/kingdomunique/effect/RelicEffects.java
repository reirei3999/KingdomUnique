package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.RelicRegistry;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.UUID;

/** 遺物9種のパッシブ効果（所持しているだけでも発動）。 */
public final class RelicEffects {

    private static final long DAY_TICKS = 24000L;

    private RelicEffects() {
    }

    public static boolean has(ServerPlayerEntity player, String id) {
        WorldUniqueData data = data(player);
        return data != null && data.hasId(player.getUuid(), id);
    }

    private static WorldUniqueData data(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return null;
        }
        ServerWorld world = player.getServer().getWorld(World.OVERWORLD);
        return world == null ? null : WorldUniqueData.getOrCreate(world);
    }

    public static void applyAttributes(ServerPlayerEntity player) {
        // 遺物は基本的にNPC／世界に対して作用するため、プレイヤー属性は使用しない。
    }

    /** 村の建築時間倍率（賢者の設計盤で半減）。 */
    public static double getBuildTimeMultiplier(ServerPlayerEntity player) {
        return has(player, RelicRegistry.BLUEPRINT) ? 0.5D : 1.0D;
    }

    /** 建築士NPCの作業効率倍率。 */
    public static double getBuilderEfficiencyMultiplier(ServerPlayerEntity player) {
        return has(player, RelicRegistry.BLUEPRINT) ? 2.0D : 1.0D;
    }

    /** 人口増加スピード倍率（繁栄の揺りかごで1.5倍）。 */
    public static double getPopulationGrowthMultiplier(ServerPlayerEntity player) {
        return has(player, RelicRegistry.CRADLE) ? 1.5D : 1.0D;
    }

    /** 兵士NPCの攻撃力倍率（戦勝の軍旗で1.2倍）。 */
    public static double getSoldierAttackMultiplier(ServerPlayerEntity player) {
        return getSoldierAttackMultiplierOwner(
                player.getServerWorld(), player.getUuid());
    }

    /** UUID指定で兵士の攻撃力倍率を返す（政策との合成用）。 */
    public static double getSoldierAttackMultiplierOwner(ServerWorld world, UUID owner) {
        return WorldUniqueData.getOrCreate(world).hasId(owner, RelicRegistry.BANNER) ? 1.2D : 1.0D;
    }

    /** 英雄NPCの召喚上限枠ボーナス。 */
    public static int getHeroSummonCapBonus(ServerPlayerEntity player) {
        return has(player, RelicRegistry.BANNER) ? 1 : 0;
    }

    /** 夜間襲撃ダメージカット率（守護の結界石で30%）。 */
    public static double getNightRaidDamageMultiplier(ServerPlayerEntity player) {
        return has(player, RelicRegistry.WARD_STONE) ? 0.7D : 1.0D;
    }

    /** 王国内のNPC自然回復倍率（豊穣の聖杯で2倍）。 */
    public static float getNpcHealingMultiplier(ServerPlayerEntity player) {
        return has(player, RelicRegistry.CHALICE) ? 2.0F : 1.0F;
    }

    /** 空腹ステータス効果を解除（豊穣の聖杯）。 */
    public static void tickChalice(ServerPlayerEntity player) {
        if (has(player, RelicRegistry.CHALICE)) {
            player.removeStatusEffect(StatusEffects.HUNGER);
        }
    }

    /** 王国支配範囲内の敵対スポーンを禁止するか（守護の結界石）。 */
    public static boolean isHostileSpawnBlocked(ServerPlayerEntity player) {
        return has(player, RelicRegistry.WARD_STONE);
    }

    /** サーバーTick: 黄金の天秤の1日ごとの王庫生成と職人の神槌の自動生産。 */
    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (has(player, RelicRegistry.GOLDEN_SCALE)) {
                tickGoldenScale(player);
            }
            if (has(player, RelicRegistry.HAMMER)) {
                tickHammer(player);
            }
        }
    }

    static void tickGoldenScale(ServerPlayerEntity player) {
        WorldUniqueData data = data(player);
        long day = player.getServerWorld().getTimeOfDay() / DAY_TICKS;
        if (data == null || !data.claimGoldenScalePayout(player.getUuid(), day)) {
            return;
        }

        int citizens = countCitizens(player);
        int emeralds = Math.max(1, citizens / 3);
        int ore = Math.max(1, citizens / 5);
        player.giveItemStack(new ItemStack(net.minecraft.item.Items.EMERALD, emeralds));
        player.giveItemStack(new ItemStack(net.minecraft.item.Items.IRON_INGOT, ore));
        player.sendMessage(Text.literal("§6[黄金の天秤] 国民" + citizens + "人に応じて収益が入りました。"), false);
    }

    private static void tickHammer(ServerPlayerEntity player) {
        if (player.getServerWorld().getTime() % 600L != 0L) {
            return;
        }
        if (player.getRandom().nextFloat() > 0.12F) {
            return;
        }
        player.giveItemStack(new ItemStack(net.minecraft.item.Items.ENCHANTED_BOOK));
    }

    /** 暫定の国民数（近傍の村人）。本来の王国システムで置き換える。 */
    private static int countCitizens(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(
                net.minecraft.entity.passive.VillagerEntity.class,
                player.getBoundingBox().expand(256.0D),
                villager -> villager.isAlive()).size();
    }
}
