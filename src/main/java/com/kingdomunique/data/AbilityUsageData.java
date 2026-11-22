package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** プレイヤーと能力ごとの次回発動可能ティックを保存する。 */
public final class AbilityUsageData extends PersistentState {

    public static final String KEY = "kingdomunique_ability_usage";
    public static final PersistentState.Type<AbilityUsageData> TYPE = new PersistentState.Type<>(
            AbilityUsageData::new,
            (nbt, registryLookup) -> new AbilityUsageData(nbt),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Map<String, Long>> readyTicks = new HashMap<>();

    public AbilityUsageData() {
    }

    public AbilityUsageData(NbtCompound nbt) {
        NbtCompound ready = nbt.getCompound("ready_ticks");
        for (String player : ready.getKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(player);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            NbtCompound abilities = ready.getCompound(player);
            Map<String, Long> ticks = new HashMap<>();
            for (String id : abilities.getKeys()) {
                ticks.put(id, abilities.getLong(id));
            }
            readyTicks.put(uuid, ticks);
        }
    }

    public static AbilityUsageData getOrCreate(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound ready = new NbtCompound();
        readyTicks.forEach((uuid, abilities) -> {
            NbtCompound entry = new NbtCompound();
            abilities.forEach(entry::putLong);
            ready.put(uuid.toString(), entry);
        });
        nbt.put("ready_ticks", ready);
        return nbt;
    }

    public long getReadyTick(UUID player, String abilityId) {
        return readyTicks.getOrDefault(player, Map.of()).getOrDefault(abilityId, 0L);
    }

    public void setReadyTick(UUID player, String abilityId, long tick) {
        readyTicks.computeIfAbsent(player, ignored -> new HashMap<>()).put(abilityId, tick);
        markDirty();
    }
}
