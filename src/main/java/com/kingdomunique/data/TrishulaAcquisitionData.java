package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrishulaAcquisitionData extends PersistentState {
    private static final Type<TrishulaAcquisitionData> TYPE = new Type<>(
            TrishulaAcquisitionData::new, (nbt, lookup) -> new TrishulaAcquisitionData(nbt), DataFixTypes.LEVEL);
    private final Map<UUID, Set<Long>> progress = new HashMap<>();

    public TrishulaAcquisitionData() {
    }

    public TrishulaAcquisitionData(NbtCompound nbt) {
        for (String key : nbt.getKeys()) {
            try {
                Set<Long> positions = new HashSet<>();
                for (long position : nbt.getLongArray(key)) {
                    positions.add(position);
                }
                progress.put(UUID.fromString(key), positions);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static TrishulaAcquisitionData getOrCreate(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, "kingdomunique_trishula_acquisition");
    }

    public void recordWitherKill(UUID player) {
        if (progress.putIfAbsent(player, new HashSet<>()) == null) {
            markDirty();
        }
    }

    public int recordSpawner(UUID player, long position) {
        Set<Long> positions = progress.get(player);
        if (positions == null) {
            return 0;
        }
        if (positions.size() < 3 && positions.add(position)) {
            markDirty();
        }
        return positions.size();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        progress.forEach((player, positions) -> nbt.putLongArray(player.toString(),
                positions.stream().mapToLong(Long::longValue).toArray()));
        return nbt;
    }
}
