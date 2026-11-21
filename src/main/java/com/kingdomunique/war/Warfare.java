package com.kingdomunique.war;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

/** 所属王国UUIDによる敵味方判定とヘイト操作。 */
public final class Warfare {

    private Warfare() {
    }

    /** エンティティの所属王国（王UUID）を返す。 */
    public static UUID ownerOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlayerEntity player) {
            return player.getUuid();
        }
        if (entity instanceof HeroEntity hero) {
            return hero.getOwnerUuid();
        }
        if (entity instanceof CitizenVillager citizen) {
            String uuid = citizen.ku$getOwnerUuid();
            if (uuid.isEmpty()) {
                return null;
            }
            try {
                return UUID.fromString(uuid);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (entity instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
            return tameable.getOwnerUuid();
        }
        return null;
    }

    /** 同じ王国または同盟なら友好（攻撃対象にしない）。 */
    public static boolean isFriendly(MinecraftServer server, Entity a, Entity b) {
        UUID ownerA = ownerOf(a);
        UUID ownerB = ownerOf(b);
        if (ownerA == null || ownerB == null) {
            return false;
        }
        if (ownerA.equals(ownerB)) {
            return true;
        }
        return DiplomacyData.getOrCreate(server.getWorld(World.OVERWORLD)).isAllied(ownerA, ownerB);
    }

    /** 攻撃者からダメージを受けた際、周囲の味方のヘイトを攻撃者へ向ける。 */
    public static void propagateHate(LivingEntity victim, LivingEntity attacker) {
        if (victim.getWorld().isClient()) {
            return;
        }
        ServerWorld world = (ServerWorld) victim.getWorld();
        MinecraftServer server = world.getServer();
        UUID victimOwner = ownerOf(victim);
        if (victimOwner == null) {
            return;
        }

        for (MobEntity ally : world.getEntitiesByClass(MobEntity.class,
                victim.getBoundingBox().expand(20.0D), mob -> mob.isAlive() && mob != victim)) {
            UUID allyOwner = ownerOf(ally);
            if (allyOwner == null || !allyOwner.equals(victimOwner)) {
                continue;
            }
            boolean soldier = ally instanceof CitizenVillager citizen
                    && CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole());
            boolean hero = ally instanceof HeroEntity heroEntity
                    && heroEntity.getMode() != com.kingdomunique.entity.HeroMode.WAIT;
            boolean tamed = ally instanceof net.minecraft.entity.passive.TameableEntity;
            if (soldier || hero || tamed) {
                ally.setTarget(attacker);
            }
        }
    }

    /** 騎士団長の挑発：周囲8マスの敵のターゲットを自身へ固定。 */
    public static void taunt(HeroEntity hero) {
        if (hero.getWorld().isClient()) {
            return;
        }
        for (MobEntity enemy : hero.getWorld().getEntitiesByClass(MobEntity.class,
                hero.getBoundingBox().expand(8.0D), mob -> mob.isAlive() && mob != hero)) {
            UUID enemyOwner = ownerOf(enemy);
            UUID heroOwner = hero.getOwnerUuid();
            if (enemyOwner != null && heroOwner != null && enemyOwner.equals(heroOwner)) {
                continue;
            }
            enemy.setTarget(hero);
        }
    }

    /** 刺客のステルス：対象から一定距離以上離れていればターゲットを外す。 */
    public static void applyStealth(HeroEntity assassin) {
        if (assassin.getWorld().isClient() || !assassin.isSneaking()) {
            return;
        }
        for (MobEntity enemy : assassin.getWorld().getEntitiesByClass(MobEntity.class,
                assassin.getBoundingBox().expand(16.0D), mob -> mob.isAlive())) {
            if (enemy.getTarget() == assassin && enemy.squaredDistanceTo(assassin) > 64.0D) {
                enemy.setTarget(null);
            }
        }
    }

    public static void setAlly(ServerPlayerEntity a, ServerPlayerEntity b) {
        DiplomacyData.getOrCreate(a.getServerWorld()).ally(a.getUuid(), b.getUuid());
        com.kingdomunique.kingdom.KingdomManager.refreshCitizenNameplates(a.getServerWorld(), a.getUuid(), b.getUuid());
        com.kingdomunique.kingdom.KingdomManager.refreshCitizenNameplates(b.getServerWorld(), b.getUuid(), a.getUuid());
        a.sendMessage(Text.literal("§6[外交] " + b.getName().getString() + " と同盟を結んだ。"), false);
        b.sendMessage(Text.literal("§6[外交] " + a.getName().getString() + " と同盟を結んだ。"), false);
    }

    public static void declareWar(ServerPlayerEntity a, ServerPlayerEntity b) {
        DiplomacyData.getOrCreate(a.getServerWorld()).declareWar(a.getUuid(), b.getUuid());
        com.kingdomunique.kingdom.KingdomManager.refreshCitizenNameplates(a.getServerWorld(), a.getUuid(), b.getUuid());
        com.kingdomunique.kingdom.KingdomManager.refreshCitizenNameplates(b.getServerWorld(), b.getUuid(), a.getUuid());
        a.getServer().getPlayerManager().broadcast(
                Text.literal("§c§l【宣戦布告】§r " + a.getName().getString()
                        + " が " + b.getName().getString() + " に宣戦布告した！"), false);
    }
}
