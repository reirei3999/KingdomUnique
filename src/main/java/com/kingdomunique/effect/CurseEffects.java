package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.LightType;

/** 呪物9種のパッシブ効果。強力なメリットとデメリットを両立させる。 */
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

    private CurseEffects() {
    }

    private static Identifier id(String path) {
        return Identifier.of("kingdomunique", path);
    }

    public static boolean has(ServerPlayerEntity player, String id) {
        if (player.getServer() == null) {
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
        multiply(player, EntityAttributes.PLAYER_MINING_EFFICIENCY, GLASS_MINING, glass, 1.5D);
        multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, SOLITUDE_DAMAGE, solitude, 0.5D);
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SOLITUDE_SPEED, solitude, 0.2D);
        multiply(player, EntityAttributes.GENERIC_ARMOR, SOLITUDE_ARMOR, solitude, 0.5D);
        multiply(player, EntityAttributes.PLAYER_MINING_EFFICIENCY, SOLITUDE_MINING, solitude, 0.5D);
        multiply(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SHACKLES_SPEED, shackles, -0.2D);
        multiply(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, SHACKLES_DAMAGE, shackles, -0.4D);
        add(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, SHACKLES_KNOCKBACK, shackles, 1.0D);
        multiply(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, DESTRUCTION_MINING, destruction, 200.0D);

        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null && glass) {
            if (health.getBaseValue() != 10.0D) {
                health.setBaseValue(10.0D);
                player.setHealth(Math.min(player.getHealth(), 10.0F));
            }
        }
    }

    /** 虚無のランタン：明るさで補正を切り替える（毎Tick）。 */
    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyAttributes(player);
            boolean lantern = has(player, CurseRegistry.VOID_LANTERN);
            boolean dark = player.getWorld().getLightLevel(player.getBlockPos()) <= 7;
            multiply(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, VOID_DARK_DAMAGE, lantern && dark, 1.0D);
            multiply(player, EntityAttributes.GENERIC_ARMOR, VOID_LIGHT_ARMOR, lantern && !dark, -0.5D);
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
        instance.removeModifier(modifierId);
        if (enabled) {
            instance.addTemporaryModifier(new EntityAttributeModifier(modifierId, value, operation));
        }
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

    /** 混乱サイコロ：攻撃ごとの倍率（0.7〜2.7、期待値1.7）。 */
    public static double rollChaosMultiplier(ServerPlayerEntity player) {
        if (!has(player, CurseRegistry.CHAOS)) {
            return 1.0D;
        }
        return 0.7D + player.getRandom().nextDouble() * 2.0D;
    }

    /** 与ダメージの追加倍率（狂戦士は属性で既に反映済みのため1.0）。 */
    public static double outgoingMultiplier(ServerPlayerEntity player) {
        return 1.0D;
    }

    /** 状態異常を無効化するか（重力の足枷）。 */
    public static boolean blocksStatusEffects(ServerPlayerEntity player) {
        return has(player, CurseRegistry.SHACKLES);
    }

    /** 被ダメージ倍率（夜間襲撃は遺物側でも処理）。 */
    public static double incomingMultiplier(ServerPlayerEntity player) {
        return 1.0D;
    }
}
