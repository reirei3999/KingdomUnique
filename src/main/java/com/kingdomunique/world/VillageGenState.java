package com.kingdomunique.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

/** 初期村の生成予定と完了状態を永続化する（再起動時の重複生成を防止）。 */
public final class VillageGenState extends PersistentState {

    public static final String KEY = "kingdomunique_village_gen";

    public static final PersistentState.Type<VillageGenState> TYPE = new PersistentState.Type<>(
            VillageGenState::new,
            VillageGenState::fromNbt,
            DataFixTypes.LEVEL
    );

    public boolean initialized = false;
    public final List<VillageEntry> pendingVillages = new ArrayList<>();

    public VillageGenState() {
    }

    private static VillageGenState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        VillageGenState state = new VillageGenState();
        state.initialized = nbt.getBoolean("initialized");
        NbtList list = nbt.getList("villages", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            state.pendingVillages.add(new VillageEntry(
                    c.getInt("x"), c.getInt("z"), c.getBoolean("built")));
        }
        return state;
    }

    public static VillageGenState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putBoolean("initialized", initialized);
        NbtList list = new NbtList();
        for (VillageEntry entry : pendingVillages) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", entry.x);
            c.putInt("z", entry.z);
            c.putBoolean("built", entry.built);
            list.add(c);
        }
        nbt.put("villages", list);
        return nbt;
    }

    /** 村の生成予定エントリ。 */
    public static final class VillageEntry {
        public int x;
        public int z;
        public boolean built;
        long retryAfter;

        public VillageEntry(int x, int z, boolean built) {
            this.x = x;
            this.z = z;
            this.built = built;
        }
    }
}
