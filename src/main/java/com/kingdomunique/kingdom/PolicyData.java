package com.kingdomunique.kingdom;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 王国ごとの政策・大襲撃の状態を永続保存する。 */
public class PolicyData extends PersistentState {

    public static final String KEY = "kingdomunique_policy_data";

    public static final PersistentState.Type<PolicyData> TYPE = new PersistentState.Type<>(
            PolicyData::new,
            (nbt, lookup) -> new PolicyData(nbt),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, PolicyState> states = new HashMap<>();

    public PolicyData() {
    }

    public PolicyData(NbtCompound nbt) {
        NbtCompound owners = nbt.getCompound("owners");
        for (String key : owners.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound c = owners.getCompound(key);
                String policy = c.getString("policy");
                states.put(uuid, new PolicyState(
                        policy.isEmpty() ? null : policy,
                        c.getInt("lastCycle"),
                        c.getBoolean("raidActive"),
                        c.getInt("lastRaidDay"),
                        c.getBoolean("rewardGiven")
                ));
            } catch (IllegalArgumentException ignored) {
                // 壊れたエントリは無視する。
            }
        }
    }

    public static PolicyData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound owners = new NbtCompound();
        for (Map.Entry<UUID, PolicyState> entry : states.entrySet()) {
            NbtCompound c = new NbtCompound();
            PolicyState s = entry.getValue();
            c.putString("policy", s.policy() == null ? "" : s.policy());
            c.putInt("lastCycle", s.lastCycle());
            c.putBoolean("raidActive", s.raidActive());
            c.putInt("lastRaidDay", s.lastRaidDay());
            c.putBoolean("rewardGiven", s.rewardGiven());
            owners.put(entry.getKey().toString(), c);
        }
        nbt.put("owners", owners);
        return nbt;
    }

    public PolicyState get(UUID owner) {
        return states.get(owner);
    }

    public PolicyState getOrCreate(UUID owner) {
        return states.computeIfAbsent(owner, ignored -> {
            markDirty();
            return new PolicyState(null, -1, false, -1, false);
        });
    }

    public void put(UUID owner, PolicyState state) {
        states.put(owner, state);
        markDirty();
    }

    /** 政策・襲撃状態。policy は Policy#name()、未設定なら null。 */
    public record PolicyState(String policy, int lastCycle, boolean raidActive,
                              int lastRaidDay, boolean rewardGiven) {

        public PolicyState withPolicy(String value, int cycle) {
            return new PolicyState(value, cycle, raidActive, lastRaidDay, rewardGiven);
        }

        public PolicyState withRaid(boolean active, int day) {
            return new PolicyState(policy, lastCycle, active, day, false);
        }

        public PolicyState withReward() {
            return new PolicyState(policy, lastCycle, false, lastRaidDay, true);
        }
    }
}
