package com.kingdomunique.building;

import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.war.DiplomacyData;
import com.kingdomunique.war.KingdomDiplomacyState;
import com.kingdomunique.war.KingdomWarTargetHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 完成建築の内政・防衛・英雄効果（Gemini 提示を既存システム向けに調整）。 */
public final class BuildingEffectHandler {
    private static final Map<ServerWorld, Long> LAST_EVALUATED_TIMES = new WeakHashMap<>();

    private BuildingEffectHandler() {
    }

    /** 建築完了時の通知。 */
    public static void onCompleted(ServerWorld world, UUID ownerUuid, BuildingType type, BlockPos origin) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        String message = switch (type) {
            case GRANARY -> "§e[王立穀物庫] 完成！周辺の国民に食料が供給され、人口増加率が上昇します。";
            case INFIRMARY -> "§d[王立救護院] 完成！半径24マス以内の味方の自然回復が強化されます。";
            case MARKET -> "§a[王都交易場] 完成！定期的に交易収益（エメラルド）が得られます。";
            case ARSENAL -> "§b[王立軍事庫] 完成！兵士と英雄の防衛力が強化されます。";
            case TRAINING_GROUND -> "§6[英雄訓練場] 完成！周辺の英雄と兵士が定期的に強化されます。";
            case ROYAL_PALACE -> null;
            default -> null;
        };
        if (type == BuildingType.ROYAL_PALACE) {
            String name = owner != null ? owner.getName().getString() : "王";
            world.getServer().getPlayerManager().broadcast(
                    Text.literal("§6§l[建国偉業] §e" + name + " の王国に『王宮』が落成しました！"), false);
        } else if (owner != null && message != null) {
            owner.sendMessage(Text.literal(message), false);
        }
    }

    /** 完了済み建築の定期効果（5秒ごと）。 */
    public static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 100L != 0L
                    || world.getTime() == LAST_EVALUATED_TIMES.getOrDefault(world, Long.MIN_VALUE)) {
                continue;
            }
            LAST_EVALUATED_TIMES.put(world, world.getTime());
            CompletedBuildingsData data = CompletedBuildingsData.getOrCreate(world);
            for (CompletedBuildingsData.Entry entry : data.entries()) {
                Box area = new Box(entry.pos()).expand(32.0D, 16.0D, 32.0D);
                switch (entry.type()) {
                    case INFIRMARY -> applyInfirmary(world, entry.owner(), area);
                    case MARKET -> {
                        if (world.getTime() % 12000L == 0L) {
                            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.owner());
                            if (owner != null) {
                                owner.getInventory().offerOrDrop(new ItemStack(Items.EMERALD, 3));
                                owner.sendMessage(Text.literal(
                                        "§a[王都交易場] 交易の収益としてエメラルド x3 を獲得しました。"), true);
                            }
                        }
                    }
                    case GRANARY -> {
                        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.owner());
                        if (owner != null && area.contains(owner.getPos())
                                && owner.getHungerManager().getFoodLevel() < 18) {
                            owner.getHungerManager().add(2, 0.5F);
                        }
                    }
                    case ARSENAL -> applyArsenal(world, entry.owner(), area);
                    case TRAINING_GROUND -> {
                        for (LivingEntity trainee : world.getEntitiesByClass(LivingEntity.class, area,
                                e -> e.isAlive() && (e instanceof HeroEntity
                                        || e instanceof ServerPlayerEntity
                                        && e.getUuid().equals(entry.owner())))) {
                            trainee.addStatusEffect(new StatusEffectInstance(
                                    StatusEffects.STRENGTH, 120, 0, true, false));
                        }
                    }
                    case ROYAL_PALACE -> {
                        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.owner());
                        if (owner != null && area.contains(owner.getPos())) {
                            owner.addStatusEffect(new StatusEffectInstance(
                                    StatusEffects.HERO_OF_THE_VILLAGE, 140, 0, true, false));
                            owner.addStatusEffect(new StatusEffectInstance(
                                    StatusEffects.SPEED, 140, 0, true, false));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /** 救護院：範囲内で負傷している所有者陣営（本人・自国民・自国英雄・同盟国）に再生を付与する。 */
    static void applyInfirmary(ServerWorld world, UUID owner, Box area) {
        for (LivingEntity ally : world.getEntitiesByClass(LivingEntity.class, area,
                e -> receivesInfirmary(world, owner, e))) {
            if (ally.getHealth() < ally.getMaxHealth()) {
                ally.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, 120, 0, true, false));
            }
        }
    }

    /** 軍事庫：範囲内の所有者陣営の国民・英雄（同盟国を含む）に耐性を付与する。 */
    static void applyArsenal(ServerWorld world, UUID owner, Box area) {
        for (LivingEntity defender : world.getEntitiesByClass(LivingEntity.class, area,
                e -> receivesArsenal(world, owner, e))) {
            defender.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, 120, 0, true, false));
        }
    }

    static boolean receivesInfirmary(ServerWorld world, UUID owner, LivingEntity entity) {
        return entity.isAlive() && (entity instanceof ServerPlayerEntity
                || entity instanceof VillagerEntity
                || entity instanceof HeroEntity)
                && isAllyOf(world, owner, entity);
    }

    static boolean receivesArsenal(ServerWorld world, UUID owner, LivingEntity entity) {
        return entity.isAlive() && (entity instanceof VillagerEntity || entity instanceof HeroEntity)
                && isAllyOf(world, owner, entity);
    }

    /** 建築所有者と同じ王国、または戦争中でない同盟国に属するエンティティか。所属のない者は対象外。 */
    static boolean isAllyOf(ServerWorld world, UUID owner, LivingEntity entity) {
        if (owner == null) {
            return false;
        }
        UUID kingdom = entity instanceof ServerPlayerEntity player
                ? player.getUuid() : KingdomWarTargetHelper.getOwnerUuid(entity);
        if (kingdom == null) {
            return false;
        }
        if (kingdom.equals(owner)) {
            return true;
        }
        KingdomDiplomacyState diplomacy = KingdomDiplomacyState.get(world);
        if (diplomacy.isAtWar(owner, kingdom)) {
            return false;
        }
        return diplomacy.isAlly(owner, kingdom) || DiplomacyData.getOrCreate(world).isAllied(owner, kingdom);
    }
}
