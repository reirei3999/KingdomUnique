package com.kingdomunique.entity;

import com.kingdomunique.effect.KingdomRelicEffects;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.war.KingdomWarTargetHelper;
import com.kingdomunique.world.VillageProtection;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public final class KingdomGuardianEntity extends IronGolemEntity {
    public KingdomGuardianEntity(EntityType<? extends IronGolemEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        targetSelector.clear(goal -> true);
        goalSelector.add(2, new GoToWalkTargetGoal(this, 1.0));
        targetSelector.add(1, new ActiveTargetGoal<>(this, LivingEntity.class, false, this::isIntruder));
    }

    private boolean isIntruder(LivingEntity candidate) {
        UUID owner = KingdomWarTargetHelper.getOwnerUuid(this);
        return owner != null && candidate != this && getWorld() instanceof ServerWorld world
                && KingdomRelicEffects.isTerritoryEnemy(world, this, owner, candidate)
                && !VillageProtection.protectsFromMobDamage(candidate);
    }

    @Override
    public void tick() {
        if (isAlive() && getWorld() instanceof ServerWorld world) {
            UUID owner = KingdomWarTargetHelper.getOwnerUuid(this);
            if (owner == null || !KingdomRelicEffects.hasGuardianRelic(world, owner, getUuid())) {
                discard();
                return;
            }
            BlockPos center = KingdomManager.centerOf(world, owner);
            if (center != null) {
                setPositionTarget(center.withY(getBlockY()), KingdomManager.territoryRadius(world, owner));
            }
            if (getTarget() != null && !isIntruder(getTarget())) {
                setTarget(null);
                getNavigation().stop();
            }
        }
        super.tick();
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public void onDeath(DamageSource source) {
        recordRemoval();
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            recordRemoval();
        }
        super.remove(reason);
    }

    private void recordRemoval() {
        if (getWorld() instanceof ServerWorld world) {
            UUID owner = KingdomWarTargetHelper.getOwnerUuid(this);
            if (owner != null) {
                KingdomRelicEffects.guardianRemoved(world, owner, getUuid());
            }
        }
    }
}
