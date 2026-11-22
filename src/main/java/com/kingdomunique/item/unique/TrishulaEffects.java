package com.kingdomunique.item.unique;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

import java.util.Map;
import java.util.WeakHashMap;

public final class TrishulaEffects {
    private static final Map<ServerPlayerEntity, Long> PROTECTED_UNTIL = new WeakHashMap<>();

    private TrishulaEffects() {
    }

    public static boolean isProtected(ServerPlayerEntity player, DamageSource source) {
        Long until = PROTECTED_UNTIL.get(player);
        if (until == null) {
            return false;
        }
        if (player.getServer().getOverworld().getTime() >= until) {
            PROTECTED_UNTIL.remove(player);
            return false;
        }
        return source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_FALL);
    }

    public static void impact(TrishulaEntity projectile, Vec3d impact) {
        if (!(projectile.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity owner = projectile.getOwner();
        world.createExplosion(projectile, null, new ExplosionBehavior() {
            @Override
            public boolean shouldDamage(Explosion explosion, Entity entity) {
                return entity != owner && super.shouldDamage(explosion, entity);
            }

            @Override
            public float getKnockbackModifier(Entity entity) {
                return entity == owner ? 0.0F : super.getKnockbackModifier(entity);
            }
        }, impact, 2.0F, false, World.ExplosionSourceType.NONE);
        if (!(owner instanceof ServerPlayerEntity player) || !player.isAlive()
                || player.getWorld() != world) {
            return;
        }
        BlockPos origin = BlockPos.ofFloored(impact);
        for (BlockPos offset : BlockPos.iterateOutwards(BlockPos.ORIGIN, 3, 3, 3)) {
            BlockPos feet = origin.add(offset);
            Vec3d destination = Vec3d.ofBottomCenter(feet);
            if (isSafeLanding(world, player, feet)) {
                player.teleport(destination.x, destination.y, destination.z, false);
                player.setVelocity(Vec3d.ZERO);
                player.fallDistance = 0;
                player.extinguish();
                PROTECTED_UNTIL.put(player, player.getServer().getOverworld().getTime() + 100);
                return;
            }
        }
    }

    public static boolean isSafeLanding(ServerWorld world, ServerPlayerEntity player, BlockPos feet) {
        Vec3d destination = Vec3d.ofBottomCenter(feet);
        return world.getWorldBorder().contains(feet) && world.isInBuildLimit(feet)
                && world.isInBuildLimit(feet.up())
                && world.getBlockState(feet.down()).isSolidBlock(world, feet.down())
                && !isHazard(world.getBlockState(feet.down()))
                && !isHazard(world.getBlockState(feet)) && !isHazard(world.getBlockState(feet.up()))
                && world.getFluidState(feet).isEmpty() && world.getFluidState(feet.up()).isEmpty()
                && world.isSpaceEmpty(player, player.getBoundingBox().offset(destination.subtract(player.getPos())));
    }

    private static boolean isHazard(BlockState state) {
        return state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.POINTED_DRIPSTONE);
    }
}
