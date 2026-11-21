package com.kingdomunique.selection;

import com.kingdomunique.data.PendingSelectionState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.ContentRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

import java.util.UUID;

public final class SelectionGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void playersCannotRerollSelectionsOnDemand(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        context.assertFalse(SelectionManager.reopenOrOpen(player, ContentRegistry.Kind.RELIC),
                "/ku relic must not roll a fresh selection for regular players");
        context.assertFalse(PendingSelectionState.get(world).hasPending(player.getUuid()),
                "No pending selection may be created");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void selectionTriggersSurviveRestart(TestContext context) {
        var overworld = context.getWorld().getServer().getOverworld();
        WorldUniqueData saved = WorldUniqueData.getOrCreate(overworld);
        UUID player = UUID.randomUUID();
        context.assertTrue(saved.markSelectionTrigger(player, "diamond"), "The first diamond must trigger");
        WorldUniqueData reloaded = new WorldUniqueData(
                saved.writeNbt(new NbtCompound(), overworld.getRegistryManager()));
        context.assertFalse(reloaded.markSelectionTrigger(player, "diamond"),
                "A restart must not trigger the diamond selection again");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void secondTriggerWaitsForThePendingChoice(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        PendingSelectionState state = PendingSelectionState.get(world);
        SelectionManager.onDiamondObtained(player);
        PendingSelectionState.Pending first = state.getPending(player.getUuid());
        context.assertTrue(first != null && first.kind() == ContentRegistry.Kind.ABILITY, "The diamond must open an ability choice");

        SelectionManager.onNetherEntered(player);
        context.assertTrue(state.getPending(player.getUuid()).kind() == ContentRegistry.Kind.ABILITY,
                "Entering the Nether must not overwrite the pending ability choice");
        SelectionManager.handleChoice(player, ContentRegistry.Kind.ABILITY, first.optionIds().get(0));
        PendingSelectionState.Pending next = state.getPending(player.getUuid());
        context.assertTrue(next != null && next.kind() == ContentRegistry.Kind.CURSE,
                "The queued curse choice must open after the ability is chosen");

        WorldUniqueData.getOrCreate(world).releaseId(player.getUuid(), first.optionIds().get(0));
        state.clearPending(player.getUuid());
        context.complete();
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "selection_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
