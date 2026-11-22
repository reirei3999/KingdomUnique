package com.kingdomunique.data;

import com.kingdomunique.registry.ContentRegistry.Kind;
import com.kingdomunique.selection.SelectionManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public final class PendingSelectionQueueGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void milestonesQueueUntilConfirmed(TestContext context) {
        var player = player(context, UUID.randomUUID());
        var uuid = player.getUuid();
        var state = PendingSelectionState.get(context.getWorld());
        var ownership = WorldUniqueData.getOrCreate(context.getWorld());
        String curse = null;
        String ability = null;
        try {
            SelectionManager.onNetherEntered(player);
            SelectionManager.onDiamondObtained(player);
            var pending = state.getPending(uuid);
            context.assertTrue(pending != null && pending.kind() == Kind.CURSE,
                    "The nether curse must remain pending after the diamond milestone");
            curse = pending.optionIds().getFirst();
            SelectionManager.handleChoice(player, Kind.ABILITY, "invalid");
            context.assertEquals(state.getPending(uuid), pending, "An invalid choice must not consume the head");
            SelectionManager.handleChoice(player, Kind.CURSE, curse);
            context.assertTrue(ownership.hasId(uuid, curse), "The curse must be granted");
            pending = state.getPending(uuid);
            context.assertTrue(pending != null && pending.kind() == Kind.ABILITY,
                    "The ability must become pending after confirming the curse");
            ability = pending.optionIds().getFirst();
            SelectionManager.handleChoice(player, Kind.ABILITY, ability);
            context.assertTrue(ownership.hasId(uuid, ability), "The ability must be granted");
            context.assertFalse(state.hasPending(uuid), "Both confirmations must empty the queue");
            context.complete();
        } finally {
            if (curse != null) ownership.releaseId(uuid, curse);
            if (ability != null) ownership.releaseId(uuid, ability);
            while (state.hasPending(uuid)) state.clearPending(uuid);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void queueSurvivesReloadAndRefresh(TestContext context) {
        var lookup = context.getWorld().getRegistryManager();
        var state = PendingSelectionState.fromNbt(new NbtCompound(), lookup);
        var uuid = UUID.randomUUID();
        state.setPending(uuid, Kind.CURSE, List.of("curse"));
        state.setPending(uuid, Kind.ABILITY, List.of("ability"));
        state.setPending(uuid, Kind.CURSE, List.of("refreshed"));
        var loaded = PendingSelectionState.fromNbt(state.writeNbt(new NbtCompound(), lookup), lookup);
        context.assertEquals(loaded.getPending(uuid), new PendingSelectionState.Pending(Kind.CURSE, List.of("refreshed")),
                "Refreshing must preserve queue position and survive saving");
        loaded.clearPending(uuid);
        context.assertEquals(loaded.getPending(uuid), new PendingSelectionState.Pending(Kind.ABILITY, List.of("ability")),
                "The second selection must survive saving");
        loaded.clearPending(uuid);
        context.assertFalse(loaded.hasPending(uuid), "Clearing both heads must empty the queue");
        loaded.clearPending(uuid);
        state.setPending(uuid, Kind.ABILITY, List.of());
        context.assertEquals(state.getPending(uuid).kind(), Kind.CURSE, "Removing the tail must preserve the head");
        state.setPending(uuid, Kind.CURSE, null);
        context.assertFalse(state.hasPending(uuid), "Null options must remove that kind");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void legacySelectionStillLoads(TestContext context) {
        var uuid = UUID.randomUUID();
        var entry = new NbtCompound();
        entry.putInt("kind", Kind.CURSE.ordinal());
        var options = new NbtList();
        options.add(NbtString.of("legacy"));
        entry.put("options", options);
        var players = new NbtCompound();
        players.put(uuid.toString(), entry);
        var saved = new NbtCompound();
        saved.put("players", players);
        var loaded = PendingSelectionState.fromNbt(saved, context.getWorld().getRegistryManager());
        context.assertEquals(loaded.getPending(uuid), new PendingSelectionState.Pending(Kind.CURSE, List.of("legacy")),
                "Legacy single selections must still load");
        context.complete();
    }

    private static ServerPlayerEntity player(TestContext context, UUID uuid) {
        var world = context.getWorld();
        var profile = new GameProfile(uuid, "queue_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        return player;
    }
}
