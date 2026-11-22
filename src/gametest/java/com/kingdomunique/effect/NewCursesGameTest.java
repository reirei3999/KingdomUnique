package com.kingdomunique.effect;

import com.kingdomunique.ability.AbilityActions;
import com.kingdomunique.ability.AbilityPassives;
import com.kingdomunique.ability.AbilityState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.selection.SelectionManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class NewCursesGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_curses")
    public void vampireFangHealsForHealthLostAndBurnsInSun(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var data = claim(context, player, CurseRegistry.VAMPIRE_FANG);
        long originalTime = world.getTimeOfDay();
        try {
            var target = context.spawnEntity(EntityType.PIG, 1, 2, 2);
            target.setHealth(4.0F);
            player.setHealth(10.0F);
            target.damage(world.getDamageSources().playerAttack(player), 20.0F);
            assertNear(context, player.getHealth(), 11.0D, "Lethal overkill only drains the 4 HP actually lost");

            double baseSpeed = player.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            world.setTimeOfDay(18000L);
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    baseSpeed * 1.3D, "Night movement speed");

            world.setTimeOfDay(6000L);
            world.calculateAmbientDarkness();
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    baseSpeed, "Daylight removes the speed bonus");
            // 共有テストワールドでは他テストの建造物が頭上を塞ぐことがあるため、確実に空が見える高さで検証する。
            player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 0, 1))
                    .withY(world.getTopY() - 4), 0.0F, 0.0F);
            context.assertTrue(world.isSkyVisible(BlockPos.ofFloored(player.getEyePos())),
                    "The sunlight check position must see the sky");
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            CurseEffects.tickSunlight(player);
            context.assertTrue(!player.isOnFire(), "A helmet protects against sunlight");
            player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            CurseEffects.tickSunlight(player);
            context.assertTrue(player.isOnFire(), "Direct sunlight ignites an unprotected holder");
        } finally {
            world.setTimeOfDay(originalTime);
            world.calculateAmbientDarkness();
            data.releaseId(player.getUuid(), CurseRegistry.VAMPIRE_FANG);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_curses")
    public void reaperHourglassScalesAttackAndHalvesHealing(TestContext context) {
        var player = player(context);
        var data = claim(context, player, CurseRegistry.REAPER_HOURGLASS);
        try {
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getMaxHealth(), 14.0D, "Maximum health is reduced by 30%");
            assertNear(context, player.getHealth(), 14.0D, "Current health is clamped");
            double baseAttack = player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                    baseAttack, "Full health does not boost attack");

            player.setHealth(1.0F);
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                    baseAttack * 2.5D, "Attack reaches x2.5 at half a heart");
            player.heal(4.0F);
            assertNear(context, player.getHealth(), 3.0D, "Healing is halved");
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.REAPER_HOURGLASS);
        }
        CurseEffects.applyAttributes(player);
        assertNear(context, player.getMaxHealth(), 20.0D, "Losing the curse restores the health limit");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_curses")
    public void envyMirrorReflectsDamageAndRejectsOnlyFriendlyAid(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var data = claim(context, player, CurseRegistry.ENVY_MIRROR);
        UUID shadowOwner = data.getOwner(UniqueContentRegistry.SHADOW_STEP);
        try {
            claimAbility(context, data, player, UniqueContentRegistry.SHADOW_STEP, shadowOwner);
            var abilityState = AbilityState.get(player.getUuid());
            abilityState.setShadowDamageBuff(true);
            var attacker = context.spawnEntity(EntityType.PIG, 1, 2, 2);
            var citizen = context.spawnEntity(EntityType.VILLAGER, 1, 2, 3);
            ((CitizenVillager) citizen).ku$setOwnerUuid(player.getUuid().toString());
            player.damage(world.getDamageSources().mobAttack(attacker), 10.0F);
            assertNear(context, attacker.getHealth(), 7.0D, "The attacker takes 30% of the HP lost");
            context.assertTrue(abilityState.isShadowDamageBuff(), "Reflection does not consume Shadow Step");
            var attackTarget = context.spawnEntity(EntityType.PIG, 2, 2, 2);
            attackTarget.damage(world.getDamageSources().playerAttack(player), 4.0F);
            assertNear(context, attackTarget.getHealth(), 4.0D, "The next attack still gets the Shadow Step bonus");
            context.assertTrue(!abilityState.isShadowDamageBuff(), "An actual attack consumes Shadow Step");
            player.timeUntilRegen = 0;
            player.damage(world.getDamageSources().thorns(attacker), 1.0F);
            assertNear(context, attacker.getHealth(), 7.0D, "Thorns damage cannot bounce again");

            context.assertTrue(!player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100), citizen),
                    "A friendly strength effect is blocked");
            var cloud = new AreaEffectCloudEntity(world, player.getX(), player.getY(), player.getZ());
            cloud.setOwner(citizen);
            context.assertTrue(!player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100), cloud),
                    "A friendly lingering potion is still sourced to its owner");
            context.assertTrue(player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100), citizen),
                    "A harmful effect is not blocked");
            context.assertTrue(player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100), player),
                    "A self-applied buff still works");
            double health = player.getHealth();
            StatusEffects.INSTANT_HEALTH.value().applyInstantEffect(citizen, citizen, player, 0, 1.0D);
            assertNear(context, player.getHealth(), health, "Sourced instant healing from a citizen is blocked");
            StatusEffects.INSTANT_HEALTH.value().applyInstantEffect(player, player, player, 0, 1.0D);
            assertNear(context, player.getHealth(), health + 4.0D, "Self-applied instant healing still works");
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.ENVY_MIRROR);
            restoreAbility(data, player, UniqueContentRegistry.SHADOW_STEP, shadowOwner);
            AbilityState.remove(player.getUuid());
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_curses")
    public void madMoonNecklaceSwitchesAtNightAndBlocksBeds(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var data = claim(context, player, CurseRegistry.MAD_MOON_NECKLACE);
        long originalTime = world.getTimeOfDay();
        try {
            double baseSpeed = player.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            double baseAttack = player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            world.setTimeOfDay(6000L);
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                    baseAttack * 0.7D, "Daytime attack penalty");
            world.setTimeOfDay(18000L);
            world.calculateAmbientDarkness();
            CurseEffects.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                    baseAttack * 1.5D, "Night attack bonus");
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    baseSpeed * 1.3D, "Night movement bonus");

            BlockPos bed = context.getAbsolutePos(new BlockPos(2, 2, 1));
            world.setBlockState(bed, Blocks.RED_BED.getDefaultState());
            player.trySleep(bed);
            context.assertTrue(!player.isSleeping() && player.lastMessage != null
                    && player.lastMessage.getString().contains("呪いで眠れません"), "The curse denies the bed interaction");
        } finally {
            world.setTimeOfDay(originalTime);
            world.calculateAmbientDarkness();
            data.releaseId(player.getUuid(), CurseRegistry.MAD_MOON_NECKLACE);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_curses")
    public void sealingChainReducesDamageWithoutRemovingAbilityOwnership(TestContext context) {
        var player = player(context);
        var data = claim(context, player, CurseRegistry.SEALING_CHAIN);
        UUID galeOwner = data.getOwner(UniqueContentRegistry.GALE);
        UUID leapOwner = data.getOwner(UniqueContentRegistry.SPATIAL_LEAP);
        try {
            claimAbility(context, data, player, UniqueContentRegistry.GALE, galeOwner);
            claimAbility(context, data, player, UniqueContentRegistry.SPATIAL_LEAP, leapOwner);
            CurseEffects.applyAttributes(player);
            AbilityPassives.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE),
                    1.0D, "The chain grants full knockback resistance");
            player.damage(context.getWorld().getDamageSources().generic(), 8.0F);
            assertNear(context, player.getHealth(), 16.0D, "The chain halves damage");
            context.assertTrue(UniqueContentRegistry.getAbilities(player).contains(UniqueContentRegistry.GALE)
                    && UniqueContentRegistry.getEnabledAbilities(player).isEmpty(), "Owned abilities remain sealed");
            AbilityActions.handleActivate(player);
            context.assertTrue(player.lastMessage != null && player.lastMessage.getString().contains("封印されています"),
                    "Manual activation is denied");
            data.releaseId(player.getUuid(), CurseRegistry.SEALING_CHAIN);
            AbilityPassives.applyAttributes(player);
            assertNear(context, player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                    player.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 1.2D,
                    "Gale returns after losing the chain");
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.SEALING_CHAIN);
            restoreAbility(data, player, UniqueContentRegistry.SPATIAL_LEAP, leapOwner);
            restoreAbility(data, player, UniqueContentRegistry.GALE, galeOwner);
        }
        context.complete();
    }

    private static WorldUniqueData claim(TestContext context, ServerPlayerEntity player, String id) {
        context.assertTrue(ContentRegistry.kindOf(id) == ContentRegistry.Kind.CURSE, id + " is registered as a curse");
        context.assertTrue(SelectionManager.CURSE_OPTIONS == 5, "The curse offer still has five cards");
        var data = WorldUniqueData.getOrCreate(context.getWorld().getServer().getOverworld());
        context.assertTrue(data.claimId(player.getUuid(), id), id + " must be claimable");
        return data;
    }

    private static void assertNear(TestContext context, double actual, double expected, String reason) {
        context.assertTrue(Math.abs(actual - expected) < 1.0E-5D, reason + ": expected " + expected + ", got " + actual);
    }

    private static void claimAbility(TestContext context, WorldUniqueData data, ServerPlayerEntity player,
                                     String id, UUID previousOwner) {
        boolean claimed = previousOwner == null ? data.claimId(player.getUuid(), id)
                : data.transferId(previousOwner, player.getUuid(), id);
        context.assertTrue(claimed, id + " must be available to the test player");
    }

    private static void restoreAbility(WorldUniqueData data, ServerPlayerEntity player, String id, UUID previousOwner) {
        if (previousOwner == null) {
            data.releaseId(player.getUuid(), id);
        } else {
            data.transferId(player.getUuid(), previousOwner, id);
        }
    }

    private static TestPlayer player(TestContext context) {
        var profile = new GameProfile(UUID.randomUUID(), "curse_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new TestPlayer(context, profile, client);
        var world = context.getWorld();
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        // Expire vanilla join protection before testing player damage.
        for (int i = 0; i < 60; i++) {
            player.tick();
        }
        return player;
    }

    private static final class TestPlayer extends ServerPlayerEntity {
        private Text lastMessage;

        private TestPlayer(TestContext context, GameProfile profile, ConnectedClientData client) {
            super(context.getWorld().getServer(), context.getWorld(), profile, client.syncedOptions());
        }

        @Override
        public void sendMessage(Text message, boolean overlay) {
            lastMessage = message;
        }
    }
}
