package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.World;

import java.util.UUID;

public final class SolitudeCrownGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "solitude_crown")
    public void movementIsBoostedLessThanOtherStats(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "solitude_test"), SyncedClientOptions.createDefault());
        var ownership = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        context.assertTrue(ownership.claimId(player.getUuid(), CurseRegistry.SOLITUDE),
                "The crown of solitude must be claimable in the test world");
        try {
            CurseEffects.applyAttributes(player);
            assertRatio(context, player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.2D, "movement speed");
            assertRatio(context, player, EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.4D, "attack damage");
            player.getAttributeInstance(EntityAttributes.PLAYER_MINING_EFFICIENCY).setBaseValue(4.0D);
            assertRatio(context, player, EntityAttributes.PLAYER_MINING_EFFICIENCY, 1.4D, "mining efficiency");
            player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(10.0D);
            assertRatio(context, player, EntityAttributes.GENERIC_ARMOR, 1.4D, "armor");
            context.complete();
        } finally {
            ownership.releaseId(player.getUuid(), CurseRegistry.SOLITUDE);
        }
    }

    private static void assertRatio(TestContext context, ServerPlayerEntity player,
                                    RegistryEntry<EntityAttribute> attribute, double expected, String name) {
        var instance = player.getAttributeInstance(attribute);
        double base = instance.getBaseValue();
        double ratio = instance.getValue() / base;
        context.assertTrue(Math.abs(ratio - expected) < 1.0E-6,
                "Crown of solitude " + name + " must be x" + expected + ", got x" + ratio + " (base " + base + ")");
    }
}
