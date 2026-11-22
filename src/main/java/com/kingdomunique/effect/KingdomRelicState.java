package com.kingdomunique.effect;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.UUID;

final class KingdomRelicState extends PersistentState {
    private static final Type<KingdomRelicState> TYPE = new Type<>(
            KingdomRelicState::new, KingdomRelicState::fromNbt, DataFixTypes.LEVEL);

    private NbtCompound recallDays = new NbtCompound();
    private NbtCompound guardians = new NbtCompound();

    static KingdomRelicState get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, "kingdomunique_kingdom_relics");
    }

    boolean canRecall(UUID owner, long day) {
        String key = owner.toString();
        return !recallDays.contains(key) || recallDays.getLong(key) < day;
    }

    void recordRecall(UUID owner, long day) {
        recallDays.putLong(owner.toString(), day);
        markDirty();
    }

    boolean canSpawnGuardian(UUID owner, long day) {
        NbtCompound guardian = guardians.getCompound(owner.toString());
        return !guardian.containsUuid("entity")
                && (!guardian.contains("reviveDay") || day >= guardian.getLong("reviveDay"));
    }

    boolean isGuardian(UUID owner, UUID entity) {
        NbtCompound guardian = guardians.getCompound(owner.toString());
        return guardian.containsUuid("entity") && entity.equals(guardian.getUuid("entity"));
    }

    void recordGuardian(UUID owner, UUID entity) {
        NbtCompound guardian = new NbtCompound();
        guardian.putUuid("entity", entity);
        guardians.put(owner.toString(), guardian);
        markDirty();
    }

    void removeGuardian(UUID owner, UUID entity, long reviveDay) {
        if (isGuardian(owner, entity)) {
            NbtCompound guardian = new NbtCompound();
            guardian.putLong("reviveDay", reviveDay);
            guardians.put(owner.toString(), guardian);
            markDirty();
        }
    }

    private static KingdomRelicState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        KingdomRelicState state = new KingdomRelicState();
        state.recallDays = nbt.getCompound("recallDays");
        state.guardians = nbt.getCompound("guardians");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.put("recallDays", recallDays);
        nbt.put("guardians", guardians);
        return nbt;
    }
}
