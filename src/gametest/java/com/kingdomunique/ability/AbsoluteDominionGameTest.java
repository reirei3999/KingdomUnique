package com.kingdomunique.ability;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.UUID;

public final class AbsoluteDominionGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void slainHostileIsRevivedAsPersistentAlly(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.damage(world.getDamageSources().playerAttack(player), 1000.0F);
        context.assertTrue(zombie.isDead(), "The zombie must die before it can be revived");

        context.assertTrue(AlliedMobManager.tryRevive(zombie, player, world), "A slain hostile must be revivable");
        ZombieEntity revived = world.getEntitiesByClass(ZombieEntity.class,
                        new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(8.0D),
                        z -> z.isAlive() && z != zombie)
                .stream().findFirst().orElse(null);
        context.assertTrue(revived != null, "The revived zombie must be in the world");
        context.assertTrue(player.getUuid().equals(AlliedMobManager.getOwnerUuid(revived)),
                "The revived zombie must belong to the killer");
        context.assertTrue(revived.isPersistent(), "Allies must not despawn when the owner walks away");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void allyOwnershipSurvivesReload(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        AlliedMobManager.ally(zombie, player);

        NbtCompound nbt = new NbtCompound();
        zombie.saveNbt(nbt);
        zombie.discard();
        AlliedMobManager.forget(zombie.getUuid());

        Entity reloaded = EntityType.loadEntityWithPassengers(nbt, world, entity -> entity);
        context.assertTrue(reloaded != null && world.spawnEntity(reloaded), "The saved ally must load back");
        context.assertTrue(player.getUuid().equals(AlliedMobManager.getOwnerUuid(reloaded)),
                "A reloaded ally must keep its owner");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void revivedRaiderLeavesTheRaidAndIsNotAnEnemy(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        ZombieEntity raider = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        raider.addCommandTag("ku_raid_" + player.getUuid());
        raider.damage(world.getDamageSources().playerAttack(player), 1000.0F);
        context.assertTrue(AlliedMobManager.tryRevive(raider, player, world), "The raider must be revivable");

        ZombieEntity revived = world.getEntitiesByClass(ZombieEntity.class,
                        new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(8.0D),
                        z -> z.isAlive() && z != raider)
                .stream().findFirst().orElseThrow();
        context.assertFalse(revived.getCommandTags().stream().anyMatch(tag -> tag.startsWith("ku_raid_")),
                "A revived raider must no longer count toward the raid");
        ZombieEntity guard = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        context.assertFalse(com.kingdomunique.war.KingdomWarTargetHelper.isValidEnemy(world, guard, null, revived),
                "Kingdom units must not treat allies as enemies");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unreachableTargetsAreReleasedWithoutBlockingCombat(TestContext context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlockState(new BlockPos(x, 1, z), net.minecraft.block.Blocks.STONE);
            }
        }
        ZombieEntity ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        AlliedMobManager.ally(ally, player(context.getWorld()));
        ZombieEntity trapped = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        for (int x = 4; x <= 6; x++) {
            for (int y = 2; y <= 5; y++) {
                for (int z = 4; z <= 6; z++) {
                    if (x != 5 || z != 5) {
                        context.setBlockState(new BlockPos(x, y, z), net.minecraft.block.Blocks.STONE);
                    }
                }
            }
        }
        AlliedTargetGoal targeting = new AlliedTargetGoal(ally);
        context.assertFalse(targeting.getControls().contains(net.minecraft.entity.ai.goal.Goal.Control.MOVE),
                "Target selection must not reserve movement from melee combat");
        ally.setTarget(trapped);
        for (int i = 0; i < 10; i++) targeting.tick();
        context.assertTrue(ally.getTarget() == null, "An enclosed enemy must not stop following forever");
        ZombieEntity reachable = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        for (int i = 0; i < 10; i++) targeting.tick();
        context.assertTrue(ally.getTarget() == reachable, "An accessible enemy must still be selected");
        reachable.discard();
        for (int i = 0; i < 10; i++) targeting.tick();
        context.assertTrue(ally.getTarget() == null, "A removed enemy must be released");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void knightTauntPreservesSameKingAllies(TestContext context) {
        var king = player(context.getWorld());
        var hero = com.kingdomunique.entity.ModEntities.HERO.create(context.getWorld());
        hero.setOwnerUuid(king.getUuid());
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        hero.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        context.getWorld().spawnEntity(hero);
        var ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var enemy = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        AlliedMobManager.ally(ally, king);
        com.kingdomunique.war.Warfare.taunt(hero);
        context.assertTrue(ally.getTarget() == null, "A knight must not taunt the king's revived allies");
        context.assertTrue(enemy.getTarget() == hero, "An unowned enemy must still be taunted");
        context.assertTrue(com.kingdomunique.war.Warfare.ownerOf(null) == null, "Missing entities have no owner");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void onlyNaturalSpawnsKeepTheWardMarker(TestContext context) {
        var world = context.getWorld();
        var zombie = EntityType.ZOMBIE.create(world);
        var difficulty = world.getLocalDifficulty(context.getAbsolutePos(BlockPos.ORIGIN));
        zombie.initialize(world, difficulty, net.minecraft.entity.SpawnReason.NATURAL, null);
        context.assertTrue(zombie.getCommandTags().contains("kingdomunique_natural_spawn"),
                "Naturally spawned hostiles must be eligible for ward removal");
        NbtCompound nbt = new NbtCompound();
        zombie.writeNbt(nbt);
        var loaded = EntityType.ZOMBIE.create(world);
        loaded.readNbt(nbt);
        context.assertTrue(loaded.getCommandTags().contains("kingdomunique_natural_spawn"),
                "Natural spawn provenance must survive saving and loading");
        loaded.initialize(world, difficulty, net.minecraft.entity.SpawnReason.SPAWNER, null);
        context.assertFalse(loaded.getCommandTags().contains("kingdomunique_natural_spawn"),
                "Spawner mobs must not be eligible, even if initialized from marked data");
        var uninitialized = EntityType.ZOMBIE.create(world);
        context.assertFalse(uninitialized.getCommandTags().contains("kingdomunique_natural_spawn"),
                "Unknown spawn provenance must not qualify for removal");
        context.complete();
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "dominion_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
