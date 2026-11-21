package com.kingdomunique.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;
import java.util.UUID;

/** 味方化Mobが戦闘中以外に所有者へ追従するGoal。 */
public final class AlliedFollowGoal extends Goal {

    private final MobEntity mob;
    private final UUID ownerUuid;
    private int counter = 0;

    public AlliedFollowGoal(MobEntity mob, UUID ownerUuid) {
        this.mob = mob;
        this.ownerUuid = ownerUuid;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    /** 所有者がオンラインかつ同じワールドにいれば返す（オフライン中は待機）。 */
    private PlayerEntity owner() {
        PlayerEntity owner = mob.getWorld().getPlayerByUuid(ownerUuid);
        return owner != null && owner.isAlive() ? owner : null;
    }

    /** 戦闘中は移動・視線をバニラの攻撃Goalへ譲る。 */
    private boolean inCombat() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canStart() {
        return owner() != null && !inCombat();
    }

    @Override
    public boolean shouldContinue() {
        return mob.isAlive() && !mob.isRemoved() && owner() != null && !inCombat();
    }

    @Override
    public void tick() {
        if (++counter < 10) {
            return;
        }
        counter = 0;
        PlayerEntity owner = owner();
        if (owner == null) {
            return;
        }

        if (mob.squaredDistanceTo(owner) > 16.0D) {
            mob.getNavigation().startMovingTo(owner, 1.1D);
            mob.lookAtEntity(owner, 30.0F, 30.0F);
        } else if (mob.getTarget() == null) {
            mob.getNavigation().stop();
        }
    }
}

final class AlliedTargetGoal extends Goal {
    private final MobEntity mob;
    private int counter;

    AlliedTargetGoal(MobEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        return true;
    }

    private boolean reachable(LivingEntity target) {
        if (!target.isAlive() || mob.squaredDistanceTo(target) > 144.0D) {
            return false;
        }
        var path = mob.getNavigation().findPathTo(target, 0);
        return path != null && path.reachesTarget();
    }

    @Override
    public void tick() {
        if (++counter < 10) {
            return;
        }
        counter = 0;
        LivingEntity current = mob.getTarget();
        if (current != null && reachable(current)) {
            return;
        }
        mob.setTarget(null);
        LivingEntity nearest = null;
        double best = 144.0D;
        for (HostileEntity hostile : mob.getWorld().getEntitiesByClass(
                HostileEntity.class,
                mob.getBoundingBox().expand(12.0D),
                h -> h.isAlive() && !AlliedMobManager.isAlly(h)
                        && reachable(h))) {
            double distance = mob.squaredDistanceTo(hostile);
            if (distance < best) {
                best = distance;
                nearest = hostile;
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
        }
    }
}
