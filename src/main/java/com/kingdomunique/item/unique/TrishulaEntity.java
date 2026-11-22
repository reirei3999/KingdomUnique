package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class TrishulaEntity extends TridentEntity {
    private boolean impacted;

    public TrishulaEntity(World world, LivingEntity owner, ItemStack stack) {
        super(world, owner, stack);
        pickupType = PickupPermission.DISALLOWED;
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
    }

    @Override
    protected boolean tryPickup(PlayerEntity player) {
        return false;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        impact(hit.getPos());
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        impact(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(0.1)));
    }

    private void impact(Vec3d position) {
        if (!impacted) {
            impacted = true;
            TrishulaEffects.impact(this, position);
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (age > 100) {
            discard();
        }
    }
}
