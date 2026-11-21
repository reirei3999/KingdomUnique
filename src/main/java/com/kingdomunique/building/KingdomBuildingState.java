package com.kingdomunique.building;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 完成済み建築の所在・向き・占有範囲・特殊コアを永続化する。 */
public final class KingdomBuildingState extends PersistentState {

    private static final String KEY = "kingdomunique_buildings";

    private static final PersistentState.Type<KingdomBuildingState> TYPE = new PersistentState.Type<>(
            KingdomBuildingState::new, KingdomBuildingState::fromNbt, DataFixTypes.LEVEL
    );

    private final List<CompletedBuilding> completedBuildings = new ArrayList<>();

    private KingdomBuildingState() {
    }

    public static KingdomBuildingState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public static final class CompletedBuilding {
        public final UUID ownerUuid;
        public final BuildingType type;
        public final BlockPos origin;
        public final Direction facing;
        public final BlockBox boundingBox;
        public final BlockPos specialCorePos;

        public CompletedBuilding(UUID ownerUuid, BuildingType type, BlockPos origin,
                                 Direction facing, BlockBox boundingBox, BlockPos specialCorePos) {
            this.ownerUuid = ownerUuid;
            this.type = type;
            this.origin = origin;
            this.facing = facing;
            this.boundingBox = boundingBox;
            this.specialCorePos = specialCorePos;
        }
    }

    public boolean hasCompletedBuilding(UUID owner, BuildingType type) {
        return completedBuildings.stream().anyMatch(b -> b.ownerUuid.equals(owner) && b.type == type);
    }

    /** 完成＋建築中の合計（上限チェック用、建築中は BuildData から数える）。 */
    public int countBuildings(ServerWorld world, UUID owner, BuildingType type) {
        int count = (int) completedBuildings.stream()
                .filter(b -> b.ownerUuid.equals(owner) && b.type == type).count();
        for (BuildData.BuildSite site : BuildData.getOrCreate(world).sites()) {
            if (site.ready() && site.owner().equals(owner) && site.type() == type) {
                count++;
            }
        }
        return count;
    }

    public void add(UUID owner, BuildingType type, BlockPos origin, Direction facing,
                    BlockBox box, BlockPos core) {
        completedBuildings.removeIf(b -> b.origin.equals(origin));
        completedBuildings.add(new CompletedBuilding(owner, type, origin.toImmutable(),
                facing, box, core == null ? null : core.toImmutable()));
        markDirty();
    }

    public List<CompletedBuilding> entries() {
        return completedBuildings;
    }

    private static KingdomBuildingState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        KingdomBuildingState state = new KingdomBuildingState();
        NbtList list = nbt.getList("completed", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            try {
                UUID owner = c.getUuid("owner");
                BuildingType type = BuildingType.valueOf(c.getString("type"));
                BlockPos origin = BlockPos.fromLong(c.getLong("origin"));
                Direction facing = Direction.byName(c.getString("facing"));
                if (facing == null || !facing.getAxis().isHorizontal()) {
                    facing = Direction.SOUTH;
                }
                BlockBox box = new BlockBox(c.getInt("minX"), c.getInt("minY"), c.getInt("minZ"),
                        c.getInt("maxX"), c.getInt("maxY"), c.getInt("maxZ"));
                BlockPos core = c.contains("corePos")
                        ? BlockPos.fromLong(c.getLong("corePos")) : null;
                state.completedBuildings.add(
                        new CompletedBuilding(owner, type, origin, facing, box, core));
            } catch (IllegalArgumentException ignored) {
                // 削除された enum 名などは安全にスキップ。
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (CompletedBuilding b : completedBuildings) {
            NbtCompound c = new NbtCompound();
            c.putUuid("owner", b.ownerUuid);
            c.putString("type", b.type.name());
            c.putLong("origin", b.origin.asLong());
            c.putString("facing", b.facing.getName());
            c.putInt("minX", b.boundingBox.getMinX());
            c.putInt("minY", b.boundingBox.getMinY());
            c.putInt("minZ", b.boundingBox.getMinZ());
            c.putInt("maxX", b.boundingBox.getMaxX());
            c.putInt("maxY", b.boundingBox.getMaxY());
            c.putInt("maxZ", b.boundingBox.getMaxZ());
            if (b.specialCorePos != null) {
                c.putLong("corePos", b.specialCorePos.asLong());
            }
            list.add(c);
        }
        nbt.put("completed", list);
        return nbt;
    }
}
