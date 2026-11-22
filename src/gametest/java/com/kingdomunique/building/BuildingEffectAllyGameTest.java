package com.kingdomunique.building;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.war.KingdomDiplomacyState;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

public final class BuildingEffectAllyGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void marketPaysAtWorldTimeBoundariesRegardlessOfRestartPhase(TestContext context) {
        var server = context.getWorld().getServer();
        var world = server.getWorld(World.OVERWORLD);
        var properties = server.getSaveProperties().getMainWorldProperties();
        long originalTime = world.getTime();
        var data = CompletedBuildingsData.getOrCreate(world);
        var originalEntries = new java.util.ArrayList<>(data.entries());
        var owner = context.createMockCreativeServerPlayerInWorld();
        context.assertTrue(server.getPlayerManager().getPlayer(owner.getUuid()) == owner,
                "Market owner must be online in PlayerManager");
        try {
            data.entries().clear();
            for (long savedTime : new long[]{24037L, 24000L}) {
                owner.getInventory().clear();
                data.add(BlockPos.ORIGIN, BuildingType.MARKET, owner.getUuid());
                for (long time = savedTime + 1; time <= 48001L; time++) {
                    properties.setTime(time);
                    BuildingEffectHandler.tick(server);
                    int expected = time < 36000L ? 0 : time < 48000L ? 3 : 6;
                    context.assertEquals(owner.getInventory().count(Items.EMERALD), expected,
                            "Market must pay exactly once per boundary from saved time " + savedTime
                                    + " at world time " + time);
                }
                data.entries().clear();
            }
            owner.getInventory().clear();
            properties.setTime(60000L);
            BuildingEffectHandler.tick(server);
            context.assertEquals(owner.getInventory().count(Items.EMERALD), 0,
                    "No completed market must mean no payout");
            data.add(BlockPos.ORIGIN, BuildingType.MARKET, UUID.randomUUID());
            BuildingEffectHandler.tick(server);
            context.assertEquals(owner.getInventory().count(Items.EMERALD), 0,
                    "An offline owner's market must not pay another player");
        } finally {
            properties.setTime(originalTime);
            data.entries().clear();
            data.entries().addAll(originalEntries);
            data.markDirty();
            server.getPlayerManager().remove(owner);
            owner.discard();
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void infirmaryAndArsenalOnlyAffectOwnerAndAllies(TestContext context) {
        ServerWorld world = context.getWorld();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
            }
        }
        var owner = player(world, "infirmary_owner");
        var ally = player(world, "infirmary_ally");
        var enemy = player(world, "infirmary_enemy");
        var neutral = player(world, "infirmary_neutral");
        KingdomDiplomacyState diplomacy = KingdomDiplomacyState.get(world);
        diplomacy.setRelation(owner.getUuid(), ally.getUuid(), KingdomDiplomacyState.Relation.ALLY);
        diplomacy.setRelation(owner.getUuid(), enemy.getUuid(), KingdomDiplomacyState.Relation.WAR);
        try {
            VillagerEntity ownSoldier = soldier(context, 1, owner);
            VillagerEntity allySoldier = soldier(context, 2, ally);
            VillagerEntity enemySoldier = soldier(context, 3, enemy);
            VillagerEntity neutralSoldier = soldier(context, 4, neutral);
            VillagerEntity unowned = soldier(context, 5, null);

            Box area = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).stretch(7.0D, 4.0D, 3.0D);
            BuildingEffectHandler.applyInfirmary(world, owner.getUuid(), area);
            BuildingEffectHandler.applyArsenal(world, owner.getUuid(), area);

            for (VillagerEntity friend : new VillagerEntity[]{ownSoldier, allySoldier}) {
                context.assertTrue(friend.hasStatusEffect(StatusEffects.REGENERATION),
                        "Own and allied soldiers must regenerate at the infirmary");
                context.assertTrue(friend.hasStatusEffect(StatusEffects.RESISTANCE),
                        "Own and allied soldiers must get the arsenal's resistance");
            }
            for (VillagerEntity other : new VillagerEntity[]{enemySoldier, neutralSoldier, unowned}) {
                context.assertFalse(other.hasStatusEffect(StatusEffects.REGENERATION),
                        "Enemy, neutral and unowned villagers must not regenerate at the infirmary");
                context.assertFalse(other.hasStatusEffect(StatusEffects.RESISTANCE),
                        "Enemy, neutral and unowned villagers must not get the arsenal's resistance");
            }

            context.assertTrue(BuildingEffectHandler.receivesInfirmary(world, owner.getUuid(), owner),
                    "The owner must be healed by their own infirmary");
            context.assertTrue(BuildingEffectHandler.receivesInfirmary(world, owner.getUuid(), ally),
                    "An allied king must be healed by the infirmary");
            context.assertFalse(BuildingEffectHandler.receivesInfirmary(world, owner.getUuid(), enemy),
                    "A king at war with the owner must not be healed by the infirmary");
            context.assertFalse(BuildingEffectHandler.receivesInfirmary(world, owner.getUuid(), neutral),
                    "A neutral king must not be healed by the infirmary");
            context.assertFalse(BuildingEffectHandler.receivesArsenal(world, owner.getUuid(), owner),
                    "The arsenal must not apply to players");
        } finally {
            diplomacy.setRelation(owner.getUuid(), ally.getUuid(), KingdomDiplomacyState.Relation.NEUTRAL);
            diplomacy.setRelation(owner.getUuid(), enemy.getUuid(), KingdomDiplomacyState.Relation.NEUTRAL);
        }
        context.complete();
    }

    private static VillagerEntity soldier(TestContext context, int x, ServerPlayerEntity king) {
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, x + 0.5f, 2.0f, 1.5f);
        villager.setAiDisabled(true);
        CitizenVillager citizen = (CitizenVillager) villager;
        citizen.ku$setRole(CitizenVillager.ROLE_SOLDIER);
        if (king != null) {
            citizen.ku$setOwnerUuid(king.getUuid().toString());
        }
        villager.setHealth(villager.getMaxHealth() / 2.0F);
        return villager;
    }

    private static ServerPlayerEntity player(ServerWorld world, String name) {
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), name), SyncedClientOptions.createDefault());
        player.setHealth(player.getMaxHealth() / 2.0F);
        return player;
    }
}
