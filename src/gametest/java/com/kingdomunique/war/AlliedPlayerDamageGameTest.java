package com.kingdomunique.war;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class AlliedPlayerDamageGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "allied_player_damage")
    public void allianceBlocksPlayerDamageUntilWar(TestContext context) {
        var world = context.getWorld();
        var attacker = createPlayer(context, "pvp_attacker");
        var victim = createPlayer(context, "pvp_victim");
        var diplomacy = KingdomDiplomacyState.get(world);
        var melee = world.getDamageSources().playerAttack(attacker);
        var arrow = new ArrowEntity(world, attacker, new ItemStack(Items.ARROW), null);
        var projectile = world.getDamageSources().arrow(arrow, attacker);
        float health = victim.getHealth();
        boolean pvpEnabled = world.getServer().isPvpEnabled();

        try {
            world.getServer().setPvpEnabled(true);
            diplomacy.setRelation(attacker.getUuid(), victim.getUuid(), KingdomDiplomacyState.Relation.ALLY);
            context.assertFalse(victim.damage(melee, 6.0F), "An allied melee hit must be rejected");
            context.assertFalse(victim.damage(projectile, 6.0F), "An allied arrow hit must be rejected");
            context.assertEquals(victim.getHealth(), health, "Allied hits must not affect health");

            diplomacy.setRelation(attacker.getUuid(), victim.getUuid(), KingdomDiplomacyState.Relation.WAR);
            context.assertTrue(victim.damage(melee, 6.0F), "A melee hit during war must be accepted");
            context.assertEquals(victim.getHealth(), health - 6.0F, "The wartime hit must deal normal damage");
            context.complete();
        } finally {
            diplomacy.setRelation(attacker.getUuid(), victim.getUuid(), KingdomDiplomacyState.Relation.NEUTRAL);
            world.getServer().setPvpEnabled(pvpEnabled);
        }
    }

    private static ServerPlayerEntity createPlayer(TestContext context, String name) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        Vec3d pos = context.getAbsolute(new Vec3d(1.5D, 1.0D, 1.5D));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        for (int i = 0; i < 60; i++) {
            player.tick();
        }
        return player;
    }
}
