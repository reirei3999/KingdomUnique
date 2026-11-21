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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 未確定の選択と、一度だけの自動抽選をワールドに保存する。
 */
public final class PendingSelectionState extends PersistentState {

    private static final String KEY = "kingdomunique_pending_selections";

    private static final PersistentState.Type<PendingSelectionState> TYPE = new PersistentState.Type<>(
            PendingSelectionState::new, PendingSelectionState::fromNbt, DataFixTypes.LEVEL
    );

    /** プレイヤーUUID -> 未確定選択（kind + 選択肢）。 */
    private final Map<UUID, Pending> pendingMap = new HashMap<>();
    private final Map<UUID, EnumSet<ContentRegistry.Kind>> triggered = new HashMap<>();

    /** プレイヤーUUID -> 順番待ちの選択カテゴリ。 */
    private final Map<UUID, List<ContentRegistry.Kind>> queued = new HashMap<>();

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

    /** 別の選択が保留中に発生したトリガーを順番待ちに入れる（上書きで失わないため）。 */
    public void enqueue(UUID playerUuid, ContentRegistry.Kind kind) {
        queued.computeIfAbsent(playerUuid, ignored -> new ArrayList<>()).add(kind);
        markDirty();
    }

    /** 順番待ちの先頭を取り出す（なければ null）。 */
    public ContentRegistry.Kind pollQueued(UUID playerUuid) {
        List<ContentRegistry.Kind> kinds = queued.get(playerUuid);
        if (kinds == null || kinds.isEmpty()) {
            return null;
        }
        ContentRegistry.Kind kind = kinds.remove(0);
        if (kinds.isEmpty()) {
            queued.remove(playerUuid);
        }
        markDirty();
        return kind;
    }

    public boolean recordTrigger(UUID playerUuid, ContentRegistry.Kind kind) {
        if (!triggered.computeIfAbsent(playerUuid, ignored -> EnumSet.noneOf(ContentRegistry.Kind.class)).add(kind)) {
            return false;
        }
        markDirty();
        return true;
    }

    static PendingSelectionState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
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
        NbtCompound queuedNbt = nbt.getCompound("queued");
        ContentRegistry.Kind[] queuedKinds = ContentRegistry.Kind.values();
        for (String key : queuedNbt.getKeys()) {
            try {
                List<ContentRegistry.Kind> list = new ArrayList<>();
                for (int idx : queuedNbt.getIntArray(key)) {
                    if (idx >= 0 && idx < queuedKinds.length) {
                        list.add(queuedKinds[idx]);
                    }
                }
                if (!list.isEmpty()) {
                    state.queued.put(UUID.fromString(key), list);
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
        NbtCompound queuedNbt = new NbtCompound();
        for (Map.Entry<UUID, List<ContentRegistry.Kind>> entry : queued.entrySet()) {
            queuedNbt.putIntArray(entry.getKey().toString(),
                    entry.getValue().stream().mapToInt(Enum::ordinal).toArray());
        }
        nbt.put("queued", queuedNbt);
        NbtCompound triggers = new NbtCompound();
        triggered.forEach((uuid, kinds) -> {
            NbtCompound entry = new NbtCompound();
            kinds.forEach(kind -> entry.putBoolean(kind.name(), true));
            triggers.put(uuid.toString(), entry);
        });
        nbt.put("triggered", triggers);
        return nbt;
    }
}
