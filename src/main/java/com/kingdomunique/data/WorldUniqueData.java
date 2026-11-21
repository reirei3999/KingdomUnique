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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ワールド全体で一意な「能力・遺物・呪物」の排他管理データ。
 *
 * 1プレイヤーが複数のID（能力+遺物+呪物）を保持できるよう、
 * 所有情報を UUID -> Set&lt;ID&gt; で管理する。
 * 取得済みIDは世界全体の claimedIds に入り、他プレイヤーの抽選から除外される。
 */
public class WorldUniqueData extends PersistentState {

    public static final String KEY = "kingdomunique_world_unique_data";

    public static final PersistentState.Type<WorldUniqueData> TYPE = new PersistentState.Type<>(
            WorldUniqueData::new,
            (nbt, registryLookup) -> new WorldUniqueData(nbt),
            DataFixTypes.LEVEL
    );

    /** プレイヤーUUID -> 取得したID集合。 */
    private final Map<UUID, Set<String>> ownership = new HashMap<>();

    /** 世界で取得済みの全ID（排他リスト）。 */
    private final Set<String> claimedIds = new HashSet<>();

    private final List<VillageSite> villageSites = new ArrayList<>();
    private boolean initialized = false;

    /** 黄金の天秤: プレイヤーUUID -> 最後に収益を受け取った日。 */
    private final Map<UUID, Long> goldenScalePayoutDays = new HashMap<>();

    public WorldUniqueData() {
    }

    public WorldUniqueData(NbtCompound nbt) {
        NbtCompound ownershipNbt = nbt.getCompound("ownership");
        for (String key : ownershipNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                String csv = ownershipNbt.getString(key);
                Set<String> ids = new LinkedHashSet<>();
                for (String id : csv.split(",")) {
                    if (!id.isEmpty()) {
                        ids.add(id);
                        claimedIds.add(id);
                    }
                }
                if (!ids.isEmpty()) {
                    ownership.put(uuid, ids);
                }
            } catch (IllegalArgumentException ignored) {
                // 壊れたキーは無視する。
            }
        }

        NbtList claimedNbt = nbt.getList("claimed_ids", NbtElement.STRING_TYPE);
        for (int i = 0; i < claimedNbt.size(); i++) {
            String id = claimedNbt.getString(i);
            if (!id.isEmpty()) {
                claimedIds.add(id);
            }
        }

        NbtList sitesNbt = nbt.getList("village_sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < sitesNbt.size(); i++) {
            NbtCompound site = sitesNbt.getCompound(i);
            villageSites.add(new VillageSite(site.getInt("x"), site.getInt("z")));
        }
        initialized = nbt.getBoolean("initialized");

        NbtCompound payoutsNbt = nbt.getCompound("golden_scale_payouts");
        for (String key : payoutsNbt.getKeys()) {
            try {
                goldenScalePayoutDays.put(UUID.fromString(key), payoutsNbt.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // 壊れたキーは無視する。
            }
        }
    }

    public static WorldUniqueData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound ownershipNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : ownership.entrySet()) {
            ownershipNbt.putString(entry.getKey().toString(), String.join(",", entry.getValue()));
        }
        nbt.put("ownership", ownershipNbt);

        NbtList claimedNbt = new NbtList();
        for (String id : claimedIds) {
            claimedNbt.add(NbtString.of(id));
        }
        nbt.put("claimed_ids", claimedNbt);

        NbtList sitesNbt = new NbtList();
        for (VillageSite site : villageSites) {
            NbtCompound siteNbt = new NbtCompound();
            siteNbt.putInt("x", site.x());
            siteNbt.putInt("z", site.z());
            sitesNbt.add(siteNbt);
        }
        nbt.put("village_sites", sitesNbt);
        nbt.putBoolean("initialized", initialized);

        NbtCompound payoutsNbt = new NbtCompound();
        for (Map.Entry<UUID, Long> entry : goldenScalePayoutDays.entrySet()) {
            payoutsNbt.putLong(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("golden_scale_payouts", payoutsNbt);
        return nbt;
    }

    /** 排他取得。すでに誰かが取得したIDは再度取得できない。 */
    public boolean claimId(UUID playerUuid, String id) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (id == null || id.isEmpty() || claimedIds.contains(id)) {
            return false;
        }
        claimedIds.add(id);
        ownership.computeIfAbsent(playerUuid, ignored -> new LinkedHashSet<>()).add(id);
        markDirty();
        return true;
    }

    /** 所有権を別UUIDへ移す（チェスト報酬からプレイヤーへ受け渡す際に使用）。 */
    public boolean transferId(UUID from, UUID to, String id) {
        Set<String> fromIds = ownership.get(from);
        if (fromIds == null || !fromIds.remove(id)) {
            return false;
        }
        claimedIds.remove(id);
        if (fromIds.isEmpty()) {
            ownership.remove(from);
        }
        return claimId(to, id);
    }

    /** 指定プレイヤーからIDを解放する。 */
    public boolean releaseId(UUID playerUuid, String id) {
        Set<String> ids = ownership.get(playerUuid);
        if (ids == null || !ids.remove(id)) {
            return false;
        }
        claimedIds.remove(id);
        if (ids.isEmpty()) {
            ownership.remove(playerUuid);
        }
        markDirty();
        return true;
    }

    public List<String> getAvailableIds(Collection<String> candidates) {
        List<String> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        for (String id : candidates) {
            if (id != null && !claimedIds.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public List<String> getAvailableIds() {
        return getAvailableIds(ContentRegistry.ALL_IDS);
    }

    public boolean hasClaim(UUID playerUuid) {
        return ownership.containsKey(playerUuid) && !ownership.get(playerUuid).isEmpty();
    }

    public boolean hasId(UUID playerUuid, String id) {
        Set<String> ids = ownership.get(playerUuid);
        return ids != null && ids.contains(id);
    }

    /** 旧API互換（最初に取得したIDを返す）。 */
    public String getClaimedId(UUID playerUuid) {
        Set<String> ids = ownership.get(playerUuid);
        return ids == null || ids.isEmpty() ? null : ids.iterator().next();
    }

    public Set<String> getClaimedIds(UUID playerUuid) {
        Set<String> ids = ownership.get(playerUuid);
        return ids == null ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    public UUID getOwner(String id) {
        for (Map.Entry<UUID, Set<String>> entry : ownership.entrySet()) {
            if (entry.getValue().contains(id)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isClaimed(String id) {
        return claimedIds.contains(id);
    }

    public Set<String> getClaimedIds() {
        return Collections.unmodifiableSet(claimedIds);
    }

    public Map<UUID, Set<String>> getOwnership() {
        return Collections.unmodifiableMap(ownership);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean value) {
        this.initialized = value;
        markDirty();
    }

    public List<VillageSite> getVillageSites() {
        return Collections.unmodifiableList(villageSites);
    }

    public void setVillageSites(List<VillageSite> sites) {
        villageSites.clear();
        villageSites.addAll(sites);
        markDirty();
    }

    /** 黄金の天秤の収益をその日に未受領なら受領済みにして true を返す。 */
    public boolean claimGoldenScalePayout(UUID playerUuid, long day) {
        Long last = goldenScalePayoutDays.get(playerUuid);
        if (last != null && last == day) {
            return false;
        }
        goldenScalePayoutDays.put(playerUuid, day);
        markDirty();
        return true;
    }

    public record VillageSite(int x, int z) {
    }
}
