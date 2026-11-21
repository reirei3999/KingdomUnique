package com.kingdomunique.ability;

import com.kingdomunique.registry.AbilityDescriptions;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rキー／Vキーによる能動的な能力発動処理。 */
public final class AbilityActions {

    private static final Identifier GRAVITY_FALL_HALF = Identifier.of("kingdomunique", "gravity_fall_half");
    private static final long GRAVITY_MOB_HOLD_MS = 5000L;
    private static final long GRAVITY_PLAYER_HOLD_MS = 1500L;

    /** 分身UUID -> 消滅ティック。 */
    private static final Map<UUID, Long> DECOYS = new HashMap<>();

    private AbilityActions() {
    }

    /** Vキー：神匠の採掘範囲を切り替える。 */
    public static void handleModeSwitch(ServerPlayerEntity player) {
        if (!UniqueContentRegistry.has(player, UniqueContentRegistry.DIVINE_SMITH)) {
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

    /** Rキー：サーバー側で world.getTime() によるクールタイムを厳密に_checkして発動。 */
    public static void handleActivate(ServerPlayerEntity player) {
        String ability = UniqueContentRegistry.getActiveAbility(player);
        if (ability == null) {
            if (!UniqueContentRegistry.getAbilities(player).isEmpty()) {
                player.sendMessage(
                        Text.literal("§7[KingdomUnique] この能力は受動型のため発動キーは使用しません。"), true);
            }
            return;
        }
        int cooldown = AbilityDescriptions.cooldownTicks(ability);

        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = player.getServerWorld().getTime();
        long ready = state.getReadyTick(ability);
        if (now < ready) {
            float remaining = (ready - now) / 20.0F;
            player.sendMessage(Text.literal("§c[KingdomUnique] クールタイム中です（残り "
                    + String.format("%.1f", remaining) + "秒）"), true);
            return;
        }

        boolean success = switch (ability) {
            case UniqueContentRegistry.SPATIAL_LEAP -> spatialLeap(player);
            case UniqueContentRegistry.PHASE_THROUGH -> phaseThrough(player);
            case UniqueContentRegistry.SHADOW_STEP -> shadowStep(player);
            case UniqueContentRegistry.GRAVITY_CONTROL -> gravityControl(player);
            case UniqueContentRegistry.TRICKERY -> trickery(player);
            default -> false;
        };
        // 対象が見つからない等で失敗した場合はCTを消費しない。
        if (success) {
            state.setReadyTick(ability, player.getServerWorld().getTime() + cooldown);
        }
    }

    /** 空間跳躍：最大8マス先の安全な地点へ瞬間移動（5秒）。 */
    private static boolean spatialLeap(ServerPlayerEntity player) {
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
                player.sendMessage(Text.literal("§b[空間跳躍] 移動しました。"), true);
                return true;
            }
        }
        player.sendMessage(Text.literal("§c[空間跳躍] 安全な着地点が見つかりませんでした。"), true);
        return false;
    }

