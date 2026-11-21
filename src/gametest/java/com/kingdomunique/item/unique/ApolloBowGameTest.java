package com.kingdomunique.item.unique;

import com.kingdomunique.data.WorldUniqueData;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

import java.util.UUID;

public final class ApolloBowGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void fullyDrawnSkywardShotAtAltitudeGrantsApolloBow(TestContext context) {
        ServerWorld world = context.getWorld();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        UUID previous = data.getOwner("apollo_bow");
        if (previous != null) {
            data.releaseId(previous, "apollo_bow");
        }
        world.setTimeOfDay(1000L);

        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "apollo_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        player.refreshPositionAndAngles(0.5D, 305.0D, 0.5D, 0.0F, -90.0F);
        player.getInventory().insertStack(new ItemStack(Items.ARROW, 8));
        ItemStack bow = new ItemStack(Items.BOW);

        // 20ティック（フル）引き絞ってから放つ。
        Items.BOW.onStoppedUsing(bow, world, player, Items.BOW.getMaxUseTime(bow, player) - 20);
        context.assertTrue(player.getUuid().equals(data.getOwner("apollo_bow")),
                "A full draw shot skyward at y>=300 during the day must grant the Apollo bow");
        data.releaseId(player.getUuid(), "apollo_bow");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void weaponsReservedByOldVillageChestsAreReleased(TestContext context) {
        ServerWorld world = context.getWorld();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        UUID previous = data.getOwner("mjolnir");
        if (previous != null) {
            data.releaseId(previous, "mjolnir");
        }
        data.claimId(UniqueWeaponManager.WORLD_OWNER, "mjolnir");
        UniqueWeaponManager.releaseWorldOwnedWeapons(world);
        context.assertFalse(data.isClaimed("mjolnir"),
                "Weapons the old chest distribution reserved must become obtainable again");
        context.complete();
    }
}
