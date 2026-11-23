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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 未確定の選択と、一度だけの自動抽選をワールドに保存する。
 */
public final class PendingSelectionState extends PersistentState {

    private static final String KEY = "kingdomunique_pending_selections";

    private static final PersistentState.Type<PendingSelectionState> TYPE = new PersistentState.Type<>(
            PendingSelectionState::new, PendingSelectionState::fromNbt, DataFixTypes.LEVEL
    );

    /** プレイヤーUUID -> 未確定選択の順序付きキュー。 */
    private final Map<UUID, List<Pending>> pendingMap = new HashMap<>();
    private final Map<UUID, EnumSet<ContentRegistry.Kind>> triggered = new HashMap<>();
    private final Set<UUID> ancientCityTriggered = new HashSet<>();

    public record Pending(ContentRegistry.Kind kind, List<String> optionIds) {
    }

    private PendingSelectionState() {
    }

    public static PendingSelectionState get(ServerWorld world) {
        ServerWorld overworld = world.getServer().getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public void setPending(UUID playerUuid, ContentRegistry.Kind kind, List<String> options) {
        List<Pending> queue = pendingMap.computeIfAbsent(playerUuid, ignored -> new ArrayList<>());
        int index = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).kind() == kind) {
                index = i;
                break;
            }
        }
        if (options == null || options.isEmpty()) {
            if (index >= 0) {
                queue.remove(index);
            }
        } else {
            Pending pending = new Pending(kind, new ArrayList<>(options));
            if (index >= 0) {
                queue.set(index, pending);
            } else {
                queue.add(pending);
            }
        }
        if (queue.isEmpty()) {
            pendingMap.remove(playerUuid);
        }
        markDirty();
    }

    public void clearPending(UUID playerUuid) {
        List<Pending> queue = pendingMap.get(playerUuid);
        if (queue != null) {
            queue.removeFirst();
            if (queue.isEmpty()) {
                pendingMap.remove(playerUuid);
            }
            markDirty();
        }
    }

    public boolean hasPending(UUID playerUuid) {
        return pendingMap.containsKey(playerUuid);
    }

    public Pending getPending(UUID playerUuid) {
        List<Pending> queue = pendingMap.get(playerUuid);
        return queue == null ? null : queue.getFirst();
    }

    public boolean recordTrigger(UUID playerUuid, ContentRegistry.Kind kind) {
        if (!triggered.computeIfAbsent(playerUuid, ignored -> EnumSet.noneOf(ContentRegistry.Kind.class)).add(kind)) {
            return false;
        }
        markDirty();
        return true;
    }

    public boolean recordAncientCityTrigger(UUID playerUuid) {
        if (!ancientCityTriggered.add(playerUuid)) return false;
        markDirty();
        return true;
    }

    public void enqueuePending(UUID playerUuid, ContentRegistry.Kind kind, List<String> options) {
        pendingMap.computeIfAbsent(playerUuid, ignored -> new ArrayList<>())
                .add(new Pending(kind, new ArrayList<>(options)));
        markDirty();
    }

    static PendingSelectionState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PendingSelectionState state = new PendingSelectionState();
        NbtList ancientCity = nbt.getList("ancientCityTriggered", NbtElement.STRING_TYPE);
        for (int i = 0; i < ancientCity.size(); i++) {
            try {
                state.ancientCityTriggered.add(UUID.fromString(ancientCity.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound entry = players.getCompound(key);
                if (entry.contains("queue", NbtElement.LIST_TYPE)) {
                    NbtList queue = entry.getList("queue", NbtElement.COMPOUND_TYPE);
                    for (int i = 0; i < queue.size(); i++) {
                        readPending(state, uuid, queue.getCompound(i));
                    }
                } else {
                    readPending(state, uuid, entry);
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れたUUIDは無視。
            }
        }
        NbtCompound triggers = nbt.getCompound("triggered");
        for (String key : triggers.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound entry = triggers.getCompound(key);
                EnumSet<ContentRegistry.Kind> kinds = EnumSet.noneOf(ContentRegistry.Kind.class);
                for (ContentRegistry.Kind kind : ContentRegistry.Kind.values()) {
                    if (entry.getBoolean(kind.name())) {
                        kinds.add(kind);
                    }
                }
                if (!kinds.isEmpty()) {
                    state.triggered.put(uuid, kinds);
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れたUUIDは無視。
            }
        }
        return state;
    }

    private static void readPending(PendingSelectionState state, UUID uuid, NbtCompound entry) {
        ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
        int idx = entry.getInt("kind");
        ContentRegistry.Kind kind = kinds[idx >= 0 && idx < kinds.length ? idx : 0];
        NbtList list = entry.getList("options", NbtElement.STRING_TYPE);
        List<String> options = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            options.add(list.getString(i));
        }
        if (!options.isEmpty()) {
            state.enqueuePending(uuid, kind, options);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<UUID, List<Pending>> entry : pendingMap.entrySet()) {
            NbtCompound e = new NbtCompound();
            NbtList queue = new NbtList();
            for (Pending pending : entry.getValue()) {
                NbtCompound selection = new NbtCompound();
                selection.putInt("kind", pending.kind().ordinal());
                NbtList list = new NbtList();
                for (String id : pending.optionIds()) {
                    list.add(NbtString.of(id));
                }
                selection.put("options", list);
                queue.add(selection);
            }
            e.put("queue", queue);
            players.put(entry.getKey().toString(), e);
        }
        nbt.put("players", players);
        NbtCompound triggers = new NbtCompound();
        triggered.forEach((uuid, kinds) -> {
            NbtCompound entry = new NbtCompound();
            kinds.forEach(kind -> entry.putBoolean(kind.name(), true));
            triggers.put(uuid.toString(), entry);
        });
        nbt.put("triggered", triggers);
        NbtList ancientCity = new NbtList();
        ancientCityTriggered.forEach(uuid -> ancientCity.add(NbtString.of(uuid.toString())));
        nbt.put("ancientCityTriggered", ancientCity);
        return nbt;
    }
}