    /** 物質透過：厚さ3マス以内の壁を抜けて反対側へ移動（8秒）。 */
    private static boolean phaseThrough(ServerPlayerEntity player) {
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
                    player.sendMessage(Text.literal("§5[物質透過] 壁を通過しました。"), true);
                    return true;
                }
            }
        }
        player.sendMessage(Text.literal("§c[物質透過] 通過できる壁が見つかりませんでした。"), true);
        return false;
    }

    /** 影渡り：視線15マスのMobの背後にワープし、次の攻撃を1.5倍にする（10秒）。 */
    private static boolean shadowStep(ServerPlayerEntity player) {
        LivingEntity target = findLookTarget(player, 15.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§c[影渡り] 対象が見つかりませんでした。"), true);
            return false;
        }

        Vec3d targetLook = target.getRotationVec(1.0F);
        double x = target.getX() - targetLook.x * 1.5D;
        double y = target.getY();
        double z = target.getZ() - targetLook.z * 1.5D;
        player.teleport(x, y, z, false);
        AbilityState.get(player.getUuid()).setShadowDamageBuff(true);
        player.sendMessage(Text.literal("§8[影渡り] " + target.getName().getString() + " の背後に潜みました。"), true);
        return true;
    }

    /** 重力操作：敵を視線先に浮かせて拘束／再押下で投げる（15秒）。 */
    private static boolean gravityControl(ServerPlayerEntity player) {
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = System.currentTimeMillis();

        UUID targetUuid = state.getGravityTarget();
        if (targetUuid != null) {
            ServerWorld world = player.getServerWorld();
            if (world.getEntity(targetUuid) instanceof LivingEntity held) {
                AbilityPassives.releaseGravity(player, held);
            }
            state.setGravityTarget(null);
            player.sendMessage(Text.literal("§d[重力操作] 拘束を解放しました。"), true);
            return true;
        }

        LivingEntity target = findLookTarget(player, 16.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§c[重力操作] 対象が見つかりませんでした。"), true);
            return false;
        }
        if (!canLift(target)) {
            player.sendMessage(Text.literal("§c[重力操作] " + target.getName().getString()
                    + " は重すぎて持ち上げられない。"), true);
            return false;
        }

        EntityAttributeInstance instance = target.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER);
        if (instance != null) {
            instance.removeModifier(GRAVITY_FALL_HALF);
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    GRAVITY_FALL_HALF, -0.5D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        target.setNoGravity(true);
        state.setGravityTarget(target.getUuid());
        state.setGravityReleaseAt(now + (target instanceof PlayerEntity ? GRAVITY_PLAYER_HOLD_MS : GRAVITY_MOB_HOLD_MS));
        player.sendMessage(Text.literal("§d[重力操作] " + target.getName().getString() + " を拘束しました。"), true);
        return true;
    }

    /** 重力操作で持ち上げられるか（ボス級は不可）。 */
    public static boolean canLift(LivingEntity target) {
        EntityType<?> type = target.getType();
        return !type.isIn(ConventionalEntityTypeTags.BOSSES)
                && type != EntityType.ENDER_DRAGON
                && type != EntityType.WITHER
                && type != EntityType.WARDEN
                && type != EntityType.ELDER_GUARDIAN;
    }

    /** 新・奇術：視線20マス以内の対象と位置・向きを入れ替え、分身を残す（12秒）。 */
    private static boolean trickery(ServerPlayerEntity player) {
        LivingEntity target = findLookTarget(player, 20.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§c[奇術] 対象が見つかりませんでした。"), true);
            return false;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        float playerYaw = player.getYaw();
        float playerPitch = player.getPitch();
        Vec3d targetPos = target.getPos();
        float targetYaw = target.getYaw();
        float targetPitch = target.getPitch();

        // 位置と向きを入れ替える。
        player.teleport(targetPos.x, targetPos.y, targetPos.z, false);
        player.setYaw(targetYaw);
        player.setPitch(targetPitch);
        player.setHeadYaw(targetYaw);
        target.teleport(playerPos.x, playerPos.y, playerPos.z, false);
        target.setYaw(playerYaw);
        target.setPitch(playerPitch);
        if (target instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setHeadYaw(playerYaw);
        }

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
        spawnTeleportFx(world, playerPos);
        spawnTeleportFx(world, targetPos);
        spawnDecoy(world, playerPos, playerYaw, player);

        player.sendMessage(Text.literal("§d[奇術] " + target.getName().getString()
                + " と位置を入れ替えた。分身がヘイトを引き受ける。"), true);
        return true;
    }

    private static void spawnTeleportFx(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0D, pos.z,
                40, 0.6D, 1.2D, 0.6D, 0.15D);
        world.playSound(null, new BlockPos((int) pos.x, (int) pos.y, (int) pos.z),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** 元の位置に5秒間だけ残るデコイ（ArmorStand）。 */
    private static void spawnDecoy(ServerWorld world, Vec3d pos, float yaw, ServerPlayerEntity owner) {
        ArmorStandEntity decoy = EntityType.ARMOR_STAND.create(world);
        if (decoy == null) {
            return;
        }
        decoy.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
        decoy.setCustomName(Text.literal("§d[奇術] " + owner.getName().getString() + " の分身"));
        decoy.setCustomNameVisible(true);
        decoy.setInvisible(true);
        world.spawnEntity(decoy);
        DECOYS.put(decoy.getUuid(), world.getTime() + 100L);
    }

    /** 分身の期限切れを毎Tick確認して消滅させる。 */
    public static void tickDecoys(MinecraftServer server) {
        if (DECOYS.isEmpty()) {
            return;
        }
        long now = server.getWorld(World.OVERWORLD) == null ? 0L
                : server.getWorld(World.OVERWORLD).getTime();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(DECOYS).entrySet()) {
            boolean expired = now >= entry.getValue();
            for (ServerWorld world : server.getWorlds()) {
                if (world.getEntity(entry.getKey()) instanceof ArmorStandEntity decoy
                        && (expired || !decoy.isAlive())) {
                    decoy.discard();
                }
            }
            if (expired) {
                DECOYS.remove(entry.getKey());
            }
        }
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
