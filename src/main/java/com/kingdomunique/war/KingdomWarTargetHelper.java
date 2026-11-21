package com.kingdomunique.war;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** 戦時中の敵対判定＆索敵ヘルパー（Gemini 提示を既存エンティティへ接続）。 */
public final class KingdomWarTargetHelper {

    private KingdomWarTargetHelper() {
    }

    /** 兵士・英雄・村人の所属王国UUIDを取得する。 */
    public static UUID getOwnerUuid(Entity entity) {
        if (entity instanceof CitizenVillager citizen) {
            String owner = citizen.ku$getOwnerUuid();
            if (owner != null && !owner.isEmpty()) {
                try {
                    return UUID.fromString(owner);
                } catch (IllegalArgumentException ignored) {
                    // 壊れたUUIDはコマンドタグへフォールバック。
                }
            }
        }
        if (entity instanceof HeroEntity hero && hero.getOwnerUuid() != null) {
            return hero.getOwnerUuid();
        }
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith("owner_")) {
                try {
                    return UUID.fromString(tag.substring("owner_".length()));
                } catch (IllegalArgumentException ignored) {
                    // 不正なタグは無視。
                }
            }
        }
        return null;
    }

    /** 候補が攻撃すべき敵か判定する（同盟・中立は誤射しない）。 */
    public static boolean isValidEnemy(ServerWorld world, LivingEntity self, UUID myKingUuid,
                                       LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || candidate == self) {
            return false;
        }
        KingdomDiplomacyState diplomacy = KingdomDiplomacyState.get(world);

        // プレイヤー（王）：戦争状態なら攻撃対象。スペクテイター/クリエイティブは除外。
        if (candidate instanceof ServerPlayerEntity targetPlayer) {
            if (targetPlayer.isCreative() || targetPlayer.isSpectator() || myKingUuid == null) {
                return false;
            }
            return diplomacy.isAtWar(myKingUuid, targetPlayer.getUuid());
        }

        // 他国の兵士・英雄：戦争状態なら攻撃対象（非戦闘員の一般国民は狙わない）。
        UUID targetOwner = getOwnerUuid(candidate);
        if (targetOwner != null) {
            if (myKingUuid == null) {
                return false;
            }
            boolean combatant = candidate instanceof HeroEntity
                    || candidate instanceof CitizenVillager c
                    && CitizenVillager.ROLE_SOLDIER.equals(c.ku$getRole());
            return combatant && diplomacy.isAtWar(myKingUuid, targetOwner);
        }

        // 敵対モンスター（絶対支配で味方化した Mob は除外）。
        if (candidate instanceof HostileEntity && !candidate.getCommandTags().contains("allied_mob")) {
            return true;
        }
        return false;
    }

    /** 戦争相手（兵士・英雄・王）をモンスターより優先して最も近い敵を返す。 */
    public static LivingEntity findBestTarget(ServerWorld world, LivingEntity self, double radius) {
        return findBestTarget(world, self, radius, candidate -> true);
    }

    public static LivingEntity findBestTarget(ServerWorld world, LivingEntity self, double radius,
                                              Predicate<LivingEntity> canTarget) {
        UUID myKingUuid = getOwnerUuid(self);
        Box box = new Box(self.getBlockPos()).expand(radius);
        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                e -> isValidEnemy(world, self, myKingUuid, e) && canTarget.test(e));

        LivingEntity bestWar = null;
        double warDist = Double.MAX_VALUE;
        LivingEntity bestMonster = null;
        double monsterDist = Double.MAX_VALUE;

        for (LivingEntity candidate : nearby) {
            double dist = self.squaredDistanceTo(candidate);
            boolean warUnit = candidate instanceof ServerPlayerEntity || getOwnerUuid(candidate) != null;
            if (warUnit) {
                if (dist < warDist) {
                    warDist = dist;
                    bestWar = candidate;
                }
            } else if (dist < monsterDist) {
                monsterDist = dist;
                bestMonster = candidate;
            }
        }
        return bestWar != null ? bestWar : bestMonster;
    }
}
