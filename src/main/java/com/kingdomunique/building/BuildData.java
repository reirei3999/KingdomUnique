package com.kingdomunique.building;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 進行中の設計図建築を永続保存する。 */
public class BuildData extends PersistentState {

    public static final String KEY = "kingdomunique_build_data";

    public static final PersistentState.Type<BuildData> TYPE = new PersistentState.Type<>(
            BuildData::new,
            (nbt, lookup) -> new BuildData(nbt),
            DataFixTypes.LEVEL
    );

    private final List<BuildSite> sites = new ArrayList<>();

    public BuildData() {
    }

    public BuildData(NbtCompound nbt) {
        NbtList list = nbt.getList("sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                sites.add(new BuildSite(
                        new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                        BuildingType.valueOf(c.getString("type")),
                        c.getInt("progress"),
                        c.getBoolean("ready"),
                        UUID.fromString(c.getString("owner")),
                        net.minecraft.util.math.Direction.byName(c.getString("facing")) == null
                                ? net.minecraft.util.math.Direction.SOUTH
                                : net.minecraft.util.math.Direction.byName(c.getString("facing"))
                ));
            } catch (IllegalArgumentException ignored) {
                // 壊れたサイトは無視する。
            }
        }
    }

    public static BuildData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (BuildSite site : sites) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", site.pos().getX());
            c.putInt("y", site.pos().getY());
            c.putInt("z", site.pos().getZ());
            c.putString("type", site.type().name());
            c.putInt("progress", site.progress());
            c.putBoolean("ready", site.ready());
            c.putString("owner", site.owner().toString());
            c.putString("facing", site.facing().getName());
            list.add(c);
        }
        nbt.put("sites", list);
        return nbt;
    }

    public List<BuildSite> sites() {
        return sites;
    }

    public void add(BuildSite site) {
        sites.add(site);
        markDirty();
    }

    public void remove(BuildSite site) {
        sites.remove(site);
        markDirty();
    }

    public void markChanged() {
        markDirty();
    }

    /** 建築サイト。progress は tick 数。 */
    public record BuildSite(BlockPos pos, BuildingType type, int progress, boolean ready, UUID owner, Direction facing) {
        public BuildSite withProgress(int value) {
            return new BuildSite(pos, type, value, ready, owner, facing);
        }

        public BuildSite withReady(boolean value) {
            return new BuildSite(pos, type, progress, value, owner, facing);
        }
    }
}
