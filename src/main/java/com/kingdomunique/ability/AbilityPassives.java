package com.kingdomunique.ability;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 常時発動型の能力効果と毎Tick処理。 */
public final class AbilityPassives {

    private static final Identifier CONQUEROR_DAMAGE = id("conqueror_damage");
    private static final Identifier CONQUEROR_KNOCKBACK = id("conqueror_knockback");
    private static final Identifier GALE_SPEED = id("gale_speed");
    private static final Identifier GALE_FALL = id("gale_fall");
    private static final Identifier SITE_BREAK_SPEED = id("site_break_speed");

    public static final long INDOMITABLE_DELAY_MS = 8000L;

    private static final double FERTILITY_CHANCE_PER_COLUMN_PER_SECOND = 0.15D;

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
                abilities.contains(UniqueContentRegistry.GALE), 0.5D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, GALE_FALL,
                abilities.contains(UniqueContentRegistry.GALE), -0.8D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            List<String> abilities = UniqueContentRegistry.getAbilities(player);
            applyAttributes(player, abilities);

            if (abilities.contains(UniqueContentRegistry.INDOMITABLE)) {
                tickIndomitable(player);
            }
            if (abilities.contains(UniqueContentRegistry.GRAVITY_CONTROL)) {
                tickGravity(player);
            }
        }
        tickFertility(server);
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

    /** 豊穣：所持者の王国領土内にある畑の作物の成長を加速する（1列あたり毎秒15%）。 */
    private static void tickFertility(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        WorldUniqueData unique = WorldUniqueData.getOrCreate(world);
        Random random = world.getRandom();
        for (Map.Entry<UUID, KingdomData.KingdomInfo> entry : KingdomData.getOrCreate(world).all().entrySet()) {
            if (!unique.hasId(entry.getKey(), UniqueContentRegistry.FERTILITY)) {
                continue;
            }
            KingdomData.KingdomInfo info = entry.getValue();
            int radius = info.territoryRadius();
            int side = radius * 2 + 1;
            int samples = (int) Math.ceil(side * side * FERTILITY_CHANCE_PER_COLUMN_PER_SECOND / 20.0D);
            for (int i = 0; i < samples; i++) {
                int dx = random.nextBetween(-radius, radius);
                int dz = random.nextBetween(-radius, radius);
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = info.centerX() + dx;
                int z = info.centerZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1, z);
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    crop.applyGrowth(world, pos, state);
                }
            }
        }
    }

    /** 指定座標を領土に含む、豊穣所持者の王国（王UUID）を返す。該当なしなら null。 */
    public static UUID fertilityKingdomAt(ServerWorld world, BlockPos pos) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return null;
        }
        WorldUniqueData unique = WorldUniqueData.getOrCreate(world);
        Vec3d center = Vec3d.ofCenter(pos);
        for (UUID owner : KingdomData.getOrCreate(world).all().keySet()) {
            if (unique.hasId(owner, UniqueContentRegistry.FERTILITY)
                    && KingdomManager.isInTerritory(world, owner, center)) {
                return owner;
            }
        }
        return null;
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
        if (target instanceof ServerPlayerEntity heldPlayer && heldPlayer.networkHandler != null) {
            // プレイヤーはサーバー側の座標変更だけではクライアントへ反映されないため、テレポートで同期する。
            heldPlayer.networkHandler.requestTeleport(x, y, z, heldPlayer.getYaw(), heldPlayer.getPitch());
        } else {
            target.setPosition(x, y, z);
        }
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
        clearGravity(target);
        Vec3fHolder holder = new Vec3fHolder(player);
        target.setVelocity(holder.x * 2.0D, holder.y * 2.0D + 0.3D, holder.z * 2.0D);
        target.velocityModified = true;
    }

    /** 拘束中のプレイヤーがダメージを受けたら、投擲せずにその場で拘束を解く。 */
    public static void breakFree(ServerPlayerEntity victim) {
        for (Map.Entry<UUID, PlayerAbilityState> entry : AbilityState.all().entrySet()) {
            if (!victim.getUuid().equals(entry.getValue().getGravityTarget())) {
                continue;
            }
            entry.getValue().setGravityTarget(null);
            clearGravity(victim);
            ServerPlayerEntity holder = victim.getServer() == null ? null
                    : victim.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (holder != null) {
                holder.sendMessage(net.minecraft.text.Text.literal(
                        "§d[重力操作] " + victim.getName().getString() + " に拘束を振りほどかれました。"), true);
            }
        }
    }

    private static void clearGravity(net.minecraft.entity.LivingEntity target) {
        target.setNoGravity(false);
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
