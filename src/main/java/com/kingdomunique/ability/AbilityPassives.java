package com.kingdomunique.ability;

import com.kingdomunique.registry.UniqueContentRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 常時発動型の能力効果と毎Tick処理。 */
public final class AbilityPassives {

    private static final Identifier CONQUEROR_DAMAGE = id("conqueror_damage");
    private static final Identifier CONQUEROR_KNOCKBACK = id("conqueror_knockback");
    private static final Identifier GALE_SPEED = id("gale_speed");
    private static final Identifier GALE_FALL = id("gale_fall");
    private static final Identifier SITE_BLOCK_REACH = id("site_block_reach");
    private static final Identifier SITE_ENTITY_REACH = id("site_entity_reach");
    private static final Identifier SITE_BREAK_SPEED = id("site_break_speed");

    public static final long INDOMITABLE_DELAY_MS = 8000L;

    private static long tickCounter = 0L;

    private AbilityPassives() {
    }

    private static Identifier id(String path) {
        return Identifier.of("kingdomunique", path);
    }

    /** プレイヤーの保持能力に応じて属性補正を再適用する。 */
    public static void applyAttributes(ServerPlayerEntity player) {
        applyAttributes(player, UniqueContentRegistry.getAbilities(player));
    }

    private static void applyAttributes(ServerPlayerEntity player, List<String> abilities) {
        apply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, CONQUEROR_DAMAGE,
                abilities.contains(UniqueContentRegistry.CONQUEROR), 0.3D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, CONQUEROR_KNOCKBACK,
                abilities.contains(UniqueContentRegistry.CONQUEROR), 1.0D, EntityAttributeModifier.Operation.ADD_VALUE);
        apply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, GALE_SPEED,
                abilities.contains(UniqueContentRegistry.GALE), 0.2D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, GALE_FALL,
                abilities.contains(UniqueContentRegistry.GALE), -0.8D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, SITE_BLOCK_REACH,
                abilities.contains(UniqueContentRegistry.SITE_SUPERVISOR), 3.0D, EntityAttributeModifier.Operation.ADD_VALUE);
        apply(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, SITE_ENTITY_REACH,
                abilities.contains(UniqueContentRegistry.SITE_SUPERVISOR), 3.0D, EntityAttributeModifier.Operation.ADD_VALUE);
        apply(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, SITE_BREAK_SPEED,
                abilities.contains(UniqueContentRegistry.SITE_SUPERVISOR), 0.5D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void apply(ServerPlayerEntity player, RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                              Identifier modifierId, boolean enabled, double value,
                              EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierId);
        if (enabled) {
            instance.addTemporaryModifier(new EntityAttributeModifier(modifierId, value, operation));
        }
    }

    /** サーバー Tick ごとの処理（重い処理は20Tickごと）。 */
    public static void onServerTick(MinecraftServer server) {
        tickCounter++;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            List<String> abilities = UniqueContentRegistry.getAbilities(player);
            applyAttributes(player, abilities);

            if (abilities.contains(UniqueContentRegistry.INDOMITABLE)) {
                tickIndomitable(player);
            }
            if (abilities.contains(UniqueContentRegistry.GRAVITY_CONTROL)) {
                tickGravity(player);
            }
            if (tickCounter % 20L == 0L && abilities.contains(UniqueContentRegistry.FERTILITY)) {
                tickFertility(player);
            }
        }
    }

    /** 不屈：8秒無傷なら再生IVを付与し続ける。 */
    private static void tickIndomitable(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();
        if (now - state.getLastDamageTimeMillis() < INDOMITABLE_DELAY_MS) {
            player.removeStatusEffect(StatusEffects.REGENERATION);
            return;
        }
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.REGENERATION);
        if (current == null || current.getAmplifier() < 3 || current.getDuration() < 40) {
            player.setStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 3, true, true), player);
        }
    }

    /** 豊穣：半径16マスの作物の成長を加速する。 */
    private static void tickFertility(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        int radius = 16;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                if (ThreadLocalRandom.current().nextDouble() > 0.15D) {
                    continue;
                }
                BlockPos pos = center.add(dx, 0, dz);
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    crop.applyGrowth(world, pos, state);
                }
            }
        }
    }

    /** 重力操作：拘束中の敵を視線先に浮かせ、時間経過で解放する。 */
    private static void tickGravity(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        UUID targetUuid = state.getGravityTarget();
        if (targetUuid == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (!(world.getEntity(targetUuid) instanceof net.minecraft.entity.LivingEntity target) || !target.isAlive()) {
            state.setGravityTarget(null);
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= state.getGravityReleaseAt()) {
            releaseGravity(player, target);
            return;
        }

        Vec3fHolder holder = new Vec3fHolder(player);
        double x = player.getX() + holder.x * 3.0D;
        double y = player.getY() + player.getEyeHeight(player.getPose()) + holder.y * 3.0D;
        double z = player.getZ() + holder.z * 3.0D;
        target.setPosition(x, y, z);
        target.setVelocity(0.0D, 0.0D, 0.0D);
        target.setNoGravity(true);
    }

    /** 重力操作の対象を解放し、視線方向へ吹き飛ばす。 */
    public static void releaseGravity(ServerPlayerEntity player, net.minecraft.entity.LivingEntity target) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        state.setGravityTarget(null);
        if (!target.isAlive()) {
            return;
        }
        target.setNoGravity(false);
        Vec3fHolder holder = new Vec3fHolder(player);
        target.setVelocity(holder.x * 2.0D, holder.y * 2.0D + 0.3D, holder.z * 2.0D);

        EntityAttributeInstance instance = target.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER);
        if (instance != null) {
            instance.removeModifier(id("gravity_fall_half"));
        }
    }

    /** 視線ベクトルの簡易ホルダー。 */
    private static final class Vec3fHolder {
        final double x;
        final double y;
        final double z;

        Vec3fHolder(ServerPlayerEntity player) {
            net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0F);
            this.x = look.x;
            this.y = look.y;
            this.z = look.z;
        }
    }

    /** ダメージを受けた時刻を記録する（LivingEntityMixin から呼ばれる）。 */
    public static void markDamaged(ServerPlayerEntity player) {
        AbilityState.get(player.getUuid()).markDamaged(System.currentTimeMillis());
    }
}
