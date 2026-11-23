package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public final class KusanagiItem extends SwordItem implements UniqueWeapon {
    private static final int COOLDOWN = 200;
    private static final double RANGE = 6.0;
    private static final double CONE = Math.cos(Math.toRadians(45));

    public KusanagiItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "kusanagi";
    }

    public void slash(ServerPlayerEntity player) {
        if (player.isSpectator() || player.getItemCooldownManager().isCoolingDown(this)) {
            return;
        }
        player.getItemCooldownManager().set(this, COOLDOWN);
        ServerWorld world = player.getServerWorld();
        boolean raining = world.isRaining();
        float damage = raining ? 9.0F : 6.0F;
        Vec3d origin = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0F);

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(RANGE), target -> target != player && target.isAlive()
                        && !target.isSpectator() && !player.isTeammate(target) && target.isAttackable())) {
            Vec3d offset = target.getBoundingBox().getCenter().subtract(origin);
            if (offset.lengthSquared() > RANGE * RANGE || offset.normalize().dotProduct(direction) < CONE
                    || !player.canSee(target)) {
                continue;
            }
            if (target.damage(world.getDamageSources().playerAttack(player), damage)) {
                target.takeKnockback(raining ? 1.2 : 0.8, -offset.x, -offset.z);
            }
        }

        for (int i = -3; i <= 3; i++) {
            Vec3d point = origin.add(direction.rotateY(i * 0.17F).multiply(3.0)).subtract(0.0, 0.35, 0.0);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                player.getSoundCategory(), 1.0F, 0.8F);
    }
}
