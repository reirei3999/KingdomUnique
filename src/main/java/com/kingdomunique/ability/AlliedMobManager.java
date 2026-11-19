package com.kingdomunique.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 絶対支配によって味方化したMobの管理。 */
public final class AlliedMobManager {

    /** Mob UUID -> 所有者 UUID。 */
    private static final Map<UUID, UUID> ALLIES = new ConcurrentHashMap<>();

    private AlliedMobManager() {
    }

    public static boolean isAlly(Entity entity) {
        return entity != null && ALLIES.containsKey(entity.getUuid());
    }

    public static UUID getOwnerUuid(Entity entity) {
        return entity == null ? null : ALLIES.get(entity.getUuid());
    }

    /** Mobを味方化し、カスタムAIを組み込む。 */
    public static void ally(MobEntity mob, ServerPlayerEntity owner) {
        ALLIES.put(mob.getUuid(), owner.getUuid());
        mob.addCommandTag("kingdomunique_ally");

        // 既存のターゲット候補を排除し、こちらのAIだけにする。
        com.kingdomunique.mixin.MobEntityAccessor accessor = (com.kingdomunique.mixin.MobEntityAccessor) mob;
        accessor.kingdomUnique$targetSelector().clear(goal -> true);
        accessor.kingdomUnique$goalSelector().add(1, new AlliedFollowGoal(mob, owner));
        mob.setTarget(null);
    }

    /** 所有者が死亡的などで切断した際の解除。 */
    public static void forgetOwner(UUID ownerUuid) {
        ALLIES.entrySet().removeIf(entry -> ownerUuid.equals(entry.getValue()));
    }

    public static void forget(UUID mobUuid) {
        ALLIES.remove(mobUuid);
    }

    /**
     * 敵対Mobを倒した際の蘇生処理。
     *
     * @return 蘇生できた場合は true
     */
    public static boolean tryRevive(HostileEntity dead, ServerPlayerEntity owner, ServerWorld world) {
        NbtCompound nbt = new NbtCompound();
        dead.writeNbt(nbt);
        Vec3d pos = dead.getPos();

        Entity revived = EntityType.loadEntityWithPassengers(nbt, world, entity -> {
            entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, dead.getYaw(), dead.getPitch());
            return entity;
        });
        if (!(revived instanceof MobEntity mob)) {
            return false;
        }

        mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * 0.5F));
        if (world.spawnEntity(mob)) {
            ally(mob, owner);
            return true;
        }
        return false;
    }

    /** 味方Mobが所有者またはプレイヤーを攻撃してよいか。 */
    public static boolean canAttackPlayer(MobEntity attacker, LivingEntity victim) {
        if (!isAlly(attacker)) {
            return true;
        }
        UUID owner = getOwnerUuid(attacker);
        if (owner != null && owner.equals(victim.getUuid())) {
            return false;
        }
        // 同国NPCはまだ実装されていないため、すべてのプレイヤーを保護する。
        return !(victim instanceof ServerPlayerEntity);
    }
}
