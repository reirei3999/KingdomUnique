package com.kingdomunique.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 各プレイヤーに割り当てた初期スポーン地点を永続保存する。 */
public class PlayerSpawnData extends PersistentState {

    public static final String KEY = "kingdomunique_player_spawns";

    public static final PersistentState.Type<PlayerSpawnData> TYPE = new PersistentState.Type<>(
            PlayerSpawnData::new,
            (nbt, lookup) -> new PlayerSpawnData(nbt),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, BlockPos> spawns = new HashMap<>();

    public PlayerSpawnData() {
    }

    public PlayerSpawnData(NbtCompound nbt) {
        NbtList list = nbt.getList("spawns", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                spawns.put(UUID.fromString(c.getString("uuid")),
                        new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
            } catch (IllegalArgumentException ignored) {
                // 不正なUUIDは無視
            }
        }
    }

    public static PlayerSpawnData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, BlockPos> entry : spawns.entrySet()) {
            NbtCompound c = new NbtCompound();
            BlockPos p = entry.getValue();
            c.putString("uuid", entry.getKey().toString());
            c.putInt("x", p.getX());
            c.putInt("y", p.getY());
            c.putInt("z", p.getZ());
            list.add(c);
        }
        nbt.put("spawns", list);
        return nbt;
    }

    public boolean has(UUID uuid) {
        return spawns.containsKey(uuid);
    }

    public BlockPos get(UUID uuid) {
        return spawns.get(uuid);
    }

    public void put(UUID uuid, BlockPos pos) {
        spawns.put(uuid, pos.toImmutable());
        markDirty();
    }

    public java.util.Collection<BlockPos> all() {
        return spawns.values();
    }
}
