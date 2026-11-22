package com.kingdomunique.selection;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.HashSet;
import java.util.Set;

public final class AncientCityRelicTrigger {
    private static final Set<ServerPlayerEntity> OPENED = new HashSet<>();

    private AncientCityRelicTrigger() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var players = Set.copyOf(OPENED);
            OPENED.clear();
            for (var player : players) {
                if (!player.isDisconnected()) SelectionManager.onAncientCityChestOpened(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OPENED.clear());
    }

    public static void onChestOpened(ServerPlayerEntity player, BlockPos pos) {
        var world = player.getServerWorld();
        var structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.ANCIENT_CITY);
        if (structure != null && world.getStructureAccessor().getStructureAt(pos, structure).hasChildren()) {
            OPENED.add(player);
        }
    }
}
