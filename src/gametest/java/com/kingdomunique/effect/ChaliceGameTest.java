package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.RelicRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.World;

import java.util.UUID;

public final class ChaliceGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void chaliceRemovesOnlyHungerAndAllowsExhaustion(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "chalice_test"), SyncedClientOptions.createDefault());
        var data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        context.assertTrue(data.claimId(player.getUuid(), RelicRegistry.CHALICE), "Chalice must be available");
        try {
            RelicEffects.tickChalice(player);
            context.assertFalse(player.hasStatusEffect(StatusEffects.HUNGER), "No effect is safe to remove");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
            RelicEffects.tickChalice(player);
            context.assertFalse(player.hasStatusEffect(StatusEffects.HUNGER), "Chalice must remove Hunger");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Other effects must remain");
            player.addExhaustion(2.0F);
            context.assertTrue(player.getHungerManager().getExhaustion() == 2.0F,
                    "Chalice must not cancel ordinary exhaustion");
        } finally {
            data.releaseId(player.getUuid(), RelicRegistry.CHALICE);
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 1));
        RelicEffects.tickChalice(player);
        context.assertTrue(player.hasStatusEffect(StatusEffects.HUNGER), "Non-holders must retain Hunger");
        context.complete();
    }
}
