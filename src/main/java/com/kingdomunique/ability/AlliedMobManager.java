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

    private static final String OWNER_TAG_PREFIX = "kingdomunique_ally_owner:";

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
        mob.getCommandTags().removeIf(tag -> tag.startsWith(OWNER_TAG_PREFIX));
        mob.addCommandTag("kingdomunique_ally");
        mob.addCommandTag(OWNER_TAG_PREFIX + owner.getUuid());
        mob.setPersistent();
        install(mob, owner.getUuid());
    }

    public static void restore(Entity entity) {
        if (!(entity instanceof MobEntity mob)) {
            return;
        }
        for (String tag : mob.getCommandTags()) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                try {
                    UUID ownerUuid = UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length()));
                    mob.setPersistent();
                    install(mob, ownerUuid);
                } catch (IllegalArgumentException ignored) {
                }
                return;
            }
        }
    }

    private static void install(MobEntity mob, UUID ownerUuid) {
        ALLIES.put(mob.getUuid(), ownerUuid);

        // 既存のターゲット候補を排除し、こちらのAIだけにする。
        com.kingdomunique.mixin.MobEntityAccessor accessor = (com.kingdomunique.mixin.MobEntityAccessor) mob;
        accessor.kingdomUnique$targetSelector().clear(goal -> true);
        accessor.kingdomUnique$goalSelector().clear(goal -> goal instanceof AlliedFollowGoal);
        accessor.kingdomUnique$targetSelector().add(1, new AlliedTargetGoal(mob));
        accessor.kingdomUnique$goalSelector().add(1, new AlliedFollowGoal(mob, ownerUuid));
        mob.setTarget(null);
    }

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
        nbt.putString("id", EntityType.getId(dead.getType()).toString());
        nbt.remove("UUID");
        nbt.remove("DeathTime");
        nbt.remove("HurtTime");
        Vec3d pos = dead.getPos();

        Entity revived = EntityType.loadEntityWithPassengers(nbt, world, entity -> {
            entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, dead.getYaw(), dead.getPitch());
            return entity;
        });
        if (!(revived instanceof MobEntity mob)) {
            return false;
        }

        mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * 0.5F));
        mob.setNoGravity(false);
        mob.getCommandTags().removeIf(tag -> tag.startsWith("ku_raid_"));
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
