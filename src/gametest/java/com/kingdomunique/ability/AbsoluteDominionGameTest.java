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

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "dominion_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
