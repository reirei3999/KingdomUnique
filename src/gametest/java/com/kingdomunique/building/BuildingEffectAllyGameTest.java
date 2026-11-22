package com.kingdomunique.building;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.war.KingdomDiplomacyState;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.UUID;

public final class BuildingEffectAllyGameTest {
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
