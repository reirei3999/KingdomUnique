package com.kingdomunique.item.unique;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/** 拾えず、命中／着弾で消滅する魔槍の projectile。 */
public final class GaeBolgEntity extends TridentEntity {

    public GaeBolgEntity(World world, LivingEntity owner, ItemStack stack) {
        super(world, owner, stack);
        this.pickupType = PickupPermission.DISALLOWED;
        this.setDamage(24.0D);
    }

    @SuppressWarnings("unused")
    public GaeBolgEntity(EntityType<? extends TridentEntity> type, World world) {
        super(type, world);
        this.pickupType = PickupPermission.DISALLOWED;
    }

    @Override
    protected boolean tryPickup(PlayerEntity player) {
        return false;
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        // 何もしない（拾わせない）。
    }

    @Override
    protected void onEntityHit(EntityHitResult hitResult) {
        super.onEntityHit(hitResult);
        this.discard();
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult hitResult) {
        super.onBlockHit(hitResult);
        this.discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        this.discard();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age > 100) {
            this.discard();
        }
    }
}
