package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

public final class GoldenScaleGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void dailyPayoutSurvivesRestart(TestContext context) {
        var world = context.getWorld();
        int[] payouts = {0};
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "golden_scale_test"), SyncedClientOptions.createDefault()) {
            @Override
            public boolean giveItemStack(ItemStack stack) {
                return true;
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
                payouts[0]++;
            }
        };

        RelicEffects.tickGoldenScale(player);
        RelicEffects.tickGoldenScale(player);
        context.assertTrue(payouts[0] == 1, "The golden scale must pay out once per day, got " + payouts[0]);

        var overworld = world.getServer().getWorld(World.OVERWORLD);
        WorldUniqueData saved = WorldUniqueData.getOrCreate(overworld);
        WorldUniqueData reloaded = new WorldUniqueData(
                saved.writeNbt(new NbtCompound(), overworld.getRegistryManager()));
        long day = overworld.getTimeOfDay() / 24000L;
        context.assertFalse(reloaded.claimGoldenScalePayout(player.getUuid(), day),
                "The day's payout must still count as received after a reload");
        context.assertTrue(reloaded.claimGoldenScalePayout(player.getUuid(), day + 1),
                "The next day's payout must be available after a reload");
        context.complete();
    }
}
