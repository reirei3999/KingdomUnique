package com.kingdomunique.kingdom;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 完成済みの英雄召喚祭壇の核（ロードストーン）とその所有者を永続化する。 */
public final class SanctuaryData extends PersistentState {

    private static final String KEY = "kingdomunique_sanctuary_data";

    private static final PersistentState.Type<SanctuaryData> TYPE = new PersistentState.Type<>(
            SanctuaryData::new, SanctuaryData::fromNbt, DataFixTypes.LEVEL
    );

    private final Map<BlockPos, UUID> cores = new LinkedHashMap<>();

    private SanctuaryData() {
    }

    private static SanctuaryData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SanctuaryData data = new SanctuaryData();
        NbtList list = nbt.getList("cores", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                data.cores.put(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                        UUID.fromString(c.getString("owner")));
            } catch (IllegalArgumentException ignored) {
                // 壊れたエントリは無視する。
            }
        }
        return data;
    }

    public static SanctuaryData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<BlockPos, UUID> entry : cores.entrySet()) {
            NbtCompound c = new NbtCompound();
            BlockPos p = entry.getKey();
            c.putInt("x", p.getX());
            c.putInt("y", p.getY());
            c.putInt("z", p.getZ());
            c.putString("owner", entry.getValue().toString());
            list.add(c);
        }
        nbt.put("cores", list);
        return nbt;
    }

    public void register(BlockPos core, UUID owner) {
        cores.put(core.toImmutable(), owner);
        markDirty();
    }

    public boolean isSanctuary(BlockPos core) {
        return cores.containsKey(core);
    }

    public UUID getOwner(BlockPos core) {
        return cores.get(core);
    }

    public boolean hasSanctuary(UUID owner) {
        return cores.containsValue(owner);
    }
}
