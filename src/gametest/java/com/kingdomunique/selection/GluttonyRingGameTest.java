package com.kingdomunique.selection;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public final class GluttonyRingGameTest {
    private static final double FRENZY_RATIO = 1.0D + CurseEffects.GLUTTONY_DAMAGE_BONUS;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "gluttony_ring", tickLimit = 300)
    public void killRaisesAttackTemporarily(TestContext context) throws Exception {
        var world = context.getWorld();
        var player = player(context);
        var ownership = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        context.assertTrue(ownership.claimId(player.getUuid(), CurseRegistry.GLUTTONY),
                "The ring of gluttony must be claimable in the test world");
        var attack = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        double base = attack.getValue();

        tick(player);
        assertRatio(context, attack.getValue() / base, 1.0D, "before any kill");

        zombieKill(context, player);
        tick(player);
        assertRatio(context, attack.getValue() / base, FRENZY_RATIO, "right after a kill");

        context.runAtTick(context.getTick() + 210L, () -> {
            try {
                tick(player);
                assertRatio(context, attack.getValue() / base, 1.0D, "after the 10 second buff ends");

                zombieKill(context, player);
                tick(player);
                assertRatio(context, attack.getValue() / base, FRENZY_RATIO, "after a second kill");
                ownership.releaseId(player.getUuid(), CurseRegistry.GLUTTONY);
                tick(player);
                assertRatio(context, attack.getValue() / base, 1.0D, "after losing the ring");
                context.complete();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                ownership.releaseId(player.getUuid(), CurseRegistry.GLUTTONY);
            }
        });
    }

    private static void zombieKill(TestContext context, ServerPlayerEntity player) {
        var zombie = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 3);
        zombie.setAiDisabled(true);
        zombie.damage(context.getWorld().getDamageSources().playerAttack(player), 1000.0F);
        context.assertTrue(zombie.isDead(), "The zombie must die from the player's attack");
    }

    private static void assertRatio(TestContext context, double ratio, double expected, String when) {
        context.assertTrue(Math.abs(ratio - expected) < 1.0E-6,
                "Ring of gluttony attack must be x" + expected + " " + when + ", got x" + ratio);
    }

    private static ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), "gluttony_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 1)), 0, 0);
        return player;
    }

    private static void tick(ServerPlayerEntity player) throws Exception {
        var method = SelectionEventHandler.class.getDeclaredMethod("tickGluttony", ServerPlayerEntity.class);
        method.setAccessible(true);
        method.invoke(null, player);
    }
}
