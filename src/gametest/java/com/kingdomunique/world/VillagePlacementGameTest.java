package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.building.StructureRotationUtil;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;
import java.util.UUID;

public final class VillagePlacementGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "water_village_relocation")
    public void worldVillageRelocatesFromBrokenIceToLand(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos planned = context.getAbsolutePos(new BlockPos(800, 180, 800));
        for (int x = -112; x <= 144; x++) {
            for (int z = -112; z <= 112; z++) {
                world.setBlockState(planned.add(x, 0, z),
                        (x % 8 == 0 ? Blocks.WATER : Blocks.ICE).getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (int x = 72; x <= 128; x++) {
            for (int z = -32; z <= 32; z++) {
                world.setBlockState(planned.add(x, 0, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        VillageGenState state = VillageGenState.get(world);
        WorldUniqueData unique = WorldUniqueData.getOrCreate(world);
        var beforeSites = List.copyOf(unique.getVillageSites());
        var entry = new VillageGenState.VillageEntry(planned.getX(), planned.getZ(), false);
        state.pendingVillages.add(entry);
        unique.setVillageSites(List.of(new WorldUniqueData.VillageSite(planned.getX(), planned.getZ())));
        try {
            context.assertTrue(WorldInitializer.tryBuildVillage(world, entry, 128) == 0
                            && entry.searchCursor == 1 && entry.retryAfter == 0,
                    "One search step must inspect only the planned center, without exhausting the search");
            int placed = 0;
            for (int step = 1; step < 81 && !entry.built; step++) {
                placed = WorldInitializer.tryBuildVillage(world, entry, 128);
            }
            context.assertTrue(placed > 0 && entry.built, "Relocated village must have actual buildings");
            context.assertTrue(entry.x >= planned.getX() + 72 && entry.x <= planned.getX() + 128
                            && Math.abs(entry.z - planned.getZ()) <= 32,
                    "Village center must move from broken ice onto the dry land patch");
            context.assertTrue(unique.getVillageSites().contains(new WorldUniqueData.VillageSite(entry.x, entry.z))
                            && !unique.getVillageSites().contains(
                                    new WorldUniqueData.VillageSite(planned.getX(), planned.getZ())),
                    "Protection sites must follow the relocated center");
            var saved = state.writeNbt(new NbtCompound(), world.getRegistryManager())
                    .getList("villages", NbtElement.COMPOUND_TYPE);
            var savedEntry = saved.getCompound(saved.size() - 1);
            context.assertTrue(savedEntry.getInt("x") == entry.x && savedEntry.getInt("z") == entry.z
                            && savedEntry.getBoolean("built"), "Relocated position and completion must persist");
            context.assertTrue(WorldInitializer.tryBuildVillage(world, entry, 128) == 0,
                    "Completed entries must not generate a second village");
        } finally {
            state.pendingVillages.remove(entry);
            state.markDirty();
            unique.setVillageSites(beforeSites);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "water_village_empty")
    public void waterVillageWithoutLandStaysPending(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(1600, 180, 800));
        for (int x = -272; x <= 272; x++) {
            for (int z = -272; z <= 272; z++) {
                world.setBlockState(center.add(x, 0, z), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        var entry = new VillageGenState.VillageEntry(center.getX(), center.getZ(), false);
        for (int step = 0; step < 289; step++) {
            context.assertTrue(WorldInitializer.tryBuildVillage(world, entry, 256) == 0 && !entry.built,
                    "A world village with no suitable land must remain unbuilt");
            if (step < 288) {
                context.assertTrue(entry.searchCursor == step + 1 && entry.retryAfter == 0,
                        "Each tick must advance exactly one candidate and defer cooldown until exhaustion");
            }
        }
        context.assertTrue(entry.x == center.getX() && entry.z == center.getZ()
                        && entry.retryAfter > world.getTime(), "Failed searches must retain their site and back off");
        long retryAfter = entry.retryAfter;
        context.assertTrue(WorldInitializer.tryBuildVillage(world, entry, 256) == 0
                        && entry.retryAfter == retryAfter, "The cooldown must prevent immediate repeated searches");
        var result = VillageBuilder.generateVillage(world, center.getX(), center.getZ(), null);
        context.assertTrue(result.placedBuildings() == 0 && result.buildings().isEmpty(),
                "All-water generation must report zero buildings, including fallback placements");
        UUID owner = UUID.randomUUID();
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(owner, "water_starter_test"), SyncedClientOptions.createDefault());
        KingdomData data = KingdomData.getOrCreate(world);
        var info = new KingdomData.KingdomInfo(center.getX(), center.getZ(), 0, 10, 0,
                false, 0, true, 100, false, "");
        data.put(owner, info);
        try {
            StarterSpawn.ensureVillage(player, world, data, info);
            context.assertFalse(data.get(owner).villageGenerated(),
                    "An unsuccessful starter village must not be flagged generated");
            context.assertTrue(PlayerSpawnData.getOrCreate(world).get(owner) == null,
                    "A failed starter village must not save a misleading spawn");
        } finally {
            data.remove(owner);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "water_village_biomes")
    public void aquaticBiomesAreRejectedEvenOnSolidIce(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(2400, 180, 800));
        for (int x = -40; x <= 40; x++) {
            for (int z = -48; z <= 40; z++) {
                world.setBlockState(center.add(x, 0, z), Blocks.ICE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        context.assertTrue(VillageSiteFinder.suitableCenter(world, center.getX(), center.getZ()) != null,
                "Flat solid ice in a non-aquatic biome must still pass the terrain checks");
        for (var biome : List.of(BiomeKeys.OCEAN, BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN,
                BiomeKeys.RIVER, BiomeKeys.FROZEN_RIVER)) {
            BlockPos min = center.add(-4, -4, -4);
            BlockPos max = center.add(4, 4, 4);
            world.getServer().getCommandManager().executeWithPrefix(
                    world.getServer().getCommandSource().withWorld(world).withSilent(),
                    "fillbiome " + min.getX() + " " + min.getY() + " " + min.getZ() + " "
                            + max.getX() + " " + max.getY() + " " + max.getZ() + " " + biome.getValue());
            context.assertTrue(world.getBiome(center.up()).matchesKey(biome),
                    "The biome fixture must actually be applied: " + biome.getValue());
            context.assertTrue(world.getBiome(center.up()).isIn(BiomeTags.IS_OCEAN)
                            || world.getBiome(center.up()).isIn(BiomeTags.IS_RIVER),
                    "The aquatic fixture must have the expected biome tags");
            context.assertTrue(VillageSiteFinder.suitableCenter(world, center.getX(), center.getZ()) == null,
                    "Aquatic biome must be rejected even with a solid ice surface: " + biome.getValue());
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "starter_village")
    public void registeringKingdomVillageForceLoadsItsChunks(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos spawn = context.getAbsolutePos(new BlockPos(527, 0, 527));
        ChunkPos farCorner = new ChunkPos(spawn.add(104, 0, 104));
        context.assertTrue(world.getChunkManager().getWorldChunk(farCorner.x, farCorner.z) == null,
                "The village's far corner must start unloaded");
        UUID owner = UUID.randomUUID();
        KingdomData data = new KingdomData();
        var before = new LongOpenHashSet(world.getForcedChunks());
        var expected = new LongOpenHashSet(before);
        for (int x = (spawn.getX() - 104) >> 4; x <= (spawn.getX() + 104) >> 4; x++) {
            for (int z = (spawn.getZ() - 104) >> 4; z <= (spawn.getZ() + 104) >> 4; z++) {
                expected.add(ChunkPos.toLong(x, z));
            }
        }
        try {
            StarterSpawn.registerVillage(world, data, owner, new KingdomData.KingdomInfo(
                    spawn.getX(), spawn.getZ(), 0, 10, 0, false, 0, true, 100, true, ""));

            context.assertTrue(data.get(owner).villageGenerated(), "The kingdom village must be recorded");
            context.assertTrue(world.getForcedChunks().equals(expected),
                    "All chunks out to the starter village's outer generation bounds must be forced");
            context.assertTrue(world.getChunkManager().getWorldChunk(farCorner.x, farCorner.z) == null,
                    "Registering forced chunks must not load them synchronously");
        } finally {
            var added = new LongOpenHashSet(world.getForcedChunks());
            added.removeAll(before);
            for (long pos : added) {
                world.setChunkForced(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos), false);
            }
        }
        context.complete();
    }

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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "starter_hall_site")
    public void starterHallClearsCanopyAndLevelsFoundation(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(40, 85, 40));
        for (int x = -32; x <= 32; x++) {
            for (int z = -40; z <= 32; z++) {
                int elevation = x < 0 ? 0 : 1;
                world.setBlockState(center.add(x, elevation, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        BlockPos origin = TownHallBuilder.findVillageOrigin(world, center.getX(), center.getZ());
        context.assertTrue(origin != null, "A starter hall plot must exist on the one-block slope");
        BlockPos foundation = origin.add(2, -1, -1);
        BlockPos interior = origin.add(2, 3, 5);
        BlockPos outside = origin.add(-1, 3, 5);
        var leaves = Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
        world.setBlockState(interior, leaves, Block.NOTIFY_LISTENERS);
        world.setBlockState(outside, leaves, Block.NOTIFY_LISTENERS);
        context.assertTrue(world.getBlockState(foundation).isAir()
                        && world.getBlockState(foundation.down()).isOf(Blocks.STONE),
                "The low side of the chosen plot must be one block below the hall's foundation level");

        List<VillageBuilder.StarterBuilding> buildings =
                VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ());
        context.assertTrue(buildings.stream().anyMatch(b -> b.type() == BuildingType.TOWN_HALL
                        && b.origin().equals(origin.north())),
                "The starter village must place the hall on the same plot despite the overhanging leaves");
        context.assertTrue(world.getBlockState(interior.down(4)).isOf(Blocks.SPRUCE_PLANKS),
                "The tested obstruction must be above the hall floor");
        boolean cleared = world.getBlockState(interior).isAir();
        boolean leveled = world.getBlockState(foundation).isOf(Blocks.DIRT);
        context.assertTrue(cleared && leveled,
                "The starter hall must clear overhanging leaves and level the low side of its foundation"
                        + " (cleared=" + cleared + ", leveled=" + leveled + ")");
        context.assertTrue(world.getBlockState(outside).isOf(Blocks.OAK_LEAVES),
                "Site preparation must keep canopy outside the hall footprint");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "starter_hall_wildlife", tickLimit = 140)
    public void starterVillageProtectsAnimalsAndClearsPlantsWithoutDrops(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(40, 85, 40));
        for (int x = -32; x <= 32; x++) {
            for (int z = -40; z <= 32; z++) {
                BlockPos ground = center.add(x, 0, z);
                world.setBlockState(ground, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(ground.up(), Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        BlockPos hall = TownHallBuilder.findVillageOrigin(world, center.getX(), center.getZ());
        context.assertTrue(hall != null, "A starter hall plot must exist under the short grass");
        BlockPos hallWall = hall.south(5);
        BlockPos wellWall = center.add(-2, 1, -2);
        CowEntity hallCow = spawnCow(world, hallWall);
        CowEntity wellCow = spawnCow(world, wellWall);
        context.assertTrue(!hallCow.isInsideWall() && !wellCow.isInsideWall(),
                "Both cows must start unobstructed");
        ItemEntity existingItem = new ItemEntity(world, hallWall.getX() + 2.5, hallWall.getY(),
                hallWall.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        context.assertTrue(world.spawnEntity(existingItem), "The pre-existing item must spawn");
        Box site = new Box(center).expand(34, 8, 42);

        VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ());
        context.assertTrue(existingItem.isAlive(), "Generation must not remove pre-existing dropped items");
        assertNoGenerationDrops(context, world, site, existingItem);
        context.assertTrue(world.getBlockState(hallWall).isOf(Blocks.STONE_BRICKS)
                        && world.getBlockState(wellWall).isOf(Blocks.COBBLESTONE),
                "Generation must place solid hall and well blocks at the cows' previous positions");
        for (CowEntity cow : List.of(hallCow, wellCow)) {
            context.assertTrue(world.isBlockSpaceEmpty(cow, cow.getBoundingBox()),
                    "Generation must move the cow out of newly placed solid blocks");
        }
        context.waitAndRun(105, () -> {
            for (CowEntity cow : List.of(hallCow, wellCow)) {
                context.assertTrue(cow.isAlive() && cow.getHealth() == cow.getMaxHealth(),
                        "The relocated cow must survive without suffocation damage");
            }
            assertNoGenerationDrops(context, world, site, existingItem);
            context.complete();
        });
    }

    private static void assertNoGenerationDrops(TestContext context, ServerWorld world, Box site, ItemEntity existingItem) {
        var drops = world.getEntitiesByClass(ItemEntity.class, site, item -> item != existingItem);
        context.assertTrue(drops.isEmpty(), "Starter generation must not create item entities: "
                + drops.stream().map(ItemEntity::getStack).toList());
    }

    private static CowEntity spawnCow(ServerWorld world, BlockPos pos) {
        CowEntity cow = EntityType.COW.create(world);
        cow.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        cow.setAiDisabled(true);
        world.spawnEntity(cow);
        return cow;
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "starter_village_buildings")
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
        KingdomData data = KingdomData.getOrCreate(world.getServer().getOverworld());
        data.put(uuid, new KingdomData.KingdomInfo(
                center.getX(), center.getZ(), 0, 10, 0, false, 0, true, 100, true, ""));
        try {
            assertStarterVillageBuildings(context, world, player, center, uuid);
        } finally {
            data.remove(uuid);
        }
        context.complete();
    }

    private static void assertStarterVillageBuildings(TestContext context, ServerWorld world,
                                                      ServerPlayerEntity player, BlockPos center, UUID uuid) {
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
    }

    private static BlockBox box(VillageBuilder.StarterBuilding building) {
        return StructureRotationUtil.computeBoundingBox(building.origin(), building.type(), building.facing());
    }
}
