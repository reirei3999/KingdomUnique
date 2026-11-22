package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 再ログイン・再起動をまたいで保持する能力のクールタイムと1日の使用回数。 */
public class AbilityUsageData extends PersistentState {

    public static final String KEY = "kingdomunique_ability_usage";

    public static final PersistentState.Type<AbilityUsageData> TYPE = new PersistentState.Type<>(
            AbilityUsageData::new,
            (nbt, registryLookup) -> new AbilityUsageData(nbt),
            DataFixTypes.LEVEL
    );

    /** プレイヤー -> 能力ID -> 次回発動可能ティック。 */
    private final Map<UUID, Map<String, Long>> readyTicks = new HashMap<>();
    /** プレイヤー -> 能力ID -> {日, 使用回数}。 */
    private final Map<UUID, Map<String, long[]>> dailyUses = new HashMap<>();

    public AbilityUsageData() {
    }

    public AbilityUsageData(NbtCompound nbt) {
        NbtCompound ready = nbt.getCompound("ready_ticks");
        for (String player : ready.getKeys()) {
            UUID uuid = parse(player);
            if (uuid == null) {
                continue;
            }
            NbtCompound abilities = ready.getCompound(player);
            for (String id : abilities.getKeys()) {
                readyTicks.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(id, abilities.getLong(id));
            }
        }
        NbtCompound uses = nbt.getCompound("daily_uses");
        for (String player : uses.getKeys()) {
            UUID uuid = parse(player);
            if (uuid == null) {
                continue;
            }
            NbtCompound abilities = uses.getCompound(player);
            for (String id : abilities.getKeys()) {
                NbtCompound entry = abilities.getCompound(id);
                dailyUses.computeIfAbsent(uuid, ignored -> new HashMap<>())
                        .put(id, new long[]{entry.getLong("day"), entry.getInt("count")});
            }
        }
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
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
        NbtCompound uses = new NbtCompound();
        dailyUses.forEach((uuid, abilities) -> {
            NbtCompound entry = new NbtCompound();
            abilities.forEach((id, value) -> {
                NbtCompound use = new NbtCompound();
                use.putLong("day", value[0]);
                use.putInt("count", (int) value[1]);
                entry.put(id, use);
            });
            uses.put(uuid.toString(), entry);
        });
        nbt.put("daily_uses", uses);
        return nbt;
    }

    public long getReadyTick(UUID player, String abilityId) {
        return readyTicks.getOrDefault(player, Map.of()).getOrDefault(abilityId, 0L);
    }

    public Map<String, Long> getReadyTicks(UUID player) {
        return Map.copyOf(readyTicks.getOrDefault(player, Map.of()));
    }

    public void setReadyTick(UUID player, String abilityId, long tick) {
        readyTicks.computeIfAbsent(player, ignored -> new HashMap<>()).put(abilityId, tick);
        markDirty();
    }

    /** 指定日の使用回数（日が変わっていれば0）。 */
    public int getUses(UUID player, String abilityId, long day) {
        long[] entry = dailyUses.getOrDefault(player, Map.of()).get(abilityId);
        return entry == null || entry[0] != day ? 0 : (int) entry[1];
    }

    public int addUse(UUID player, String abilityId, long day) {
        int count = getUses(player, abilityId, day) + 1;
        dailyUses.computeIfAbsent(player, ignored -> new HashMap<>()).put(abilityId, new long[]{day, count});
        markDirty();
        return count;
    }
}
