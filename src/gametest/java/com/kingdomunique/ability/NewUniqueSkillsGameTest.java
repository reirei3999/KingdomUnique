package com.kingdomunique.ability;

import com.kingdomunique.data.PendingSelectionState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class NewUniqueSkillsGameTest {
    private ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), "skill_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 1)), 0, 0);
        return player;
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void timeRulerFreezesNearbyEnemiesForThreeSeconds(TestContext context) {
        var player = player(context);
        context.assertFalse(TimeRulerAbility.activate(player), "No enemy nearby must not consume the cooldown");
        var near = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 5);
        var villager = context.spawnEntity(EntityType.VILLAGER, 3, 2, 1);
        context.assertTrue(TimeRulerAbility.activate(player), "A nearby zombie must be frozen");
        context.assertTrue(near.isAiDisabled() && TimeRulerAbility.isFrozen(near), "The zombie must stop");
        context.assertFalse(villager.isAiDisabled(), "Non-hostile villagers must keep moving");
        context.runAtTick(TimeRulerAbility.DURATION_TICKS + 10, () -> {
            context.assertFalse(near.isAiDisabled(), "The zombie must move again after 3 seconds");
            context.assertFalse(near.getCommandTags().contains(TimeRulerAbility.FROZEN_TAG),
                    "The freeze marker must be removed");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void alchemyUpgradesOneHeldBlockWithDailyLimit(TestContext context) {
        var player = player(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 64));
        context.assertTrue(AlchemyAbility.activate(player), "Stone must transmute");
        context.assertTrue(player.getMainHandStack().getCount() == 63, "Only one stone must be used");
        context.assertTrue(player.getInventory().count(Items.IRON_ORE) == 1, "Stone must become iron ore");
        for (int i = 1; i < AlchemyAbility.DAILY_LIMIT; i++) {
            context.assertTrue(AlchemyAbility.activate(player), "Uses within the daily limit must succeed");
        }
        context.assertTrue(AlchemyAbility.remainingUses(player) == 0, "The daily limit must be used up");
        context.assertFalse(AlchemyAbility.activate(player), "Uses beyond the daily limit must fail");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLD_BLOCK));
        context.assertTrue(AlchemyAbility.upgradeOf(Items.GOLD_BLOCK) == Items.DIAMOND_BLOCK,
                "Gold blocks must upgrade to diamond blocks");
        context.assertTrue(AlchemyAbility.upgradeOf(Items.DIAMOND_BLOCK) == null, "Diamond is the top tier");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void weatherControlCyclesAndKeepsOneDayCooldown(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var ownership = WorldUniqueData.getOrCreate(world);
        world.setWeather(1000, 0, false, false);
        try {
            context.assertTrue(ownership.claimId(player.getUuid(), UniqueContentRegistry.WEATHER_CONTROL),
                    "The test player must receive weather control");
            AbilityActions.handleActivate(player);
            context.assertTrue(WeatherControlAbility.current(world) == WeatherControlAbility.Weather.RAIN, "Clear weather must turn to rain");
            AbilityActions.handleActivate(player);
            context.assertTrue(WeatherControlAbility.current(world) == WeatherControlAbility.Weather.RAIN, "The one-day cooldown must block reuse");
            AbilityState.remove(player.getUuid());
            AbilityActions.restoreCooldowns(player);
            AbilityActions.handleActivate(player);
            context.assertTrue(WeatherControlAbility.current(world) == WeatherControlAbility.Weather.RAIN, "Rejoining must not reset the cooldown");
            context.assertTrue(WeatherControlAbility.activate(player)
                    && WeatherControlAbility.current(world) == WeatherControlAbility.Weather.THUNDER,
                    "Rain must turn to thunder");
            context.assertTrue(WeatherControlAbility.activate(player)
                    && WeatherControlAbility.current(world) == WeatherControlAbility.Weather.CLEAR,
                    "Thunder must turn back to clear");
            context.complete();
        } finally {
            ownership.releaseId(player.getUuid(), UniqueContentRegistry.WEATHER_CONTROL);
            AbilityState.remove(player.getUuid());
            world.setWeather(1000, 0, false, false);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void ancientCityChestOffersOnlyTheThreeNewSkillsOnce(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var pending = PendingSelectionState.get(world);
        try {
            context.assertFalse(UniqueContentRegistry.UNIQUE_IDS.contains(UniqueContentRegistry.TIME_RULER),
                    "Ancient City skills must not appear in the diamond pool");
            Criteria.PLAYER_GENERATES_CONTAINER_LOOT.trigger(player, LootTables.SIMPLE_DUNGEON_CHEST);
            context.assertTrue(pending.getPending(player.getUuid()) == null, "Other chests must not trigger");
            Criteria.PLAYER_GENERATES_CONTAINER_LOOT.trigger(player, LootTables.ANCIENT_CITY_CHEST);
            var offer = pending.getPending(player.getUuid());
            context.assertTrue(offer != null && offer.kind() == ContentRegistry.Kind.ABILITY
                            && UniqueContentRegistry.ANCIENT_CITY_ABILITY_IDS.containsAll(offer.optionIds())
                            && !offer.optionIds().isEmpty(),
                    "An Ancient City chest must offer the new skills");
            pending.clearPending(player.getUuid());
            Criteria.PLAYER_GENERATES_CONTAINER_LOOT.trigger(player, LootTables.ANCIENT_CITY_ICE_BOX_CHEST);
            context.assertTrue(pending.getPending(player.getUuid()) == null, "The offer must happen only once");
            context.complete();
        } finally {
            pending.clearPending(player.getUuid());
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void ancientCitySkillTakesTheActivateKeyOverEarlierSkills(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var ownership = WorldUniqueData.getOrCreate(world);
        try {
            context.assertTrue(ownership.claimId(player.getUuid(), UniqueContentRegistry.SPATIAL_LEAP)
                            && ownership.claimId(player.getUuid(), UniqueContentRegistry.WEATHER_CONTROL),
                    "The test player must receive both skills");
            context.assertTrue(UniqueContentRegistry.WEATHER_CONTROL.equals(
                            UniqueContentRegistry.getActiveAbility(player)),
                    "The R key must use the Ancient City skill");
            context.complete();
        } finally {
            ownership.releaseId(player.getUuid(), UniqueContentRegistry.SPATIAL_LEAP);
            ownership.releaseId(player.getUuid(), UniqueContentRegistry.WEATHER_CONTROL);
        }
    }
}
