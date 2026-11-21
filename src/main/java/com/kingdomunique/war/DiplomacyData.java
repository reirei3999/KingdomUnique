package com.kingdomunique.war;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 王国間の同盟関係を永続保存する（デフォルトは敵対）。 */
public class DiplomacyData extends PersistentState {

    public static final String KEY = "kingdomunique_diplomacy_data";

    public static final PersistentState.Type<DiplomacyData> TYPE = new PersistentState.Type<>(
            DiplomacyData::new,
            (nbt, lookup) -> new DiplomacyData(nbt),
            DataFixTypes.LEVEL
    );

    /** 正規化済み "uuidA|uuidB" の同盟ペア。 */
    private final Set<String> alliances = new HashSet<>();

    public DiplomacyData() {
    }

    public DiplomacyData(NbtCompound nbt) {
        NbtList list = nbt.getList("alliances", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            alliances.add(list.getString(i));
        }
    }

    /** どのディメンションから呼ばれても、外交データはオーバーワールドに保存する。 */
    public static DiplomacyData getOrCreate(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (String pair : alliances) {
            list.add(net.minecraft.nbt.NbtString.of(pair));
        }
        nbt.put("alliances", list);
        return nbt;
    }

    public static String key(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0
                ? a + "|" + b : b + "|" + a;
    }

    public boolean isAllied(UUID a, UUID b) {
        return alliances.contains(key(a, b));
    }

    public void ally(UUID a, UUID b) {
        alliances.add(key(a, b));
        markDirty();
    }

    public void declareWar(UUID a, UUID b) {
        alliances.remove(key(a, b));
        markDirty();
    }
}
