package com.kingdomunique.ability;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.UUID;

public final class AbsoluteDominionGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
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
        context.assertFalse(revived.getUuid().equals(zombie.getUuid()), "Revival must use a new UUID");
        context.assertTrue(revived.deathTime == 0 && revived.hurtTime == 0,
                "Revival must clear death and hurt timers");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
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
        context.assertTrue(((ZombieEntity) reloaded).isPersistent(), "Reload must preserve despawn protection");
        AlliedMobManager.restore(reloaded);
        context.assertTrue(player.getUuid().equals(AlliedMobManager.getOwnerUuid(reloaded)),
                "Repeated restoration must be safe");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void unreachableTargetsAreReleasedWithoutBlockingCombat(TestContext context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlockState(new BlockPos(x, 1, z), net.minecraft.block.Blocks.STONE);
            }
        }
        ZombieEntity ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        ally.setOnGround(true);
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
        reachable.setPosition(reachable.getPos().add(20.0D, 0.0D, 0.0D));
        for (int i = 0; i < 10; i++) targeting.tick();
        context.assertTrue(ally.getTarget() == null, "An out-of-range enemy must be released");
        ally.setTarget(reachable);
        reachable.discard();
        for (int i = 0; i < 10; i++) targeting.tick();
        context.assertTrue(ally.getTarget() == null, "A removed enemy must be released");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void followYieldsToCombatAndWaitsForOfflineOwner(TestContext context) {
        var world = context.getWorld();
        var king = player(world);
        var ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var enemy = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        AlliedMobManager.ally(ally, king);
        var follow = new AlliedFollowGoal(ally, king.getUuid());
        context.assertFalse(follow.canStart(), "Offline owners must not start following");
        king.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(6, 2, 1)), 0, 0);
        world.onPlayerConnected(king);
        try {
            context.assertTrue(follow.canStart(), "An online owner without combat must allow following");
            ally.setTarget(enemy);
            context.assertFalse(follow.canStart(), "Combat must prevent follow from starting");
            context.assertFalse(follow.shouldContinue(), "Combat must interrupt following");
            ally.setTarget(null);
            context.assertTrue(follow.shouldContinue(), "Following must resume after combat");
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(king.networkHandler, world.getServer());
        } finally {
            king.discard();
        }
        context.assertTrue(AlliedMobManager.isAlly(ally), "Owner departure must not remove ally ownership");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void malformedAndMissingOwnerTagsAreIgnored(TestContext context) {
        var zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        AlliedMobManager.restore(zombie);
        context.assertFalse(AlliedMobManager.isAlly(zombie), "Unowned mobs must not become allies");
        zombie.addCommandTag("kingdomunique_ally_owner:invalid");
        AlliedMobManager.restore(zombie);
        context.assertTrue(AlliedMobManager.getOwnerUuid(zombie) == null, "Invalid ownership must be ignored");
        AlliedMobManager.restore(null);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void knightAreaAttackAndTargetingSpareAllies(TestContext context) throws Exception {
        var king = player(context.getWorld());
        var hero = context.spawnEntity(com.kingdomunique.entity.ModEntities.HERO, 2, 2, 2);
        hero.setOwnerUuid(king.getUuid());
        var ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 2));
        var enemy = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        AlliedMobManager.ally(ally, king);
        float allyHealth = ally.getHealth();
        float enemyHealth = enemy.getHealth();
        var lastSpecial = hero.getClass().getDeclaredField("lastSpecialTick");
        lastSpecial.setAccessible(true);
        lastSpecial.setLong(hero, context.getWorld().getTime() - 40L);
        hero.tick();
        context.assertTrue(ally.getHealth() == allyHealth, "Knight area attacks must spare revived allies");
        context.assertTrue(enemy.getHealth() < enemyHealth, "Knight area attacks must still damage enemies");
        context.assertFalse(hero.getTarget() == ally, "Hero targeting must not choose revived allies");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void soldierSweepAndNearestEnemySpareAllies(TestContext context) throws Exception {
        var world = context.getWorld();
        var king = player(world);
        var soldier = context.spawnEntity(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        soldier.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.item.ItemStack(com.kingdomunique.item.ModItems.SLAUGHTER_BLADE));
        var ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 2));
        var enemy = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        var extra = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        AlliedMobManager.ally(ally, king);
        var handler = com.kingdomunique.soldier.SoldierCombatHandler.class;
        var nearest = handler.getDeclaredMethod("findNearestEnemy", ServerWorld.class,
                net.minecraft.entity.passive.VillagerEntity.class, double.class);
        nearest.setAccessible(true);
        context.assertTrue(nearest.invoke(null, world, soldier, 8.0D) == enemy,
                "Nearest hostile lookup must skip the closer ally");
        float allyHealth = ally.getHealth();
        float extraHealth = extra.getHealth();
        var sweep = handler.getDeclaredMethod("applyUniqueWeaponEffects",
                net.minecraft.entity.passive.VillagerEntity.class, net.minecraft.entity.LivingEntity.class, float.class);
        sweep.setAccessible(true);
        sweep.invoke(null, soldier, enemy, 4.0F);
        context.assertTrue(ally.getHealth() == allyHealth, "Soldier sweep must spare revived allies");
        context.assertTrue(extra.getHealth() < extraHealth, "Soldier sweep must still damage enemies");
        enemy.discard();
        extra.discard();
        context.assertTrue(nearest.invoke(null, world, soldier, 8.0D) == null,
                "An ally alone must not count as an enemy");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "absolute_dominion")
    public void raidCompletionIgnoresAlliesWithStaleRaidTags(TestContext context) throws Exception {
        var world = context.getWorld();
        var king = player(world);
        var center = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var data = com.kingdomunique.kingdom.KingdomData.getOrCreate(world);
        data.put(king.getUuid(), new com.kingdomunique.kingdom.KingdomData.KingdomInfo(
                center.getX(), center.getZ(), 0, 10, 0, false, 0, true, 100, true, ""));
        var ally = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 2));
        var enemy = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        try {
            ally.setPosition(ally.getX(), 64.0D, ally.getZ());
            enemy.setPosition(enemy.getX(), 64.0D, enemy.getZ());
            String tag = "ku_raid_" + king.getUuid();
            ally.addCommandTag(tag);
            enemy.addCommandTag(tag);
            AlliedMobManager.ally(ally, king);
            var remaining = com.kingdomunique.war.RaidSystem.class.getDeclaredMethod(
                    "hasLivingRaiders", ServerWorld.class, UUID.class);
            remaining.setAccessible(true);
            context.assertTrue((boolean) remaining.invoke(null, world, king.getUuid()),
                    "A living enemy raider must still block completion");
            enemy.discard();
            context.assertFalse((boolean) remaining.invoke(null, world, king.getUuid()),
                    "A revived ally with a stale raid tag must not block completion");
        } finally {
            ally.discard();
            enemy.discard();
            data.remove(king.getUuid());
        }
        context.complete();
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "dominion_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        var client = ConnectedClientData.createDefault(player.getGameProfile(), false);
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        return player;
    }
}
