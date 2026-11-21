package com.kingdomunique.data;

import com.kingdomunique.registry.ContentRegistry.Kind;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.selection.SelectionManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class AcquisitionGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void milestonesDoNotRepeatAfterLoadingSavedState(TestContext context) {
        var world = context.getWorld();
        var player = player(context, UUID.randomUUID());
        var state = PendingSelectionState.get(world);
        SelectionManager.onDiamondObtained(player);
        SelectionManager.onNetherEntered(player);
        SelectionManager.onCitizenCountReached(player, 15);
        state.clearPending(player.getUuid());

        var saved = state.writeNbt(new NbtCompound(), world.getRegistryManager());
        var reloaded = PendingSelectionState.fromNbt(saved, world.getRegistryManager());
        for (Kind kind : Kind.values()) {
            context.assertFalse(reloaded.recordTrigger(player.getUuid(), kind),
                    "The completed " + kind + " milestone must not repeat after reloading");
        }
        context.assertTrue(reloaded.recordTrigger(UUID.randomUUID(), Kind.ABILITY),
                "A new player must still be eligible for their first milestone");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void manualSelectionRequiresOp(TestContext context) throws CommandSyntaxException {
        var world = context.getWorld();
        var state = PendingSelectionState.get(world);
        var dispatcher = world.getServer().getCommandManager().getDispatcher();
        var regular = player(context, UUID.randomUUID());
        var operator = player(context, UUID.randomUUID(), 2);
        var commands = List.of("ku select", "ku select ability", "ku select relic", "ku select curse",
                "ku ability", "ku relic", "ku curse");

        try {
            for (String command : commands) {
                boolean denied = false;
                try {
                    dispatcher.execute(command, regular.getCommandSource());
                } catch (CommandSyntaxException e) {
                    denied = true;
                }
                context.assertTrue(denied, "Non-OP must not be able to use /" + command);
                context.assertEquals(dispatcher.execute(command, operator.getCommandSource()), 1,
                        "OP must be able to use /" + command);
                context.assertTrue(state.hasPending(operator.getUuid()), "Command must open a selection");
                state.clearPending(operator.getUuid());
            }
            context.assertFalse(SelectionManager.reopenOrOpen(regular, Kind.RELIC),
                    "The manual selection request used by networking must also reject non-OP");
            context.assertFalse(state.hasPending(regular.getUuid()), "Non-OP must not receive a manual draw");
            context.complete();
        } finally {
            state.clearPending(regular.getUuid());
            state.clearPending(operator.getUuid());
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void savedSelectionCanBeConfirmedOnRejoin(TestContext context) {
        UUID uuid = UUID.randomUUID();
        var world = context.getWorld();
        var player = player(context, uuid);
        var state = PendingSelectionState.get(world);
        var ownership = WorldUniqueData.getOrCreate(world);
        state.setPending(uuid, Kind.CURSE, List.of(CurseRegistry.GLUTTONY));

        try {
            SelectionManager.handleChoice(player, Kind.CURSE, CurseRegistry.CHAOS);
            context.assertFalse(ownership.hasId(uuid, CurseRegistry.CHAOS),
                    "Rejoining must not allow confirming an option that was not saved");
            SelectionManager.handleChoice(player, Kind.CURSE, CurseRegistry.GLUTTONY);
            context.assertTrue(ownership.hasId(uuid, CurseRegistry.GLUTTONY),
                    "A fresh player session must be able to confirm the saved option");
            context.assertFalse(state.hasPending(uuid), "Confirming must clear the saved selection");
            context.complete();
        } finally {
            ownership.releaseId(uuid, CurseRegistry.GLUTTONY);
            state.clearPending(uuid);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void apolloRequiresAnActualBowShot(TestContext context) {
        var world = context.getWorld();
        var player = player(context, UUID.randomUUID());
        var ownership = WorldUniqueData.getOrCreate(world);
        String apolloId = "apollo_bow";
        UUID previousOwner = ownership.getOwner(apolloId);
        var pos = context.getAbsolutePos(BlockPos.ORIGIN);
        player.refreshPositionAndAngles(pos.getX() + 0.5, 300, pos.getZ() + 0.5, 0, -65);
        long time = world.getTimeOfDay();
        var bow = new ItemStack(Items.BOW);
        player.getInventory().setStack(0, bow);
        int maxUseTime = Items.BOW.getMaxUseTime(bow, player);

        try {
            // A reused GameTest world may have already placed the bow in a village chest.
            if (previousOwner != null) {
                ownership.releaseId(previousOwner, apolloId);
            }
            world.setTimeOfDay(6000);
            world.calculateAmbientDarkness();
            context.assertTrue(world.isDay(), "Test precondition: daytime");

            Items.BOW.onStoppedUsing(bow, world, player, maxUseTime - 20);
            context.assertFalse(ownership.isClaimed(apolloId), "Releasing without arrows must not count");

            player.getInventory().setStack(1, new ItemStack(Items.ARROW));
            Items.BOW.onStoppedUsing(bow, world, player, maxUseTime - 1);
            context.assertFalse(ownership.isClaimed(apolloId), "Releasing too early must not count");

            Items.BOW.onStoppedUsing(bow, world, player, maxUseTime - 20);
            context.assertTrue(ownership.hasId(player.getUuid(), apolloId),
                    "Firing a vanilla bow while meeting the conditions must award Apollo's bow");
            context.complete();
        } finally {
            ownership.releaseId(player.getUuid(), apolloId);
            if (previousOwner != null) {
                ownership.claimId(previousOwner, apolloId);
            }
            world.setTimeOfDay(time);
            world.calculateAmbientDarkness();
        }
    }

    private static ServerPlayerEntity player(TestContext context, UUID uuid) {
        return player(context, uuid, 0);
    }

    private static ServerPlayerEntity player(TestContext context, UUID uuid, int permissionLevel) {
        var world = context.getWorld();
        var profile = new GameProfile(uuid, "acquisition_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions()) {
            @Override
            protected int getPermissionLevel() {
                return permissionLevel;
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        return player;
    }
}
