package com.kingdomunique.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.util.RewardUtil;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UniqueWeaponGrantGameTest {
    private static final String ID = "chronos_dagger";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "unique_grant_full_inventory")
    public void fullInventoryGrantPreservesUniqueWeapon(TestContext context) {
        var player = createConnectedPlayer(context, "full_grant");
        var data = WorldUniqueData.getOrCreate(context.getWorld());
        UUID owner = data.getOwner(ID);
        try {
            clearNearbyWeapons(player);
            if (owner != null) data.releaseId(owner, ID);
            for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
                player.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            UniqueWeaponTriggers.onEnhanced(player, 4);
            context.assertTrue(player.getUuid().equals(data.getOwner(ID)), "Grant must claim the weapon");
            int copies = countCopies(context, player);
            context.assertTrue(copies == 1,
                    "Full inventory grant must preserve exactly one weapon in inventory or world; found " + copies);
            context.complete();
        } finally {
            clearNearbyWeapons(player);
            data.releaseId(player.getUuid(), ID);
            if (owner != null) data.claimId(owner, ID);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "unique_royal_usurpation")
    public void royalUsurpationPreservesExactlyOneWeaponAfterDeath(TestContext context) {
        checkRoyalUsurpation(context, false, false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "unique_usurp_full_winner")
    public void royalUsurpationWithFullWinnerInventory(TestContext context) {
        checkRoyalUsurpation(context, false, true);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "unique_usurp_keep_inventory")
    public void royalUsurpationWithKeepInventory(TestContext context) {
        checkRoyalUsurpation(context, true, false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "unique_reward_delivery")
    public void physicalRewardFallbackAndPartialDelivery(TestContext context) {
        var player = createConnectedPlayer(context, "reward_delivery");
        try {
            clearNearbyWeapons(player);
            for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
                player.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            context.assertFalse(RewardUtil.grantPhysical(player, "missing_reward"), "Unknown rewards must not create an item");
            RewardUtil.deliver(player, ItemStack.EMPTY);
            context.assertTrue(RewardUtil.grantPhysical(player, ID), "Physical fallback must deliver a weapon");
            context.assertTrue(countCopies(context, player) == 1, "Full inventory fallback must drop exactly one weapon");
            player.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 63));
            RewardUtil.deliver(player, new ItemStack(Items.COBBLESTONE, 3));
            context.assertTrue(player.getInventory().getStack(0).getCount() == 64, "Partial delivery must fill the available space");
            int dropped = context.getWorld().getEntitiesByClass(ItemEntity.class,
                    player.getBoundingBox().expand(16), entity -> entity.getStack().isOf(Items.COBBLESTONE))
                    .stream().mapToInt(entity -> entity.getStack().getCount()).sum();
            context.assertTrue(dropped == 2, "Partial delivery must drop only the remainder, found " + dropped);
            context.complete();
        } finally {
            clearNearbyWeapons(player);
            for (var drop : context.getWorld().getEntitiesByClass(ItemEntity.class,
                    player.getBoundingBox().expand(16), entity -> entity.getStack().isOf(Items.COBBLESTONE))) {
                drop.discard();
            }
        }
    }

    private static void checkRoyalUsurpation(TestContext context, boolean keep, boolean fullWinner) {
        var victim = createConnectedPlayer(context, "usurp_victim");
        var winner = createConnectedPlayer(context, "usurp_winner");
        var world = context.getWorld();
        var data = WorldUniqueData.getOrCreate(world);
        UUID owner = data.getOwner(ID);
        boolean keepInventory = world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
        try {
            clearNearbyWeapons(victim);
            if (owner != null) data.releaseId(owner, ID);
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(keep, world.getServer());
            if (fullWinner) {
                for (int slot = 0; slot < winner.getInventory().main.size(); slot++) {
                    winner.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
            data.claimId(victim.getUuid(), ID);
            victim.getInventory().setStack(0, UniqueWeaponManager.createStackForId(ID));
            victim.setHealth(0.0F);
            victim.onDeath(world.getDamageSources().playerAttack(winner));
            context.assertTrue(winner.getUuid().equals(data.getOwner(ID)),
                    "Player death must execute royal usurpation and transfer ownership to the killer");
            int copies = countCopies(context, victim, winner);
            context.assertTrue(copies == 1,
                    "Royal usurpation must leave exactly one weapon across death drops and inventories; found " + copies);
            context.complete();
        } finally {
            clearNearbyWeapons(victim);
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(keepInventory, world.getServer());
            data.releaseId(victim.getUuid(), ID);
            data.releaseId(winner.getUuid(), ID);
            if (owner != null) data.claimId(owner, ID);
        }
    }

    private static void clearNearbyWeapons(ServerPlayerEntity player) {
        for (var drop : player.getServerWorld().getEntitiesByClass(ItemEntity.class,
                player.getBoundingBox().expand(16), entity -> ID.equals(entity.getStack().get(ModDataComponents.UNIQUE_WEAPON_ID)))) {
            drop.discard();
        }
    }

    private static int countCopies(TestContext context, ServerPlayerEntity... players) {
        int copies = 0;
        for (var player : players) {
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (ID.equals(stack.get(ModDataComponents.UNIQUE_WEAPON_ID))) copies += stack.getCount();
            }
        }
        for (var drop : context.getWorld().getEntitiesByClass(ItemEntity.class,
                players[0].getBoundingBox().expand(16), entity -> true)) {
            if (ID.equals(drop.getStack().get(ModDataComponents.UNIQUE_WEAPON_ID))) {
                copies += drop.getStack().getCount();
            }
        }
        return copies;
    }

    private static ServerPlayerEntity createConnectedPlayer(TestContext context, String name) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.setPosition(context.getAbsolutePos(new BlockPos(1, 1, 1)).toCenterPos());
        return player;
    }

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
