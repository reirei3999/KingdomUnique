package com.kingdomunique.war;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 王同士の外交関係（中立・同盟・戦争）を永続化する（Gemini 提示）。 */
public final class KingdomDiplomacyState extends PersistentState {

    private static final String KEY = "kingdomunique_diplomacy";

    private static final PersistentState.Type<KingdomDiplomacyState> TYPE = new PersistentState.Type<>(
            KingdomDiplomacyState::new, KingdomDiplomacyState::fromNbt, DataFixTypes.LEVEL
    );

    public enum Relation {
        NEUTRAL, ALLY, WAR
    }

    /** "UUID1:UUID2"（辞書順） -> 関係。 */
    private final Map<String, Relation> relations = new HashMap<>();
    /** 同盟申請中（申請者 -> 相手）。 */
    private final Map<UUID, UUID> pendingAllyRequests = new HashMap<>();

    private KingdomDiplomacyState() {
    }

    public static KingdomDiplomacyState get(ServerWorld world) {
        ServerWorld overworld = world.getServer().getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    private static String pairKey(UUID a, UUID b) {
        String s1 = a.toString();
        String s2 = b.toString();
        return s1.compareTo(s2) < 0 ? s1 + ":" + s2 : s2 + ":" + s1;
    }

    public Relation getRelation(UUID kingA, UUID kingB) {
        if (kingA == null || kingB == null) {
            return Relation.NEUTRAL;
        }
        if (kingA.equals(kingB)) {
            return Relation.ALLY;
        }
        return relations.getOrDefault(pairKey(kingA, kingB), Relation.NEUTRAL);
    }

    public void setRelation(UUID kingA, UUID kingB, Relation relation) {
        if (kingA == null || kingB == null || kingA.equals(kingB)) {
            return;
        }
        String key = pairKey(kingA, kingB);
        if (relation == Relation.NEUTRAL) {
            relations.remove(key);
        } else {
            relations.put(key, relation);
        }
        markDirty();
    }

    public boolean isAtWar(UUID kingA, UUID kingB) {
        return getRelation(kingA, kingB) == Relation.WAR;
    }

    public boolean isAlly(UUID kingA, UUID kingB) {
        return kingA != null && kingA.equals(kingB) || getRelation(kingA, kingB) == Relation.ALLY;
    }

    public void sendAllyRequest(UUID sender, UUID target) {
        pendingAllyRequests.put(sender, target);
    }

    public boolean hasAllyRequestFrom(UUID sender, UUID receiver) {
        return receiver.equals(pendingAllyRequests.get(sender));
    }

    public void clearAllyRequest(UUID sender) {
        pendingAllyRequests.remove(sender);
    }

    private static KingdomDiplomacyState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        KingdomDiplomacyState state = new KingdomDiplomacyState();
        NbtList list = nbt.getList("relations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                state.relations.put(c.getString("pair"), Relation.valueOf(c.getString("rel")));
            } catch (IllegalArgumentException ignored) {
                // 壊れた関係は無視。
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("pair", entry.getKey());
            c.putString("rel", entry.getValue().name());
            list.add(c);
        }
        nbt.put("relations", list);
        return nbt;
    }
}
