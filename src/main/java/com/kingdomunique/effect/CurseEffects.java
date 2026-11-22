package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.war.Warfare;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** 呪物の効果と代償。 */
public final class CurseEffects {

    private static final Identifier BERSERKER_DAMAGE = id("berserker_damage");
    private static final Identifier GLASS_DAMAGE = id("glass_damage");
    private static final Identifier GLASS_MINING = id("glass_mining");
    private static final Identifier SOLITUDE_DAMAGE = id("solitude_damage");
    private static final Identifier SOLITUDE_SPEED = id("solitude_speed");
    private static final Identifier SOLITUDE_ARMOR = id("solitude_armor");
    private static final Identifier SOLITUDE_MINING = id("solitude_mining");
    private static final Identifier SHACKLES_SPEED = id("shackles_speed");
    private static final Identifier SHACKLES_DAMAGE = id("shackles_damage");
    private static final Identifier SHACKLES_KNOCKBACK = id("shackles_knockback");
    private static final Identifier VOID_DARK_DAMAGE = id("void_dark_damage");
    private static final Identifier VOID_LIGHT_ARMOR = id("void_light_armor");
    private static final Identifier DESTRUCTION_MINING = id("destruction_mining");
    private static final Identifier GLUTTONY_DAMAGE = id("gluttony_damage");
    private static final Identifier VAMPIRE_SPEED = id("vampire_speed");
    private static final Identifier REAPER_HEALTH = id("reaper_health");
    private static final Identifier REAPER_DAMAGE = id("reaper_damage");
    private static final Identifier MAD_MOON_DAMAGE = id("mad_moon_damage");
    private static final Identifier MAD_MOON_SPEED = id("mad_moon_speed");
    private static final Identifier SEALING_KNOCKBACK = id("sealing_knockback");
    /** 暴食の指輪：撃破後の攻撃力上昇（+50%）。 */
    public static final double GLUTTONY_DAMAGE_BONUS = 0.5D;

    private CurseEffects() {
    }

    private static Identifier id(String path) {
        return Identifier.of("kingdomunique", path);
    }

