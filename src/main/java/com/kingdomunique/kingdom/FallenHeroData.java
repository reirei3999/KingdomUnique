package com.kingdomunique.kingdom;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 倒れた英雄（王UUID -> 死亡順の英雄データ）を永続化し、祭壇での復活に使う。 */
public final class FallenHeroData extends PersistentState {

    private static final String KEY = "kingdomunique_fallen_heroes";

    private static final PersistentState.Type<FallenHeroData> TYPE = new PersistentState.Type<>(
            FallenHeroData::new, FallenHeroData::fromNbt, DataFixTypes.LEVEL
    );

    private final Map<UUID, List<NbtCompound>> fallen = new LinkedHashMap<>();

    private FallenHeroData() {
    }

    private static FallenHeroData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        FallenHeroData data = new FallenHeroData();
        for (String key : nbt.getKeys()) {
            try {
                List<NbtCompound> heroes = new ArrayList<>();
                NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    heroes.add(list.getCompound(i));
                }
                data.fallen.put(UUID.fromString(key), heroes);
            } catch (IllegalArgumentException ignored) {
                // 壊れたキーは無視する。
            }
        }
        return data;
    }

    public static FallenHeroData getOrCreate(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        for (Map.Entry<UUID, List<NbtCompound>> entry : fallen.entrySet()) {
            NbtList list = new NbtList();
            list.addAll(entry.getValue());
            nbt.put(entry.getKey().toString(), list);
        }
        return nbt;
    }

    public void add(UUID owner, NbtCompound hero) {
        fallen.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(hero);
        markDirty();
    }

    public int count(UUID owner) {
        List<NbtCompound> heroes = fallen.get(owner);
        return heroes == null ? 0 : heroes.size();
    }

    /** 最も早く倒れた英雄のデータを取り出す（なければ null）。 */
    public NbtCompound poll(UUID owner) {
        List<NbtCompound> heroes = fallen.get(owner);
        if (heroes == null || heroes.isEmpty()) {
            return null;
        }
        NbtCompound hero = heroes.remove(0);
        markDirty();
        return hero;
    }
}
