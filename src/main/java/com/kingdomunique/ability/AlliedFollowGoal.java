package com.kingdomunique.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;

/** 味方化Mobが所有者に追従し、周囲の敵を自動でターゲットするGoal。 */
public final class AlliedFollowGoal extends Goal {

    private final MobEntity mob;
    private final ServerPlayerEntity owner;
    private int counter = 0;

    public AlliedFollowGoal(MobEntity mob, ServerPlayerEntity owner) {
        this.mob = mob;
        this.owner = owner;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return mob.isAlive() && !mob.isRemoved() && owner.isAlive();
    }

    @Override
    public void tick() {
        if (++counter < 10) {
            return;
        }
        counter = 0;

        if (mob.squaredDistanceTo(owner) > 16.0D) {
            mob.getNavigation().startMovingTo(owner, 1.1D);
            mob.lookAtEntity(owner, 30.0F, 30.0F);
        } else if (mob.getTarget() == null) {
            mob.getNavigation().stop();
        }

        LivingEntity nearest = null;
        double best = 144.0D;
        for (HostileEntity hostile : mob.getWorld().getEntitiesByClass(
                HostileEntity.class,
                mob.getBoundingBox().expand(12.0D),
                h -> h.isAlive() && !AlliedMobManager.isAlly(h)
                        && !h.getUuid().equals(owner.getUuid()))) {
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