    public static boolean has(ServerPlayerEntity player, String id) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        ServerWorld world = player.getServer().getWorld(World.OVERWORLD);
        if (world == null) {
            return false;
        }
        return WorldUniqueData.getOrCreate(world).hasId(player.getUuid(), id);
    }

    public static void applyAttributes(ServerPlayerEntity player) {
        boolean berserker = has(player, CurseRegistry.BERSERKER);
        boolean glass = has(player, CurseRegistry.GLASS_CANNON);
        boolean solitude = has(player, CurseRegistry.SOLITUDE);
        boolean shackles = has(player, CurseRegistry.SHACKLES);
        boolean destruction = has(player, CurseRegistry.DESTRUCTION);

        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, BERSERKER_DAMAGE, berserker, 0.6D);
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, GLASS_DAMAGE, glass, 1.5D);
        multiply(player, EntityAttributes.PLAYER_MINING_EFFICIENCY, GLASS_MINING, false, 0.0D);
        multiply(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, GLASS_MINING, glass, 4.0D);
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, SOLITUDE_DAMAGE, solitude, 0.5D);
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SOLITUDE_SPEED, solitude, 0.2D);
        multiply(player, EntityAttributes.GENERIC_ARMOR, SOLITUDE_ARMOR, solitude, 0.5D);
        multiply(player, EntityAttributes.PLAYER_MINING_EFFICIENCY, SOLITUDE_MINING, false, 0.0D);
        multiply(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, SOLITUDE_MINING, solitude, 2.0D);
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SHACKLES_SPEED, shackles, -0.2D);
        multiply(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, SHACKLES_DAMAGE, false, 0.0D);
        add(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, SHACKLES_KNOCKBACK, shackles, 1.0D);
        multiply(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, DESTRUCTION_MINING, destruction, 401.0D);

        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null && glass) {
            if (health.getBaseValue() != 10.0D) {
                health.setBaseValue(10.0D);
                player.setHealth(Math.min(player.getHealth(), 10.0F));
            }
        }

        boolean reaper = has(player, CurseRegistry.REAPER_HOURGLASS);
        multiply(player, EntityAttributes.GENERIC_MAX_HEALTH, REAPER_HEALTH, reaper, -0.3D);
        if (reaper && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        double missingHealth = (player.getMaxHealth() - player.getHealth())
                / Math.max(1.0D, player.getMaxHealth() - 1.0D);
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, REAPER_DAMAGE, reaper,
                1.5D * MathHelper.clamp(missingHealth, 0.0D, 1.0D));

        ServerWorld world = player.getServerWorld();
        long dayTime = Math.floorMod(world.getTimeOfDay(), 24000L);
        boolean hasDayCycle = world.getDimension().hasSkyLight() && !world.getDimension().hasFixedTime();
        boolean night = dayTime >= 13000L && dayTime < 23000L;
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, VAMPIRE_SPEED,
                hasDayCycle && night && has(player, CurseRegistry.VAMPIRE_FANG), 0.3D);
        boolean moon = hasDayCycle && has(player, CurseRegistry.MAD_MOON_NECKLACE);
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MAD_MOON_DAMAGE, moon, night ? 0.5D : -0.3D);
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MAD_MOON_SPEED, moon && night, 0.3D);
        add(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, SEALING_KNOCKBACK, abilitiesSealed(player), 1.0D);
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyAttributes(player);
            boolean lantern = has(player, CurseRegistry.VOID_LANTERN);
            boolean dark = player.getWorld().getLightLevel(player.getBlockPos()) <= 7;
            multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, VOID_DARK_DAMAGE, lantern && dark, 1.0D);
            multiply(player, EntityAttributes.GENERIC_ARMOR, VOID_LIGHT_ARMOR, lantern && !dark, -0.5D);
            tickSunlight(player);
        }
    }

    private static void multiply(ServerPlayerEntity player,
                                 net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                                 Identifier modifierId, boolean enabled, double value) {
        change(player, attribute, modifierId, enabled, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void add(ServerPlayerEntity player,
                            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                            Identifier modifierId, boolean enabled, double value) {
        change(player, attribute, modifierId, enabled, value, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private static void change(ServerPlayerEntity player,
                               net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                               Identifier modifierId, boolean enabled, double value,
                               EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier previous = instance.getModifier(modifierId);
        if (previous != null) {
            if (enabled && previous.value() == value && previous.operation() == operation) {
                return;
            }
            instance.removeModifier(modifierId);
        }
        if (enabled) {
            instance.addTemporaryModifier(new EntityAttributeModifier(modifierId, value, operation));
        }
    }

    /** 暴食の指輪：撃破後の一時的な攻撃力上昇を付け外しする。 */
    public static void applyGluttonyFrenzy(ServerPlayerEntity player, boolean active) {
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, GLUTTONY_DAMAGE, active, GLUTTONY_DAMAGE_BONUS);
    }

    /** 虚空のランタン：透明化判定用。 */
    public static boolean isInvisibleInDark(ServerPlayerEntity player) {
        return has(player, CurseRegistry.VOID_LANTERN)
                && player.getWorld().getLightLevel(player.getBlockPos()) <= 7;
    }

    /** 重力の足枷：ジャンプ不可。 */
    public static boolean isJumpDisabled(ServerPlayerEntity player) {
        return has(player, CurseRegistry.SHACKLES);
    }

    /** 混乱サイコロ：攻撃ごとの倍率（0.2〜2.7、期待値1.45）。 */
    public static double rollChaosMultiplier(ServerPlayerEntity player) {
        if (!has(player, CurseRegistry.CHAOS)) {
            return 1.0D;
        }
        return 0.2D + player.getRandom().nextDouble() * 2.5D;
    }

    /** 与ダメージの追加倍率（狂戦士は属性で既に反映済みのため1.0）。 */
    public static double outgoingMultiplier(ServerPlayerEntity player) {
        return 1.0D;
    }

    /** 状態異常を無効化するか（重力の足枷）。 */
    public static boolean blocksStatusEffects(ServerPlayerEntity player) {
        return has(player, CurseRegistry.SHACKLES);
    }

    static void tickSunlight(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || !has(player, CurseRegistry.VAMPIRE_FANG) || !world.isDay()
                || !world.getDimension().hasSkyLight() || player.isWet()) {
            return;
        }
        BlockPos eyes = BlockPos.ofFloored(player.getEyePos());
        if (!world.isSkyVisible(eyes) || world.getPhototaxisFavor(eyes) <= 0.0F) {
            return;
        }
        ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            if (world.getTime() % 20L == 0L) {
                helmet.damage(1, player, EquipmentSlot.HEAD);
            }
        } else {
            player.setOnFireForTicks(40);
        }
    }

    public static void afterDamage(LivingEntity victim, DamageSource source, float healthBefore) {
        if (victim.getWorld().isClient() || source.isOf(DamageTypes.THORNS)
                || !(source.getAttacker() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        float healthLost = healthBefore - victim.getHealth();
        if (healthLost <= 0.0F) {
            return;
        }
        if (attacker instanceof ServerPlayerEntity player && has(player, CurseRegistry.VAMPIRE_FANG)) {
            player.heal(healthLost * 0.25F);
        }
        if (victim instanceof ServerPlayerEntity player && has(player, CurseRegistry.ENVY_MIRROR)
                && attacker.isAlive()) {
            attacker.damage(new EnvyReflectionDamage(player), healthLost * 0.3F);
        }
    }

    public static boolean isEnvyReflection(DamageSource source) {
        return source instanceof EnvyReflectionDamage;
    }

    private static final class EnvyReflectionDamage extends DamageSource {
        private EnvyReflectionDamage(ServerPlayerEntity player) {
            super(player.getDamageSources().thorns(player).getTypeRegistryEntry(), player);
        }
    }

    public static boolean blocksAlliedAid(ServerPlayerEntity player, Entity source) {
        if (!has(player, CurseRegistry.ENVY_MIRROR)) {
            return false;
        }
        if (source instanceof AreaEffectCloudEntity cloud) {
            source = cloud.getOwner();
        }
        return source != null && source != player
                && (player.isTeammate(source) || Warfare.isFriendly(player.getServerWorld().getServer(), source, player));
    }

    public static float healingMultiplier(ServerPlayerEntity player) {
        return has(player, CurseRegistry.REAPER_HOURGLASS) ? 0.5F : 1.0F;
    }

    public static PlayerEntity.SleepFailureReason checkSleep(PlayerEntity player, BlockPos sleepingPos) {
        if (player instanceof ServerPlayerEntity serverPlayer && has(serverPlayer, CurseRegistry.MAD_MOON_NECKLACE)) {
            serverPlayer.sendMessage(Text.literal("§5[狂月の首飾り] 呪いで眠れません。"), true);
            return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
        }
        return null;
    }

    public static boolean abilitiesSealed(ServerPlayerEntity player) {
        return player != null && has(player, CurseRegistry.SEALING_CHAIN);
    }

    /** 被ダメージ倍率（夜間襲撃は遺物側でも処理）。 */
    public static double incomingMultiplier(ServerPlayerEntity player) {
        return (has(player, CurseRegistry.SHACKLES) ? 0.4D : 1.0D) * (abilitiesSealed(player) ? 0.5D : 1.0D);
    }
}
