package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.UUID;

public final class BalanceGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void phoenixClaimSurvivesReload(TestContext context) {
        var data = new WorldUniqueData(new NbtCompound());
        var owner = UUID.randomUUID();
        context.assertTrue(data.claimPhoenixRevival(owner, 0), "First revival is available on day zero");
        context.assertFalse(data.claimPhoenixRevival(owner, 0), "Same-day revival cannot repeat");
        var saved = data.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        saved.getCompound("phoenix_revivals").putLong("invalid-uuid", 0);
        var loaded = new WorldUniqueData(saved);
        context.assertFalse(loaded.claimPhoenixRevival(owner, 0), "Reload must retain the used revival");
        context.assertTrue(loaded.claimPhoenixRevival(owner, 1), "Next day must allow revival");
        context.assertTrue(loaded.claimPhoenixRevival(UUID.randomUUID(), 0), "Other players are independent");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void curseMultipliersRespectOwnership(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "balance_test"), SyncedClientOptions.createDefault());
        var data = WorldUniqueData.getOrCreate(world.getServer().getOverworld());
        context.assertTrue(CurseEffects.incomingMultiplier(player) == 1.0D, "No shackles means normal damage");
        context.assertTrue(CurseEffects.rollChaosMultiplier(player) == 1.0D, "No dice means normal damage");
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.SHACKLES), "Shackles are available");
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.CHAOS), "Dice are available");
        try {
            context.assertTrue(CurseEffects.incomingMultiplier(player) == 0.4D, "Shackles cut incoming damage by 60%");
            player.getRandom().setSeed(12345L);
            boolean downside = false;
            for (int i = 0; i < 1000; i++) {
                double roll = CurseEffects.rollChaosMultiplier(player);
                context.assertTrue(roll >= 0.2D && roll < 2.7D, "Dice stay within the new range");
                downside |= roll < 0.7D;
            }
            context.assertTrue(downside, "Dice must roll below the old minimum");
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.SHACKLES);
            data.releaseId(player.getUuid(), CurseRegistry.CHAOS);
        }
        context.complete();
    }

}
