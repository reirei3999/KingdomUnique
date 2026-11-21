package com.kingdomunique.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UniqueWeaponGrantGameTest {
    private static final String ID = "chronos_dagger";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void chronosDaggerGrantedFromVillageChestAtPlusFive(TestContext context) {
        var world = context.getWorld();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        UUID originalOwner = data.getOwner(ID);
        var originalSites = new ArrayList<>(data.getVillageSites());

        BlockPos chestPos = context.getAbsolutePos(new BlockPos(3, 1, 3));
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(UniqueWeapons.CHRONOS_DAGGER));

        List<ItemStack> given = new ArrayList<>();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "chronos_test"), SyncedClientOptions.createDefault()) {
            @Override
            public boolean giveItemStack(ItemStack stack) {
                given.add(stack.copy());
                return true;
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        player.setPosition(chestPos.toCenterPos());

        try {
            if (originalOwner != null) {
                data.releaseId(originalOwner, ID);
            }
            data.claimId(UniqueWeaponManager.WORLD_OWNER, ID);
            data.setVillageSites(List.of(new WorldUniqueData.VillageSite(chestPos.getX() - 2, chestPos.getZ() - 2)));

            UniqueWeaponTriggers.onEnhanced(player, 3);
            context.assertTrue(given.isEmpty(), "+3 must not grant the Chronos Dagger");

            UniqueWeaponTriggers.onEnhanced(player, 5);
            context.assertTrue(player.getUuid().equals(data.getOwner(ID)),
                    "Reaching +5 must give the player ownership of the Chronos Dagger still in a village chest");
            context.assertTrue(given.size() == 1
                            && ID.equals(given.get(0).get(ModDataComponents.UNIQUE_WEAPON_ID)),
                    "Reaching +5 must hand the Chronos Dagger to the player, got " + given);
            context.assertTrue(chest.getStack(0).isEmpty(),
                    "The village chest copy must be removed so only one Chronos Dagger exists");

            UniqueWeaponTriggers.onEnhanced(player, 6);
            context.assertTrue(given.size() == 1, "The Chronos Dagger must only be granted once");
            context.complete();
        } finally {
            data.releaseId(player.getUuid(), ID);
            data.releaseId(UniqueWeaponManager.WORLD_OWNER, ID);
            if (originalOwner != null) {
                data.claimId(originalOwner, ID);
            }
            data.setVillageSites(originalSites);
        }
    }
}
