package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PoseidonWhirlpool {
    private static final int DURATION = 60;
    private static final double RADIUS = 5.0;
    private static final Map<ServerWorld, List<PoseidonWhirlpool>> ACTIVE = new WeakHashMap<>();

    private final UUID owner;
    private final Vec3d center;
    private int age;

    private PoseidonWhirlpool(UUID owner, Vec3d center) {
        this.owner = owner;
        this.center = center;
    }

    public static void start(ServerWorld world, UUID owner, Vec3d center) {
        ACTIVE.computeIfAbsent(world, ignored -> new ArrayList<>()).add(new PoseidonWhirlpool(owner, center));
    }

    public static void tick(ServerWorld world) {
        List<PoseidonWhirlpool> whirlpools = ACTIVE.get(world);
        if (whirlpools != null) {
            whirlpools.removeIf(whirlpool -> whirlpool.advance(world));
            if (whirlpools.isEmpty()) {
                ACTIVE.remove(world);
            }
        }
    }

    private boolean advance(ServerWorld world) {
        if (age % 4 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = age * 0.17 + i * Math.PI / 4.0;
                double radius = i < 8 ? 1.4 : 3.2;
                world.spawnParticles(ParticleTypes.SPLASH,
                        center.x + radius * Math.cos(angle), center.y + 0.25,
                        center.z + radius * Math.sin(angle), 1, 0, 0, 0, 0);
            }
        }
        ServerPlayerEntity caster = world.getServer().getPlayerManager().getPlayer(owner);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(RADIUS),
                target -> target.isAlive() && !target.isSpectator()
                        && (target instanceof HostileEntity || target instanceof PlayerEntity player
                        && caster != null && player != caster && !player.isCreative() && caster.shouldDamagePlayer(player)))) {
            if (caster != null && caster.isTeammate(target)) {
                continue;
            }
            Vec3d offset = center.subtract(target.getBoundingBox().getCenter());
            if (offset.lengthSquared() > RADIUS * RADIUS
                    || world.raycast(new RaycastContext(target.getEyePos(), center, RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE, target)).getType() != HitResult.Type.MISS) {
                continue;
            }
            Vec3d pull = offset.multiply(0.16);
            target.setVelocity(pull.x, Math.max(-0.3, Math.min(0.22, pull.y)), pull.z);
            target.velocityModified = true;
            if (age % 5 == 0) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, false, false));
            }
        }
        return ++age >= DURATION;
    }
}
