package com.kingdomunique.kingdom;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 王（プレイヤー）ごとの王国データをワールド永続保存する。 */
public class KingdomData extends PersistentState {

    public static final String KEY = "kingdomunique_kingdom_data";

    public static final PersistentState.Type<KingdomData> TYPE = new PersistentState.Type<>(
            KingdomData::new,
            (nbt, lookup) -> new KingdomData(nbt),
            DataFixTypes.LEVEL
    );

    /** 王UUID -> 王国情報。 */
    private final Map<UUID, KingdomInfo> kingdoms = new LinkedHashMap<>();

    public KingdomData() {
    }

    public KingdomData(NbtCompound nbt) {
        NbtList list = nbt.getList("kingdoms", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            try {
                UUID owner = UUID.fromString(entry.getString("owner"));
                kingdoms.put(owner, new KingdomInfo(
                        entry.getInt("centerX"),
                        entry.getInt("centerZ"),
                        entry.getInt("population"),
                        entry.getInt("populationCap"),
                        entry.getInt("houses"),
                        entry.getBoolean("heroSummoned"),
                        entry.getInt("heroSummonCount"),
                        entry.getBoolean("centerSet"),
                        entry.getInt("territoryRadius") > 0
                                ? entry.getInt("territoryRadius") : 100,
                        entry.getBoolean("villageGenerated"),
                        entry.getString("kingdomName")
                ));
            } catch (IllegalArgumentException ignored) {
                // 壊れたエントリは無視する。
            }
        }
    }

    public static KingdomData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, KingdomInfo> entry : kingdoms.entrySet()) {
            NbtCompound c = new NbtCompound();
            KingdomInfo info = entry.getValue();
            c.putString("owner", entry.getKey().toString());
            c.putInt("centerX", info.centerX());
            c.putInt("centerZ", info.centerZ());
            c.putInt("population", info.population());
            c.putInt("populationCap", info.populationCap());
            c.putInt("houses", info.houses());
            c.putBoolean("heroSummoned", info.heroSummoned());
            c.putInt("heroSummonCount", info.heroSummonCount());
            c.putBoolean("centerSet", info.centerSet());
            c.putInt("territoryRadius", info.territoryRadius());
            c.putBoolean("villageGenerated", info.villageGenerated());
            c.putString("kingdomName", info.kingdomName() == null ? "" : info.kingdomName());
            list.add(c);
        }
        nbt.put("kingdoms", list);
        return nbt;
    }

    public KingdomInfo get(UUID owner) {
        return kingdoms.get(owner);
    }

    public KingdomInfo getOrCreate(UUID owner, int centerX, int centerZ) {
        return kingdoms.computeIfAbsent(owner, ignored -> {
            markDirty();
            return new KingdomInfo(centerX, centerZ, 0, 10, 0, false, 0, false, 100, false, "");
        });
    }

    public void put(UUID owner, KingdomInfo info) {
        kingdoms.put(owner, info);
        markDirty();
    }

    public Map<UUID, KingdomInfo> all() {
        return java.util.Collections.unmodifiableMap(kingdoms);
    }

    /** 王国情報（イミュータブル）。 */
    public record KingdomInfo(int centerX, int centerZ, int population, int populationCap,
                              int houses, boolean heroSummoned, int heroSummonCount,
                              boolean centerSet, int territoryRadius,
                              boolean villageGenerated, String kingdomName) {
    }
}
