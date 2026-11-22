package com.kingdomunique.item.unique;

import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.network.TalariaJumpPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

public final class UniqueArmorEffects {
    private static final int VEIL_SNEAK_TICKS = 60;
    private static final int VEIL_COOLDOWN_TICKS = 600;
    private static final int TALARIA_COOLDOWN_TICKS = 60;
    private static final Map<ServerPlayerEntity, PlayerState> STATES = new WeakHashMap<>();

    private UniqueArmorEffects() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TalariaJumpPayload.ID,
                (payload, context) -> airJump(context.player()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken > 0 && !blocked && entity instanceof ServerPlayerEntity player) {
                breakVeil(player);
            }
            if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != entity) {
                breakVeil(attacker);
            }
        });
    }

    public static void tick(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()) {
            breakVeil(player);
            STATES.remove(player);
            return;
        }

        if (UniqueArmors.isWearing(player, UniqueArmors.SALAMANDER_BOOTS)) {
            player.extinguish();
            CooledLavaBlock.freezeAround(player);
        }

        PlayerState state = STATES.get(player);
        if (state != null && player.isOnGround()) {
            state.airJumpSpent = false;
        }
        if (!UniqueArmors.isWearing(player, UniqueArmors.HADES_HELMET)) {
            breakVeil(player);
            return;
        }
        tickVeil(player, state != null ? state : state(player));
    }

    public static boolean canAirJump(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.isOnGround()
                && !player.hasVehicle() && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isTouchingWater() && !player.isInLava() && !player.isClimbing()
                && !player.hasStatusEffect(StatusEffects.LEVITATION)
                && UniqueArmors.isWearing(player, UniqueArmors.HERMES_TALARIA)
                && !player.getItemCooldownManager().isCoolingDown(UniqueArmors.HERMES_TALARIA);
    }

    private static void airJump(ServerPlayerEntity player) {
        if (!canAirJump(player) || CurseEffects.isJumpDisabled(player)) {
            return;
        }
        PlayerState state = state(player);
        if (state.airJumpSpent) {
            return;
        }
        state.airJumpSpent = true;
        player.jump();
        player.velocityModified = true;
        player.getItemCooldownManager().set(UniqueArmors.HERMES_TALARIA, TALARIA_COOLDOWN_TICKS);
    }

    private static void tickVeil(ServerPlayerEntity player, PlayerState state) {
        VeiledPlayer veil = (VeiledPlayer) player;
        if (veil.ku$isVeiled()) {
            if (player.age % 5 == 0) {
                clearTargets(player);
            }
            return;
        }
        Vec3d previous = state.previousPosition;
        state.previousPosition = player.getPos();
        if (!player.isSneaking() || !player.isOnGround() || previous == null
                || previous.squaredDistanceTo(state.previousPosition) > 1.0E-6
                || player.getItemCooldownManager().isCoolingDown(UniqueArmors.HADES_HELMET)) {
            state.sneakTicks = 0;
            return;
        }
        if (++state.sneakTicks >= VEIL_SNEAK_TICKS) {
            veil.ku$setVeiled(true);
            clearTargets(player);
        }
    }

    public static void breakVeil(ServerPlayerEntity player) {
        PlayerState state = STATES.get(player);
        if (state != null) {
            state.sneakTicks = 0;
        }
        VeiledPlayer veil = (VeiledPlayer) player;
        if (veil.ku$isVeiled()) {
            veil.ku$setVeiled(false);
            player.getItemCooldownManager().set(UniqueArmors.HADES_HELMET, VEIL_COOLDOWN_TICKS);
        }
    }

    private static void clearTargets(ServerPlayerEntity player) {
        for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class,
                player.getBoundingBox().expand(16), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) {
                mob.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    public static int discountLevels(PlayerEntity player, int cost) {
        return cost > 0 && UniqueArmors.isWearing(player, UniqueArmors.MIMIR_HELMET)
                ? Math.max(1, (int) Math.round(cost * 0.7D)) : cost;
    }

    public static boolean isBracing(LivingEntity entity) {
        return entity instanceof PlayerEntity && entity.isSneaking()
                && UniqueArmors.isWearing(entity, UniqueArmors.JOTUN_LEGGINGS);
    }

    public static float reduceDamage(PlayerEntity player, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        if (UniqueArmors.isWearing(player, UniqueArmors.DRAGONSCALE_WINGPLATE)
                && (source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_EXPLOSION))) {
            amount *= 0.6F;
        } else if (UniqueArmors.isWearing(player, UniqueArmors.NEMEAN_LION_HIDE) && isMelee(source)) {
            amount *= 0.65F;
        }
        return isBracing(player) ? amount * 0.6F : amount;
    }

    private static boolean isMelee(DamageSource source) {
        return source.getAttacker() instanceof LivingEntity && source.getSource() == source.getAttacker()
                && (source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK)
                        || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO)
                        || source.isOf(DamageTypes.STING));
    }

    private static PlayerState state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player, key -> new PlayerState());
    }

    private static final class PlayerState {
        private boolean airJumpSpent;
        private Vec3d previousPosition;
        private int sneakTicks;
    }

    public interface VeiledPlayer {
        boolean ku$isVeiled();
        void ku$setVeiled(boolean veiled);
    }
}
