package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.UUID;

public final class WeaponAcquisitionData extends PersistentState {
    private static final String KEY = "kingdomunique_weapon_acquisition";
    private static final Type<WeaponAcquisitionData> TYPE = new Type<>(WeaponAcquisitionData::new,
            (nbt, lookup) -> new WeaponAcquisitionData(nbt), DataFixTypes.LEVEL);

    private final NbtCompound moonKills;
    private final NbtCompound monumentKills;

    private WeaponAcquisitionData() {
        this(new NbtCompound());
    }

    private WeaponAcquisitionData(NbtCompound nbt) {
        moonKills = nbt.getCompound("moonKills");
        monumentKills = nbt.getCompound("monumentKills");
    }

    public static WeaponAcquisitionData getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public int recordNewMoonKill(UUID player) {
        String key = player.toString();
        int count = moonKills.getInt(key) + 1;
        moonKills.putInt(key, count);
        markDirty();
        return count;
    }

    public int recordMonumentKill(UUID player, long monument, UUID guardian) {
        String siteKey = Long.toString(monument);
        String playerKey = player.toString();
        NbtCompound site = monumentKills.getCompound(siteKey);
        NbtList killed = site.getList(playerKey, NbtElement.INT_ARRAY_TYPE);
        NbtElement entry = NbtHelper.fromUuid(guardian);
        if (!killed.contains(entry)) {
            killed.add(entry);
            site.put(playerKey, killed);
            monumentKills.put(siteKey, site);
            markDirty();
        }
        return killed.size();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.put("moonKills", moonKills);
        nbt.put("monumentKills", monumentKills);
        return nbt;
    }
}
