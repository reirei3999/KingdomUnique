package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** プレイヤーごとの累計撃破数などの統計。 */
public class PlayerStatsData extends PersistentState {

    public static final String KEY = "kingdomunique_player_stats";

    public static final PersistentState.Type<PlayerStatsData> TYPE = new PersistentState.Type<>(
            PlayerStatsData::new,
            (nbt, lookup) -> new PlayerStatsData(nbt),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Integer> kills = new HashMap<>();

    public PlayerStatsData() {
    }

    public PlayerStatsData(NbtCompound nbt) {
        NbtList list = nbt.getList("kills", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                kills.put(UUID.fromString(c.getString("uuid")), c.getInt("value"));
            } catch (IllegalArgumentException ignored) {
                // 不正なUUIDは無視
            }
        }
    }

    /** どのディメンションから呼ばれても、戦績データはオーバーワールドに保存する。 */
    public static PlayerStatsData getOrCreate(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("uuid", entry.getKey().toString());
            c.putInt("value", entry.getValue());
            list.add(c);
        }
        nbt.put("kills", list);
        return nbt;
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public int increment(UUID uuid) {
        int value = getKills(uuid) + 1;
        kills.put(uuid, value);
        markDirty();
        return value;
    }
}
