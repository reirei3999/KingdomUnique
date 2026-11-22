package com.kingdomunique.ability;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.war.KingdomWarTargetHelper;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.World;

import java.util.UUID;

public final class AbilityCleanupGameTest {
    private ServerPlayerEntity player(TestContext context) {
        var profile = new GameProfile(UUID.randomUUID(), "ability_test");
        return player(context, profile);
    }

    private ServerPlayerEntity player(TestContext context, GameProfile profile) {
        var world = context.getWorld();
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 1)), 0, 0);
        return player;
    }

    private ZombieEntity hold(TestContext context, ServerPlayerEntity player) throws Exception {
        var target = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 4);
        target.setAiDisabled(true);
        var method = AbilityActions.class.getDeclaredMethod("gravityControl", ServerPlayerEntity.class);
        method.setAccessible(true);
        context.assertTrue((boolean) method.invoke(null, player), "Gravity must acquire a target");
        context.assertTrue(target.hasNoGravity(), "Target must start suspended");
        return target;
    }

    private void tick(ServerPlayerEntity player) throws Exception {
        var method = AbilityPassives.class.getDeclaredMethod("tickGravity", ServerPlayerEntity.class);
        method.setAccessible(true);
        method.invoke(null, player);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void reviveUsesFreshUuid(TestContext context) {
        var owner = player(context);
        var dead = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 4);
        var soldier = context.spawnEntity(EntityType.VILLAGER, 2, 2, 1);
        ((CitizenVillager) soldier).ku$setRole(CitizenVillager.ROLE_SOLDIER);
        ((CitizenVillager) soldier).ku$setOwnerUuid(owner.getUuid().toString());
        context.assertTrue(KingdomWarTargetHelper.isValidEnemy(context.getWorld(), soldier, owner.getUuid(), dead),
                "Ordinary hostile mobs must remain enemy targets");
        dead.addCommandTag("allied_mob");
        context.assertFalse(KingdomWarTargetHelper.isValidEnemy(context.getWorld(), soldier, owner.getUuid(), dead),
                "Legacy allied tags must remain protected");
        dead.removeCommandTag("allied_mob");
        dead.kill();
        context.assertTrue(context.getWorld().getEntity(dead.getUuid()) == dead,
                "Dying original must still occupy its UUID");
        context.assertTrue(AlliedMobManager.tryRevive(dead, owner, context.getWorld()), "Revive must spawn a copy");
        var allies = context.getWorld().getEntitiesByClass(ZombieEntity.class, dead.getBoundingBox().expand(1),
                AlliedMobManager::isAlly);
        context.assertTrue(allies.size() == 1, "One allied copy must exist");
        var revived = allies.getFirst();
        context.assertFalse(revived.getUuid().equals(dead.getUuid()), "Copy must use a new UUID");
        context.assertTrue(revived.isAlive(), "Copy must be alive");
        context.assertFalse(KingdomWarTargetHelper.isValidEnemy(context.getWorld(), soldier, owner.getUuid(), revived),
                "The owner's soldiers must not target revived allies");
        AlliedMobManager.forgetOwner(owner.getUuid());
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void revivedHeldMobHasGravity(TestContext context) throws Exception {
        var owner = player(context);
        var dead = hold(context, owner);
        dead.kill();
        context.assertTrue(dead.hasNoGravity(), "Dead held mob must still carry NoGravity before cleanup");
        context.assertTrue(AlliedMobManager.tryRevive(dead, owner, context.getWorld()), "Held mob must revive");
        var allies = context.getWorld().getEntitiesByClass(ZombieEntity.class, dead.getBoundingBox().expand(1),
                AlliedMobManager::isAlly);
        context.assertTrue(allies.size() == 1, "One allied copy must exist");
        var revived = allies.getFirst();
        context.assertFalse(revived.hasNoGravity(), "Revived copy must spawn with gravity on");
        tick(owner);
        context.assertFalse(revived.hasNoGravity(), "Original target cleanup must not suspend the copy");
        AlliedMobManager.forgetOwner(owner.getUuid());
        AbilityState.remove(owner.getUuid());
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void disconnectRestoresGravity(TestContext context) throws Exception {
        var player = player(context);
        var target = hold(context, player);
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.networkHandler, context.getWorld().getServer());
        context.assertFalse(target.hasNoGravity(), "Disconnect must restore gravity");
        context.assertFalse(AbilityState.all().containsKey(player.getUuid()), "Disconnect must remove state");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "ability_cooldown_relog")
    public void gravityCooldownSurvivesRelog(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var data = WorldUniqueData.getOrCreate(server.getOverworld());
        var ability = UniqueContentRegistry.GRAVITY_CONTROL;
        var player = player(context);
        var owner = data.getOwner(ability);
        var uuid = player.getUuid();
        context.assertTrue(owner == null ? data.claimId(uuid, ability) : data.transferId(owner, uuid, ability),
                "Test player must own gravity control");
        try {
            var target = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 4);
            target.setAiDisabled(true);
            long time = world.getTime();
            AbilityActions.handleActivate(player);
            context.assertTrue(target.hasNoGravity(), "Gravity control must activate");
            AbilityActions.handleActivate(player);
            context.assertTrue(target.hasNoGravity(), "Cooldown must block immediate reuse before disconnect");

            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.networkHandler, server);
            context.assertFalse(target.hasNoGravity(), "Disconnect must release the target");
            var rejoined = player(context, player.getGameProfile());
            context.assertTrue(world.getTime() == time, "The same game tick must still be active at relog");
            AbilityActions.handleActivate(rejoined);
            context.assertFalse(target.hasNoGravity(), "Relog must not allow gravity control before cooldown expires");
        } finally {
            AbilityPassives.clearGravity(player);
            AbilityState.remove(uuid);
            if (owner == null) {
                data.releaseId(uuid, ability);
            } else {
                data.transferId(uuid, owner, ability);
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void dimensionChangeRestoresGravity(TestContext context) throws Exception {
        var player = player(context);
        var target = hold(context, player);
        player.setServerWorld(context.getWorld().getServer().getWorld(World.NETHER));
        tick(player);
        context.assertFalse(target.hasNoGravity(), "Dimension change must restore old-world target gravity");
        context.assertTrue(AbilityState.get(player.getUuid()).getGravityTarget() == null, "Hold must end");
        AbilityState.remove(player.getUuid());
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void timeoutRestoresGravity(TestContext context) throws Exception {
        var player = player(context);
        var target = hold(context, player);
        AbilityState.get(player.getUuid()).setGravityReleaseAt(0);
        tick(player);
        context.assertFalse(target.hasNoGravity(), "Timeout must restore gravity");
        context.assertTrue(AbilityState.get(player.getUuid()).getGravityTarget() == null, "Timeout must clear target");
        tick(player);
        AbilityPassives.clearGravity(player);
        AbilityState.remove(player.getUuid());
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void manualReleaseRestoresGravity(TestContext context) throws Exception {
        var player = player(context);
        var target = hold(context, player);
        var method = AbilityActions.class.getDeclaredMethod("gravityControl", ServerPlayerEntity.class);
        method.setAccessible(true);
        method.invoke(null, player);
        context.assertFalse(target.hasNoGravity(), "Manual release must restore gravity");
        context.assertTrue(AbilityState.get(player.getUuid()).getGravityTarget() == null, "Manual release must clear target");
        AbilityState.remove(player.getUuid());
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void deadTargetRestoresGravity(TestContext context) throws Exception {
        var player = player(context);
        var target = hold(context, player);
        target.kill();
        tick(player);
        context.assertFalse(target.hasNoGravity(), "Dead targets must also be cleaned up");
        AbilityState.remove(player.getUuid());
        context.complete();
    }
}
