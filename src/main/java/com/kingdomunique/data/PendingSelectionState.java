package com.kingdomunique.data;

import com.kingdomunique.registry.ContentRegistry;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 未確定の選択画面（能力2択/遺物3択/呪物5択）を永続化する（Gemini 提示）。
 * ログアウト中に選択を確定しなくても、再ログイン時に再表示できるようにする。
 */
public final class PendingSelectionState extends PersistentState {

    private static final String KEY = "kingdomunique_pending_selections";

    private static final PersistentState.Type<PendingSelectionState> TYPE = new PersistentState.Type<>(
            PendingSelectionState::new, PendingSelectionState::fromNbt, DataFixTypes.LEVEL
    );

    /** プレイヤーUUID -> 未確定選択（kind + 選択肢）。 */
    private final Map<UUID, Pending> pendingMap = new HashMap<>();

    public record Pending(ContentRegistry.Kind kind, List<String> optionIds) {
    }

    private PendingSelectionState() {
    }

    public static PendingSelectionState get(ServerWorld world) {
        ServerWorld overworld = world.getServer().getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public void setPending(UUID playerUuid, ContentRegistry.Kind kind, List<String> options) {
        if (options == null || options.isEmpty()) {
            pendingMap.remove(playerUuid);
        } else {
            pendingMap.put(playerUuid, new Pending(kind, new ArrayList<>(options)));
        }
        markDirty();
    }

    public void clearPending(UUID playerUuid) {
        if (pendingMap.remove(playerUuid) != null) {
            markDirty();
        }
    }

    public boolean hasPending(UUID playerUuid) {
        return pendingMap.containsKey(playerUuid);
    }

    public Pending getPending(UUID playerUuid) {
        return pendingMap.get(playerUuid);
    }

    private static PendingSelectionState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PendingSelectionState state = new PendingSelectionState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound entry = players.getCompound(key);
                ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
                int idx = entry.getInt("kind");
                ContentRegistry.Kind kind = kinds[idx >= 0 && idx < kinds.length ? idx : 0];
                NbtList list = entry.getList("options", NbtElement.STRING_TYPE);
                List<String> options = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    options.add(list.getString(i));
                }
                if (!options.isEmpty()) {
                    state.pendingMap.put(uuid, new Pending(kind, options));
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れたUUIDは無視。
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<UUID, Pending> entry : pendingMap.entrySet()) {
            NbtCompound e = new NbtCompound();
            e.putInt("kind", entry.getValue().kind().ordinal());
            NbtList list = new NbtList();
            for (String id : entry.getValue().optionIds()) {
                list.add(NbtString.of(id));
            }
            e.put("options", list);
            players.put(entry.getKey().toString(), e);
        }
        nbt.put("players", players);
        return nbt;
    }
}
