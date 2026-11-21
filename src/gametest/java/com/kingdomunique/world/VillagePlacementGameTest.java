package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.building.StructureRotationUtil;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class VillagePlacementGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void starterHallUsesTerraceInsteadOfBuriedCenterHeight(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(40, 70, 40));
        for (int x = -18; x <= 18; x++) {
            for (int z = -35; z <= 18; z++) {
                int elevation = z <= -8 ? 12 : z <= -1 ? -z : 0;
                BlockPos ground = center.add(x, elevation, z);
                world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        BlockPos expected = TownHallBuilder.findVillageOrigin(world, center.getX(), center.getZ());
        context.assertTrue(expected != null, "A natural town hall plot must exist on the terrace");
        context.assertTrue(expected.getY() == center.getY() + 13, "The hall must use the terrace elevation");
        BlockPos slope = center.add(0, 7, -7);
        VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ());

        BlockPos door = expected.add(5, 0, 12);
        context.assertTrue(world.getBlockState(expected.add(5, -1, 5)).isOf(Blocks.SPRUCE_PLANKS),
                "The starter village must build its town hall on the terrace");
        context.assertTrue(world.getBlockState(door).isAir() && world.getBlockState(door.up()).isAir()
                        && world.getBlockState(door.south(2)).isAir()
                        && world.getBlockState(door.south(2).up()).isAir(),
                "The entrance and its approach must not be buried");
        context.assertTrue(world.getBlockState(slope).isOf(Blocks.STONE)
                        && world.getBlockState(expected.down(2)).isAir(),
                "Generation must retain the slope and not build a raised foundation under the hall");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "starter_village")
    public void starterVillageBuildingsCountAsKingdomBuildings(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(40, 95, 40));
        for (int x = -32; x <= 32; x++) {
            for (int z = -40; z <= 32; z++) {
                world.setBlockState(center.add(x, 0, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        UUID uuid = UUID.randomUUID();
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(uuid, "village_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        KingdomData.getOrCreate(world.getServer().getOverworld()).put(uuid, new KingdomData.KingdomInfo(
                center.getX(), center.getZ(), 0, 10, 0, false, 0, true, 100, true, ""));

        List<VillageBuilder.StarterBuilding> buildings =
                VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ(), uuid);
        long houses = buildings.stream().filter(b -> b.type() == BuildingType.HOUSE).count();
        context.assertTrue(buildings.stream().anyMatch(b -> b.type() == BuildingType.TOWN_HALL),
                "The starter village must contain the town hall");
        context.assertTrue(houses == 3, "The starter village must build three blueprint houses, got " + houses);
        context.assertTrue(buildings.stream().anyMatch(b -> b.type() == BuildingType.BLACKSMITH),
                "The starter village must build the blueprint blacksmith");
        for (int a = 0; a < buildings.size(); a++) {
            BlockBox first = box(buildings.get(a));
            for (int b = a + 1; b < buildings.size(); b++) {
                BlockBox second = box(buildings.get(b));
                context.assertFalse(first.intersectsXZ(second.getMinX(), second.getMinZ(),
                        second.getMaxX(), second.getMaxZ()), "Starter buildings must not overlap");
            }
        }

        VillageBuilder.registerStarterBuildings(world, player, buildings, center);
        VillageBuilder.registerStarterBuildings(world, player, buildings, center);
        KingdomBuildingState state = KingdomBuildingState.get(world);
        context.assertTrue(state.hasCompletedBuilding(uuid, BuildingType.TOWN_HALL),
                "The starter town hall must satisfy the palace prerequisite");
        context.assertTrue(state.entries().stream()
                        .filter(b -> b.ownerUuid.equals(uuid) && b.type == BuildingType.HOUSE).count() == houses,
                "Every starter house must be registered once");
        int cap = KingdomManager.getOrCreate(player).populationCap();
        context.assertTrue(cap == 10, "Starter houses must not raise the population cap, got " + cap);
        int max = com.kingdomunique.kingdom.PopulationManager.calculateMaxPopulation(world, uuid);
        context.assertTrue(max == 10, "Starter houses must not count toward the growth cap, got " + max);
        context.complete();
    }

    private static BlockBox box(VillageBuilder.StarterBuilding building) {
        return StructureRotationUtil.computeBoundingBox(building.origin(), building.type(), building.facing());
    }
}
