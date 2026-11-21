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

/** 完成した監視塔・国の門の位置と所有者を永続保存する。 */
public class DefenseData extends PersistentState {

    public static final String KEY = "kingdomunique_defense_data";

    public static final PersistentState.Type<DefenseData> TYPE = new PersistentState.Type<>(
            DefenseData::new,
            (nbt, lookup) -> new DefenseData(nbt),
            DataFixTypes.LEVEL
    );

    private final List<DefenseSite> watchtowers = new ArrayList<>();
    private final List<DefenseSite> gates = new ArrayList<>();

    public DefenseData() {
    }

    public DefenseData(NbtCompound nbt) {
        readSites(nbt.getList("watchtowers", NbtElement.COMPOUND_TYPE), watchtowers);
        readSites(nbt.getList("gates", NbtElement.COMPOUND_TYPE), gates);
    }

    private static void readSites(NbtList list, List<DefenseSite> target) {
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                target.add(new DefenseSite(
                        new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")),
                        UUID.fromString(c.getString("owner")),
                        c.getBoolean("open"),
                        c.contains("facing") ? Direction.byName(c.getString("facing")) : null));
            } catch (IllegalArgumentException ignored) {
                // 壊れたエントリは無視する。
            }
        }
    }

    public static DefenseData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.put("watchtowers", writeSites(watchtowers));
        nbt.put("gates", writeSites(gates));
        return nbt;
    }

    private static NbtList writeSites(List<DefenseSite> sites) {
        NbtList list = new NbtList();
        for (DefenseSite site : sites) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", site.pos().getX());
            c.putInt("y", site.pos().getY());
            c.putInt("z", site.pos().getZ());
            c.putString("owner", site.owner().toString());
            c.putBoolean("open", site.open());
            if (site.facing() != null) {
                c.putString("facing", site.facing().asString());
            }
            list.add(c);
        }
        return list;
    }

    public void addWatchtower(BlockPos pos, UUID owner, Direction facing) {
        watchtowers.add(new DefenseSite(pos.toImmutable(), owner, false, facing));
        markDirty();
    }

    public void addGate(BlockPos pos, UUID owner, Direction facing) {
        gates.add(new DefenseSite(pos.toImmutable(), owner, false, facing));
        markDirty();
    }

    public List<DefenseSite> watchtowers() {
        return watchtowers;
    }

    public List<DefenseSite> gates() {
        return gates;
    }

    public void setGateOpen(BlockPos pos, boolean open) {
        for (int i = 0; i < gates.size(); i++) {
            DefenseSite site = gates.get(i);
            if (site.pos().equals(pos) && site.open() != open) {
                gates.set(i, new DefenseSite(site.pos(), site.owner(), open, site.facing()));
                markDirty();
                return;
            }
        }
    }

    /** 防衛建築の登録（完成時に呼ばれる）。 */
    public static void register(ServerWorld world, BlockPos pos, BuildingType type, UUID owner,
                                Direction facing) {
        DefenseData data = getOrCreate(world);
        if (type == BuildingType.WATCHTOWER) {
            data.addWatchtower(pos, owner, facing);
        } else if (type == BuildingType.GATE) {
            data.addGate(pos, owner, facing);
        }
    }

    /** 壊された時の後始末。 */
    public static void remove(ServerWorld world, BlockPos pos) {
        DefenseData data = getOrCreate(world);
        if (data.watchtowers.removeIf(site -> site.pos().equals(pos))
                | data.gates.removeIf(site -> site.pos().equals(pos))) {
            data.markDirty();
        }
    }

    /**
     * 監視塔・門の情報。pos は設計図の origin（北西角）、open は門の開閉状態（監視塔では未使用）。
     * facing が null の旧データは pos を中心とした旧レイアウトとして扱う。
     */
    public record DefenseSite(BlockPos pos, UUID owner, boolean open, Direction facing) {
    }
}
