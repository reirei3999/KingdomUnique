package com.kingdomunique.building;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 完成済み建築の効果を再起動後も維持するための永続データ。 */
public final class CompletedBuildingsData extends PersistentState {

    private static final String KEY = "kingdomunique_completed_buildings";

    private static final PersistentState.Type<CompletedBuildingsData> TYPE = new PersistentState.Type<>(
            CompletedBuildingsData::new, CompletedBuildingsData::fromNbt, DataFixTypes.LEVEL
    );

    private final List<Entry> entries = new ArrayList<>();

    private CompletedBuildingsData() {
    }

    private static CompletedBuildingsData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        CompletedBuildingsData data = new CompletedBuildingsData();
        NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                data.entries.add(new Entry(
                        new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                        BuildingType.valueOf(c.getString("type")),
                        UUID.fromString(c.getString("owner"))));
            } catch (IllegalArgumentException ignored) {
                // 壊れたエントリは無視する。
            }
        }
        return data;
    }

    public static CompletedBuildingsData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Entry e : entries) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", e.pos().getX());
            c.putInt("y", e.pos().getY());
            c.putInt("z", e.pos().getZ());
            c.putString("type", e.type().name());
            c.putString("owner", e.owner().toString());
            list.add(c);
        }
        nbt.put("entries", list);
        return nbt;
    }

    public void add(BlockPos pos, BuildingType type, UUID owner) {
        entries.removeIf(e -> e.pos().equals(pos));
        entries.add(new Entry(pos.toImmutable(), type, owner));
        markDirty();
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(BlockPos pos, BuildingType type, UUID owner) {
    }
}
