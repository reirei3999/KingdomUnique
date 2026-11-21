package com.kingdomunique.ability;

import net.minecraft.entity.Entity;
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

    private static final String ALLY_TAG = "kingdomunique_ally";
    /** 所有者UUIDをエンティティのコマンドタグへ保存し、再起動後も復元できるようにする。 */
    private static final String OWNER_TAG_PREFIX = "kingdomunique_ally_owner:";
    private static final String RAID_TAG_PREFIX = "ku_raid_";

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
        mob.addCommandTag(ALLY_TAG);
        mob.addCommandTag(OWNER_TAG_PREFIX + owner.getUuid());
        mob.setPersistent();
        install(mob, owner.getUuid());
    }

    /** ワールドへ読み込まれたMobが味方タグを持っていれば、所有者とAIを復元する。 */
    public static void restore(Entity entity) {
        if (!(entity instanceof MobEntity mob)) {
            return;
        }
        for (String tag : mob.getCommandTags()) {
            if (!tag.startsWith(OWNER_TAG_PREFIX)) {
                continue;
            }
            try {
                install(mob, UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length())));
            } catch (IllegalArgumentException ignored) {
                // 壊れたタグは無視する。
            }
            return;
        }
    }

    private static void install(MobEntity mob, UUID ownerUuid) {
        ALLIES.put(mob.getUuid(), ownerUuid);

        // 既存のターゲット候補を排除し、こちらのAIだけにする。
        com.kingdomunique.mixin.MobEntityAccessor accessor = (com.kingdomunique.mixin.MobEntityAccessor) mob;
        accessor.kingdomUnique$targetSelector().clear(goal -> true);
        accessor.kingdomUnique$goalSelector().clear(goal -> goal instanceof AlliedFollowGoal);
        accessor.kingdomUnique$goalSelector().add(1, new AlliedFollowGoal(mob, ownerUuid));
        mob.setTarget(null);
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
        // 死体はまだワールドに残っているため、別UUIDの新しい個体として蘇らせる。
        nbt.remove("UUID");
        nbt.remove("DeathTime");
        nbt.remove("HurtTime");
        Vec3d pos = dead.getPos();

        if (!(dead.getType().create(world) instanceof MobEntity mob)) {
            return false;
        }
        mob.readNbt(nbt);
        // 襲撃軍への所属は引き継がない（防衛完了判定を妨げないため）。
        mob.getCommandTags().removeIf(tag -> tag.startsWith(RAID_TAG_PREFIX));
        mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, dead.getYaw(), dead.getPitch());
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
