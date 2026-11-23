package com.kingdomunique.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

public final class ArmorAcquisitionData extends PersistentState {
    private static final Type<ArmorAcquisitionData> TYPE = new Type<>(
            () -> new ArmorAcquisitionData(new NbtCompound()),
            (nbt, lookup) -> new ArmorAcquisitionData(nbt.getCompound("players")), DataFixTypes.LEVEL);

    private final NbtCompound players;

    private ArmorAcquisitionData(NbtCompound players) {
        this.players = players;
    }

    public static ArmorAcquisitionData get(ServerPlayerEntity player) {
        return player.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, "kingdomunique_armor_acquisition");
    }

    public int recordEnchant(ServerPlayerEntity player, BlockPos table) {
        NbtCompound data = playerData(player);
        NbtCompound tables = data.getCompound("tables");
        String location = player.getWorld().getRegistryKey().getValue() + "/" + table.asLong();
        int count = tables.getInt(location) + 1;
        tables.putInt(location, count);
        data.put("tables", tables);
        markDirty();
        return count;
    }

    public int recordVault(ServerPlayerEntity player, BlockPos vault) {
        NbtCompound data = playerData(player);
        NbtCompound vaults = data.getCompound("vaults");
        String location = player.getWorld().getRegistryKey().getValue() + "/" + vault.asLong();
        if (!vaults.contains(location)) {
            vaults.putBoolean(location, true);
            data.put("vaults", vaults);
            markDirty();
        }
        return vaults.getSize();
    }

    public int recordPhantom(ServerPlayerEntity player, long night) {
        NbtCompound data = playerData(player);
        int count = data.getLong("night") == night ? data.getInt("phantoms") + 1 : 1;
        data.putLong("night", night);
        data.putInt("phantoms", count);
        markDirty();
        return count;
    }

    public double recordLavaDistance(ServerPlayerEntity player, double distance) {
        NbtCompound data = playerData(player);
        double total = data.getDouble("lava_distance") + distance;
        data.putDouble("lava_distance", total);
        markDirty();
        return total;
    }

    private NbtCompound playerData(ServerPlayerEntity player) {
        String key = player.getUuidAsString();
        if (!players.contains(key)) {
            players.put(key, new NbtCompound());
        }
        return players.getCompound(key);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.put("players", players);
        return nbt;
    }
}
