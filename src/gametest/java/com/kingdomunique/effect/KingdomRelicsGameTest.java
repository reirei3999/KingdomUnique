package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.entity.KingdomGuardianEntity;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.RelicRegistry;
import com.kingdomunique.war.KingdomDiplomacyState;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Set;
import java.util.UUID;

public final class KingdomRelicsGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void crystalDetectsArrivals(TestContext context) {
        var world = context.getWorld();
        var player = player(world);
        var visitor = player(world);
        BlockPos center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        setCenter(world, player.getUuid(), center);
        visitor.refreshPositionAndAngles(center, 0, 0);
        var diplomacy = KingdomDiplomacyState.get(world);
        var zombie = context.spawnMob(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var ally = context.spawnMob(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        ally.addCommandTag("allied_mob");
        try {
            context.assertTrue(ContentRegistry.idsOf(ContentRegistry.Kind.RELIC).containsAll(
                    Set.of(RelicRegistry.CRYSTAL, RelicRegistry.COMPASS, RelicRegistry.GUARDIAN_EGG)),
                    "All three relics must be in the existing relic pool");
            context.assertFalse(KingdomRelicEffects.isTerritoryEnemy(world, player, player.getUuid(), visitor),
                    "Neutral players must not be treated as intruders");
            diplomacy.setRelation(player.getUuid(), visitor.getUuid(), KingdomDiplomacyState.Relation.WAR);
            context.assertTrue(KingdomRelicEffects.isTerritoryEnemy(world, player, player.getUuid(), visitor),
                    "War enemies are intruders within the territory");
            context.assertEquals(KingdomRelicEffects.survey(world, player), 1, "One new hostile intruder");
            context.assertTrue(zombie.hasStatusEffect(StatusEffects.GLOWING), "Hostile intruder glows");
            context.assertFalse(ally.hasStatusEffect(StatusEffects.GLOWING), "Allied mob does not glow");
            context.assertEquals(KingdomRelicEffects.survey(world, player), 0, "No repeated alert while inside");
            zombie.refreshPositionAndAngles(center.add(8, 0, 0), 0, 0);
            context.assertEquals(KingdomRelicEffects.survey(world, player), 0, "No alerts from outside");
            zombie.refreshPositionAndAngles(center, 0, 0);
            context.assertEquals(KingdomRelicEffects.survey(world, player), 1, "Re-entry triggers another alert");
            context.complete();
        } finally {
            diplomacy.setRelation(player.getUuid(), visitor.getUuid(), KingdomDiplomacyState.Relation.NEUTRAL);
            KingdomData.getOrCreate(world).remove(player.getUuid());
            KingdomRelicEffects.survey(world, player);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void compassReturnsOncePerDay(TestContext context) {
        var world = context.getWorld();
        var player = player(world);
        var ownership = WorldUniqueData.getOrCreate(world);
        BlockPos center = prepareSnowyLanding(context);
        if (!DefaultAttributeRegistry.hasDefinitionFor(ModEntities.HERO)) {
            FabricDefaultAttributeRegistry.register(ModEntities.HERO, HeroEntity.createHeroAttributes());
        }
        var assassin = context.spawnMob(ModEntities.HERO, new BlockPos(2, 2, 2));
        assassin.refreshPositionAndAngles(center, 0, 0);
        assassin.setOwnerUuid(player.getUuid());
        assassin.setHeroClass(HeroClass.ASSASSIN);
        player.refreshPositionAndAngles(center.add(2, 0, 0), 0, 0);
        long time = world.getTimeOfDay();
        try {
            context.assertFalse(KingdomRelicEffects.recall(player), "Players without the relic cannot return");
            context.assertTrue(ownership.claimId(player.getUuid(), RelicRegistry.COMPASS), "Claim compass");
            context.assertFalse(KingdomRelicEffects.recall(player), "Recall fails without a base");
            setCenter(world, player.getUuid(), center);
            KingdomManager.togglePossession(player);
            context.assertEquals(KingdomManager.possessedHero(player), assassin, "Assassin possession is active");
            context.assertFalse(KingdomRelicEffects.recall(player), "Recall is denied while possessing an assassin");
            KingdomManager.togglePossession(player);
            context.assertTrue(KingdomRelicEffects.recall(player), "First recall succeeds after setting a base");
            context.assertEquals(player.getBlockPos(), center, "Recall lands on the snowy kingdom center");
            context.assertFalse(KingdomRelicEffects.recall(player), "Second recall on the same day fails");
            world.setTimeOfDay(time + 24000L);
            context.assertTrue(KingdomRelicEffects.recall(player), "Recall works again the next day");
            context.complete();
        } finally {
            KingdomManager.clearPossession(player);
            world.setTimeOfDay(time);
            ownership.releaseId(player.getUuid(), RelicRegistry.COMPASS);
            KingdomData.getOrCreate(world).remove(player.getUuid());
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void guardianReturnsFollowingDay(TestContext context) {
        var world = context.getWorld();
        var ownership = WorldUniqueData.getOrCreate(world);
        UUID owner = UUID.randomUUID();
        UUID enemy = UUID.randomUUID();
        BlockPos center = prepareSnowyLanding(context);
        setCenter(world, owner, center);
        long time = world.getTimeOfDay();
        KingdomGuardianEntity guardian = null;
        KingdomGuardianEntity revived = null;
        try {
            context.assertTrue(ownership.claimId(owner, RelicRegistry.GUARDIAN_EGG), "Claim guardian egg");
            guardian = KingdomRelicEffects.spawnGuardian(world, owner);
            context.assertTrue(guardian != null && guardian.isAlive(), "Guardian spawns at the base");
            context.assertEquals(guardian.getBlockPos(), center, "Guardian spawns on the snowy kingdom center");
            context.assertTrue(KingdomRelicEffects.spawnGuardian(world, owner) == null, "Only one guardian spawns");
            var creeper = context.spawnMob(EntityType.CREEPER, new BlockPos(3, 2, 3));
            context.assertTrue(KingdomRelicEffects.isTerritoryEnemy(world, guardian, owner, creeper),
                    "A creeper inside the kingdom is an intruder");
            context.assertTrue(TargetPredicate.createAttackable().ignoreVisibility().test(guardian, creeper),
                    "The vanilla targeting filter allows the guardian to attack creepers");
            var villager = context.spawnMob(EntityType.VILLAGER, new BlockPos(3, 2, 2));
            var soldier = (CitizenVillager) villager;
            soldier.ku$setOwnerUuid(enemy.toString());
            soldier.ku$setRole(CitizenVillager.ROLE_SOLDIER);
            KingdomDiplomacyState.get(world).setRelation(owner, enemy, KingdomDiplomacyState.Relation.WAR);
            context.assertTrue(KingdomRelicEffects.isTerritoryEnemy(world, guardian, owner, villager),
                    "The enemy soldier is within the kingdom");
            context.assertFalse(villager.damage(villager.getDamageSources().mobAttack(guardian), 4),
                    "Village protection blocks damage from the guardian");
            guardian.setTarget(villager);
            guardian.tick();
            context.assertTrue(guardian.getTarget() != villager, "Guardian drops an invulnerable village target");
            guardian.kill();
            context.assertTrue(KingdomRelicEffects.spawnGuardian(world, owner) == null, "No same-day replacement");
            world.setTimeOfDay(time + 24000L);
            revived = KingdomRelicEffects.spawnGuardian(world, owner);
            context.assertTrue(revived != null && revived.isAlive(), "Guardian revives the next day");
            context.assertTrue(!revived.getUuid().equals(guardian.getUuid()), "Dead guardian is replaced");
            context.complete();
        } finally {
            KingdomDiplomacyState.get(world).setRelation(owner, enemy, KingdomDiplomacyState.Relation.NEUTRAL);
            world.setTimeOfDay(time);
            if (guardian != null) guardian.discard();
            if (revived != null) revived.discard();
            ownership.releaseId(owner, RelicRegistry.GUARDIAN_EGG);
            KingdomData.getOrCreate(world).remove(owner);
        }
    }

    private static BlockPos prepareSnowyLanding(TestContext context) {
        var world = context.getWorld();
        var column = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
        world.setBlockState(surface.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(surface, Blocks.SNOW.getDefaultState());
        return surface;
    }

    private static void setCenter(ServerWorld world, UUID owner, BlockPos center) {
        KingdomData.getOrCreate(world).put(owner, new KingdomData.KingdomInfo(center.getX(), center.getZ(),
                0, 10, 0, false, 0, true, 4, true, "Test Kingdom"));
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        var profile = new GameProfile(UUID.randomUUID(), "relic_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions()) {
            @Override
            public void sendMessage(Text text, boolean overlay) {
            }

            @Override
            public void setCameraEntity(Entity entity) {
            }

            @Override
            public boolean teleport(ServerWorld target, double x, double y, double z, Set<PositionFlag> flags,
                                    float yaw, float pitch) {
                refreshPositionAndAngles(x, y, z, yaw, pitch);
                return true;
            }
        };
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        return player;
    }
}
