package com.kingdomunique.ability;

import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/** Rキー／Vキーによる能動的な能力発動処理。 */
public final class AbilityActions {

    private static final Identifier GRAVITY_FALL_HALF = Identifier.of("kingdomunique", "gravity_fall_half");

    private AbilityActions() {
    }

    /** Vキー：神匠の採掘範囲を切り替える。 */
    public static void handleModeSwitch(ServerPlayerEntity player) {
        String ability = UniqueContentRegistry.getAbility(player);
        if (!UniqueContentRegistry.DIVINE_SMITH.equals(ability)) {
            player.sendMessage(Text.literal("§7[KingdomUnique] モード切替可能な能力を持っていません。"), false);
            return;
        }
        if (!EnhancementUtil.isEnhanceable(player.getMainHandStack())) {
            player.sendMessage(Text.literal("§c[KingdomUnique] ツルハシなどの道具を持って切り替えてください。"), false);
            return;
        }

        PlayerAbilityState state = AbilityState.get(player.getUuid());
        state.cycleMiningRange();
        int range = state.getMiningRange();
        player.sendMessage(Text.literal("§6[神匠] 採掘範囲: §f" + range + "x" + range), true);
    }

    /** Rキー：所持能力に応じた発動処理。 */
    public static void handleActivate(ServerPlayerEntity player) {
        String ability = UniqueContentRegistry.getAbility(player);
        if (ability == null) {
            return;
        }

        switch (ability) {
            case UniqueContentRegistry.SPATIAL_LEAP -> spatialLeap(player);
            case UniqueContentRegistry.PHASE_THROUGH -> phaseThrough(player);
            case UniqueContentRegistry.SHADOW_STEP -> shadowStep(player);
            case UniqueContentRegistry.GRAVITY_CONTROL -> gravityControl(player);
            default -> player.sendMessage(
                    Text.literal("§7[KingdomUnique] この能力は受動型のため発動キーは使用しません。"), true);
        }
    }

    /** 空間跳躍：最大8マス先の安全な地点へ瞬間移動（クールタイム5秒）。 */
    private static void spatialLeap(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();
        if (now < state.getSpatialLeapReadyAt()) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d start = player.getEyePos();

        for (double distance = 1.0D; distance <= 8.0D; distance += 0.5D) {
            BlockPos candidate = new BlockPos(
                    (int) Math.floor(start.x + look.x * distance),
                    (int) Math.floor(start.y + look.y * distance) - 1,
                    (int) Math.floor(start.z + look.z * distance)
            );
            if (isSafe(world, candidate)) {
                player.teleport(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, false);
                state.setSpatialLeapReadyAt(now + 5000L);
                player.sendMessage(Text.literal("§b[空間跳躍] 移動しました。"), true);
                return;
            }
        }
        player.sendMessage(Text.literal("§c[空間跳躍] 安全な着地点が見つかりませんでした。"), true);
    }

    /** 物質透過：目の前の厚さ3マス以内の壁を抜けて反対側へ移動（クールタイム8秒）。 */
    private static void phaseThrough(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();
        if (now < state.getPhaseThroughReadyAt()) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d start = player.getEyePos();

        boolean foundWall = false;
        for (double distance = 0.25D; distance <= 6.0D; distance += 0.25D) {
            BlockPos pos = new BlockPos(
                    (int) Math.floor(start.x + look.x * distance),
                    (int) Math.floor(start.y + look.y * distance),
                    (int) Math.floor(start.z + look.z * distance)
            );
            boolean solid = !world.getBlockState(pos).isAir();
            if (solid && distance <= 3.0D) {
                foundWall = true;
            }
            if (foundWall && !solid && distance > 0.5D) {
                BlockPos feet = pos.down();
                if (isSafe(world, feet)) {
                    player.teleport(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, false);
                    state.setPhaseThroughReadyAt(now + 8000L);
                    player.sendMessage(Text.literal("§5[物質透過] 壁を通過しました。"), true);
                    return;
                }
            }
        }
        player.sendMessage(Text.literal("§c[物質透過] 通過できる壁が見つかりませんでした。"), true);
    }

    /** 影渡り：視線方向15マスのMobの背後にワープし、次の攻撃を1.5倍にする（10秒）。 */
    private static void shadowStep(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();
        if (now < state.getShadowStepReadyAt()) {
            return;
        }

        LivingEntity target = findLookTarget(player, 15.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§c[影渡り] 対象が見つかりませんでした。"), true);
            return;
        }

        Vec3d targetLook = target.getRotationVec(1.0F);
        double x = target.getX() - targetLook.x * 1.5D;
        double y = target.getY();
        double z = target.getZ() - targetLook.z * 1.5D;
        player.teleport(x, y, z, false);
        state.setShadowDamageBuff(true);
        state.setShadowStepReadyAt(now + 10000L);
        player.sendMessage(Text.literal("§8[影渡り] " + target.getName().getString() + " の背後に潜みました。"), true);
    }

    /** 重力操作：敵を視線先に浮かせて拘束／再押下で投げる。 */
    private static void gravityControl(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();
        if (now < state.getGravityReadyAt()) {
            return;
        }

        UUID targetUuid = state.getGravityTarget();
        if (targetUuid != null) {
            ServerWorld world = player.getServerWorld();
            if (world.getEntity(targetUuid) instanceof LivingEntity held) {
                AbilityPassives.releaseGravity(player, held);
            }
            state.setGravityTarget(null);
            state.setGravityReadyAt(now + 3000L);
            return;
        }

        LivingEntity target = findLookTarget(player, 16.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§c[重力操作] 対象が見つかりませんでした。"), true);
            return;
        }

        EntityAttributeInstance instance = target.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER);
        if (instance != null) {
            instance.removeModifier(GRAVITY_FALL_HALF);
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    GRAVITY_FALL_HALF, -0.5D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        target.setNoGravity(true);
        state.setGravityTarget(target.getUuid());
        state.setGravityReleaseAt(now + 5000L);
        state.setGravityReadyAt(now + 3000L);
        player.sendMessage(Text.literal("§d[重力操作] " + target.getName().getString() + " を拘束しました。"), true);
    }

    /** 視線方向の最も近いエンティティを探す。 */
    public static LivingEntity findLookTarget(ServerPlayerEntity player, double maxDistance) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        ServerWorld world = player.getServerWorld();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(maxDistance), e -> e != player && e.isAlive())) {
            Vec3d to = entity.getEyePos().subtract(eye);
            double length = to.length();
            if (length > maxDistance || length < 0.001D) {
                continue;
            }
            double dot = to.normalize().dotProduct(look);
            if (dot < 0.85D) {
                continue;
            }
            double score = length - dot * 4.0D;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /** 足元と頭上が非固体で、地面が存在するか。 */
    private static boolean isSafe(World world, BlockPos feet) {
        if (feet.getY() < world.getBottomY() || feet.getY() > world.getTopY() - 3) {
            return false;
        }
        return world.getBlockState(feet).isAir()
                && world.getBlockState(feet.up()).isAir()
                && !world.getBlockState(feet.down()).isAir();
    }
}
