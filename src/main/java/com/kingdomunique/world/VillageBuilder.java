package com.kingdomunique.world;

import net.minecraft.block.Block;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import com.kingdomunique.building.BuildingManager;
import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.StructurePlacer;
import com.kingdomunique.building.StructureRotationUtil;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * バニラの村テンプレート（StructureTemplateManager）を利用する村ビルダー。
 *
 *  - バニラの村パーツ（井戸・家・職業施設）をテンプレートから配置
 *  - 各建物の下は自然な土/草の地盤を整えるだけ（不自然な立方体の整地はしない）
 *  - 村人は「ちょうど6人」スポーンして永続化
 *  - テンプレートが見つからない場合は従来の直接配置へフォールバック
 */
public final class VillageBuilder {

    public static final int VILLAGER_COUNT = 6;
    static final int MAX_VILLAGE_RADIUS = 96;
    private static final int RADIUS = 16;
    private static final int HOUSE_RADIUS = 2;

    private static final Identifier WELL =
            Identifier.of("minecraft", "village/common/well_bottom");

    /** 配置に使うバニラの平原村パーツ（いずれも 1.21.1 に実在するID）。 */
    private static final String[] HOUSE_TEMPLATES = {
            "village/plains/houses/plains_small_house_1",
            "village/plains/houses/plains_small_house_3",
            "village/plains/houses/plains_medium_house_1",
            "village/plains/houses/plains_fletcher_house_1",
            "village/plains/houses/plains_library_1",
            "village/plains/houses/plains_butcher_shop_1"
    };

    private VillageBuilder() {
    }

    /** バイオームに応じたバニラ村のジグソー生成。 */
    public static boolean buildVillage(ServerWorld world, BlockPos center) {
        return buildVillage(world, center, List.of());
    }

    private static boolean buildVillage(ServerWorld world, BlockPos center, List<BlockBox> reserved) {
        Identifier poolId = selectVillagePoolByBiome(world, center);
        Registry<StructurePool> registry =
                world.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);
        Optional<RegistryEntry.Reference<StructurePool>> startPool = registry.getEntry(poolId);
        if (startPool.isEmpty()) {
            poolId = Identifier.ofVanilla("village/plains/town_centers");
            startPool = registry.getEntry(poolId);
        }
        if (startPool.isEmpty()) {
            return false;
        }
        try {
            ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
            NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
            Structure.Context context = new Structure.Context(
                    world.getRegistryManager(), generator, generator.getBiomeSource(),
                    noiseConfig, world.getStructureTemplateManager(), world.getSeed(),
                    new ChunkPos(center), world, biome -> true);
            Optional<Structure.StructurePosition> position =
                    StructurePoolBasedGenerator.generate(
                            context, startPool.get(), Optional.empty(), 6,
                            center, false, Optional.empty(), 80,
                            StructurePoolAliasLookup.EMPTY, DimensionPadding.NONE,
                            StructureLiquidSettings.IGNORE_WATERLOGGING);
            if (position.isEmpty()) {
                return false;
            }
            List<StructurePiece> pieces = position.get().generate().toList().pieces();
            List<StructurePiece> placed = new ArrayList<>();
            for (StructurePiece piece : pieces) {
                BlockBox bounds = piece.getBoundingBox();
                boolean fits = reserved.stream().noneMatch(area -> area.intersectsXZ(
                        bounds.getMinX(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxZ()));
                if (fits && piece instanceof PoolStructurePiece pool
                        && pool.getPoolElement().getProjection() == StructurePool.Projection.RIGID) {
                    BlockPos plot = VillageTerrain.plot(world, bounds.getMinX() - 1, bounds.getMinZ() - 1,
                            bounds.getBlockCountX() + 2, bounds.getBlockCountZ() + 2);
                    fits = plot != null;
                    if (fits) {
                        pool.translate(0, plot.getY() - bounds.getMinY() - pool.getGroundLevelDelta(), 0);
                    }
                }
                if (fits) {
                    placed.add(piece);
                } else if (placed.isEmpty()) {
                    return false;
                }
            }
            BlockBox box = new BlockBox(center.getX() - MAX_VILLAGE_RADIUS, world.getBottomY(),
                    center.getZ() - MAX_VILLAGE_RADIUS, center.getX() + MAX_VILLAGE_RADIUS,
                    world.getTopY(), center.getZ() + MAX_VILLAGE_RADIUS);
            for (StructurePiece piece : placed) {
                piece.generate(world, world.getStructureAccessor(), generator,
                        world.getRandom(), box, new ChunkPos(center), center);
            }
            return !placed.isEmpty();
        } catch (Throwable t) {
            com.kingdomunique.KingdomUniqueMod.LOGGER.error(
                    "[KingdomUnique] Jigsaw village generation failed.", t);
            return false;
        }
    }
    private static Identifier selectVillagePoolByBiome(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        if (biome.matchesKey(BiomeKeys.DESERT)) {
            return Identifier.ofVanilla("village/desert/town_centers");
        }
        if (biome.matchesKey(BiomeKeys.SAVANNA) || biome.matchesKey(BiomeKeys.SAVANNA_PLATEAU)
                || biome.matchesKey(BiomeKeys.WINDSWEPT_SAVANNA)) {
            return Identifier.ofVanilla("village/savanna/town_centers");
        }
        if (biome.matchesKey(BiomeKeys.TAIGA)
                || biome.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA)
                || biome.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA)) {
            return Identifier.ofVanilla("village/taiga/town_centers");
        }
        if (biome.matchesKey(BiomeKeys.SNOWY_PLAINS) || biome.matchesKey(BiomeKeys.SNOWY_TAIGA)) {
            return Identifier.ofVanilla("village/snowy/town_centers");
        }
        return Identifier.ofVanilla("village/plains/town_centers");
    }
    /** 既存 API 互換のエントリ。 */
    public static void build(ServerWorld world, int centerX, int centerZ) {
        generateStarterVillage(world, centerX, centerZ);
    }

    /** 初期村で生成された王国の建物（設計図と同じ形・同じ原点）。 */
    public record StarterBuilding(BuildingType type, BlockPos origin, Direction facing) {
    }

    /** 井戸を囲む初期村の建物（井戸からの建物中心のずれ）。役場は井戸の北に建つ。 */
    private static final Object[][] STARTER_LAYOUT = {
            {BuildingType.HOUSE, 14, -2},
            {BuildingType.HOUSE, -14, -2},
            {BuildingType.BLACKSMITH, 13, 13},
            {BuildingType.HOUSE, -13, 13},
    };

    /** 初期村／中立村を生成する（王国の設計図と同じ建物で構成）。 */
    public static List<StarterBuilding> generateStarterVillage(ServerWorld world, int centerX, int centerZ) {
        return generateStarterVillage(world, centerX, centerZ, null);
    }

    /** 所有者付きで初期村を生成し、村人を国民（建築士含む）として割り当てる。 */
    public static List<StarterBuilding> generateStarterVillage(ServerWorld world, int centerX, int centerZ,
                                                               java.util.UUID owner) {
        forceLoadChunks(world, centerX, centerZ);
        int baseY = surfaceY(world, centerX, centerZ);
        BlockPos hallOrigin = TownHallBuilder.findVillageOrigin(world, centerX, centerZ);
        List<BlockBox> reserved = new ArrayList<>();
        if (hallOrigin != null) {
            reserved.add(TownHallBuilder.villageFootprint(hallOrigin));
        }

        List<StarterBuilding> buildings = buildKingdomVillage(world, centerX, centerZ, reserved);
        if (buildings.isEmpty()) {
            com.kingdomunique.KingdomUniqueMod.LOGGER.warn(
                    "[KingdomUnique] No room for kingdom buildings at ({}, {}); using vanilla village.",
                    centerX, centerZ);
            if (!buildVillage(world, new BlockPos(centerX, baseY, centerZ), reserved)) {
                buildTemplateVillage(world, centerX, centerZ, reserved);
            }
        }
        if (hallOrigin != null) {
            TownHallBuilder.buildVillageTownHall(world, hallOrigin);
            buildings.add(0, new StarterBuilding(BuildingType.TOWN_HALL, hallOrigin.north(), Direction.SOUTH));
        }
        ensureVillagers(world, centerX, baseY, centerZ, owner, hallOrigin);
        return buildings;
    }

    /** 初期村の建物を所有者の王国の完成済み建物として登録する（同じ建物の再登録は加算しない）。 */
    public static void registerStarterBuildings(ServerWorld world, ServerPlayerEntity owner,
                                                List<StarterBuilding> buildings, BlockPos kingdomCenter) {
        for (StarterBuilding building : buildings) {
            BuildingManager.registerExisting(world, owner, building.type(), building.origin(), building.facing());
            if (building.type() == BuildingType.TOWN_HALL) {
                com.kingdomunique.kingdom.KingdomManager.markTownHall(owner, kingdomCenter);
            }
        }
        if (!buildings.isEmpty()) {
            com.kingdomunique.status.StatusManager.syncBuildingBoxesToWorld(world);
        }
    }

    /** 井戸を中心に、民家・鍛冶工房を井戸へ正面を向けて建て、畑を添える。 */
    private static List<StarterBuilding> buildKingdomVillage(ServerWorld world, int centerX, int centerZ,
                                                           List<BlockBox> reserved) {
        boolean well = placeVillageTemplate(world.getStructureTemplateManager(), world, WELL,
                centerX, centerZ, reserved);
        if (!well) {
            BlockPos plot = reservePlot(world, centerX - 1, centerZ - 1, 3, 3, reserved);
            if (plot != null) {
                buildWell(world, plot.getX() + 1, plot.getY() - 1, plot.getZ() + 1);
            }
        }

        List<StarterBuilding> buildings = new ArrayList<>();
        for (Object[] slot : STARTER_LAYOUT) {
            BuildingType type = (BuildingType) slot[0];
            int dx = (Integer) slot[1];
            int dz = (Integer) slot[2];
            Direction facing = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.WEST : Direction.EAST)
                    : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
            BlockBox size = StructureRotationUtil.computeBoundingBox(BlockPos.ORIGIN, type, facing);
            int width = size.getBlockCountX();
            int depth = size.getBlockCountZ();
            BlockPos plot = VillageTerrain.findPlot(world, centerX + dx - width / 2 - 1,
                    centerZ + dz - depth / 2 - 1, width + 2, depth + 2, 8, reserved);
            if (plot == null) {
                continue;
            }
            reserved.add(VillageTerrain.footprint(plot, width + 2, depth + 2));
            BlockPos origin = plot.add(1, 0, 1);
            StructurePlacer.place(world, origin, type, facing);
            buildings.add(new StarterBuilding(type, origin.toImmutable(), facing));
        }
        if (buildings.isEmpty()) {
            return buildings;
        }

        BlockPos farm = reservePlot(world, centerX - 3, centerZ + 12, 7, 5, reserved);
        if (farm != null) {
            buildFarm(world, farm.getX() + 3, farm.getY() - 1, farm.getZ());
        }
        com.kingdomunique.KingdomUniqueMod.LOGGER.info(
                "[KingdomUnique] Generated kingdom village with {} buildings at ({}, {}).",
                buildings.size(), centerX, centerZ);
        return buildings;
    }

    private static void buildTemplateVillage(ServerWorld world, int centerX, int centerZ,
                                             List<BlockBox> reserved) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        boolean well = placeVillageTemplate(manager, world, WELL, centerX, centerZ, reserved);

        int placed = 0;
        for (int i = 0; i < HOUSE_TEMPLATES.length; i++) {
            double angle = (i * 2.0D * Math.PI / HOUSE_TEMPLATES.length) + Math.PI / 4.0D;
            int hx = centerX + (int) Math.round(Math.cos(angle) * 10.0D);
            int hz = centerZ + (int) Math.round(Math.sin(angle) * 10.0D);
            if (placeVillageTemplate(manager, world,
                    Identifier.of("minecraft", HOUSE_TEMPLATES[i]), hx, hz, reserved)) {
                placed++;
            }
        }

        if (!well) {
            BlockPos plot = reservePlot(world, centerX - 1, centerZ - 1, 3, 3, reserved);
            if (plot != null) {
                buildWell(world, plot.getX() + 1, plot.getY() - 1, plot.getZ() + 1);
            }
        }
        if (placed == 0) {
            buildLegacyHouse(world, centerX - 8, centerZ - 8, Blocks.RED_BED, reserved);
            buildLegacyHouse(world, centerX + 8, centerZ - 8, Blocks.BLUE_BED, reserved);
            buildLegacyHouse(world, centerX, centerZ + 8, Blocks.GREEN_BED, reserved);
        }
        BlockPos farm = reservePlot(world, centerX - 12, centerZ + 9, 7, 5, reserved);
        if (farm != null) {
            buildFarm(world, farm.getX() + 3, farm.getY() - 1, farm.getZ());
        }
    }

    private static boolean placeVillageTemplate(StructureTemplateManager manager, ServerWorld world,
                                                Identifier id, int x, int z, List<BlockBox> reserved) {
        StructureTemplate template = manager.getTemplate(id).orElse(null);
        if (template == null) {
            return false;
        }
        var size = template.getSize();
        BlockPos plot = reservePlot(world, x - size.getX() / 2 - 1, z - size.getZ() / 2 - 1,
                size.getX() + 2, size.getZ() + 2, reserved);
        return plot != null && placeTemplate(world, template, plot.add(1, -1, 1));
    }

    private static BlockPos reservePlot(ServerWorld world, int x, int z, int width, int depth,
                                        List<BlockBox> reserved) {
        BlockPos plot = VillageTerrain.findPlot(world, x, z, width, depth, 12, reserved);
        if (plot != null) {
            reserved.add(VillageTerrain.footprint(plot, width, depth));
        }
        return plot;
    }

    /**
     * バニラテンプレートを1つ配置する。テンプレートの実サイズから
     * 必要チャンクをすべてロードし、地表〜上空を空気でクリアし、
     * 建物下端から地面までの隙間を土／丸石で埋めてから配置する。
     */
    public static boolean placeTemplate(StructureTemplateManager manager, ServerWorld world,
                                         Identifier id, int centerX, int centerZ) {
        StructureTemplate template = manager.getTemplate(id).orElse(null);
        if (template == null) {
            return false;
        }
        net.minecraft.util.math.Vec3i size = template.getSize();
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;
        int minX = centerX - halfX - 2;
        int maxX = centerX + halfX + 2;
        int minZ = centerZ - halfZ - 2;
        int maxZ = centerZ + halfZ + 2;

        // 構造物がまたがる全チャンクを確実にロード／生成する。
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }

        int groundY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ) + 1;
        // 地表から上空までを空気でクリア（建物が途中で切れないように）。
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = groundY; y <= groundY + size.getY() + 4; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                }
            }
        }
        // 建物下端〜地面の隙間を自然な土／丸石で埋める（空中浮き・埋もれ防止）。
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = groundY - 4; y < groundY; y++) {
                    world.setBlockState(new BlockPos(x, y, z),
                            (y < groundY - 1) ? Blocks.COBBLESTONE.getDefaultState()
                                    : Blocks.DIRT.getDefaultState());
                }
            }
        }

        return placeTemplate(world, template, new BlockPos(centerX - halfX, groundY, centerZ - halfZ));
    }

    private static boolean placeTemplate(ServerWorld world, StructureTemplate template, BlockPos pos) {
        StructurePlacementData data = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(true)
                .setInitializeMobs(false)
                .setUpdateNeighbors(true);
        return template.place(world, pos, BlockPos.ORIGIN, data, world.getRandom(), Block.NOTIFY_ALL);
    }

    /** 他クラスからバニラテンプレートを配置する簡易API。 */
    public static boolean placeTemplate(ServerWorld world, Identifier id, int centerX, int centerZ) {
        return placeTemplate(world.getStructureTemplateManager(), world, id, centerX, centerZ);
    }
    /** 村の範囲を含むチャンクを確実にロード／生成する。 */
    private static void forceLoadChunks(ServerWorld world, int centerX, int centerZ) {
        int minCX = (centerX - RADIUS - 16) >> 4;
        int maxCX = (centerX + RADIUS + 16) >> 4;
        int minCZ = (centerZ - RADIUS - 16) >> 4;
        int maxCZ = (centerZ + RADIUS + 16) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.getChunk(cx, cz);
            }
        }
    }

    private static void buildLegacyHouse(ServerWorld world, int x, int z, Block bed,
                                         List<BlockBox> reserved) {
        int size = HOUSE_RADIUS * 2 + 1;
        BlockPos plot = reservePlot(world, x - HOUSE_RADIUS, z - HOUSE_RADIUS, size, size + 1, reserved);
        if (plot != null) {
            buildHouse(world, plot.getX() + HOUSE_RADIUS, plot.getY() - 1, plot.getZ() + HOUSE_RADIUS, bed);
        }
    }

    private static void buildWell(ServerWorld world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + dx, y, z + dz),
                        (dx == 0 && dz == 0) ? Blocks.WATER.getDefaultState()
                                : Blocks.STONE_BRICKS.getDefaultState());
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                    world.setBlockState(new BlockPos(x + dx, y + 1, z + dz),
                            Blocks.COBBLESTONE.getDefaultState());
                    world.setBlockState(new BlockPos(x + dx, y + 2, z + dz),
                            Blocks.OAK_PLANKS.getDefaultState());
                }
            }
        }
        world.setBlockState(new BlockPos(x, y + 3, z), Blocks.BELL.getDefaultState());
    }

    private static void buildHouse(ServerWorld world, int x, int y, int z, net.minecraft.block.Block bedBlock) {
        for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
            for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                world.setBlockState(new BlockPos(x + dx, y, z + dz),
                        Blocks.COBBLESTONE.getDefaultState());
                world.setBlockState(new BlockPos(x + dx, y + 4, z + dz),
                        Blocks.OAK_STAIRS.getDefaultState());
            }
        }
        for (int h = 1; h <= 3; h++) {
            for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
                for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                    if (Math.abs(dx) != HOUSE_RADIUS && Math.abs(dz) != HOUSE_RADIUS) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x + dx, y + h, z + dz);
                    boolean door = h <= 2 && dx == 0 && dz == HOUSE_RADIUS;
                    boolean window = h == 2 && ((Math.abs(dx) == HOUSE_RADIUS && dz == 0)
                            || (Math.abs(dz) == HOUSE_RADIUS && dx == 0));
                    if (door) {
                        placeDoor(world, pos, h);
                    } else if (window) {
                        world.setBlockState(pos, Blocks.GLASS.getDefaultState());
                    } else {
                        world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState());
                    }
                }
            }
        }
        world.setBlockState(new BlockPos(x, y + 3, z), Blocks.TORCH.getDefaultState());
        placeBed(world, x - 1, y + 1, z - 1, Direction.SOUTH, bedBlock);
    }

    private static void placeDoor(ServerWorld world, BlockPos pos, int height) {
        world.setBlockState(pos, Blocks.OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, height == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
    }

    private static void placeBed(ServerWorld world, int x, int y, int z, Direction facing,
                                 net.minecraft.block.Block bedBlock) {
        BlockState foot = bedBlock.getDefaultState()
                .with(BedBlock.FACING, facing)
                .with(BedBlock.PART, BedPart.FOOT);
        BlockPos footPos = new BlockPos(x, y, z);
        world.setBlockState(footPos, foot);
        world.setBlockState(footPos.offset(facing), foot.with(BedBlock.PART, BedPart.HEAD));
    }

    private static void buildFarm(ServerWorld world, int x, int y, int z) {
        for (int dx = -3; dx <= 3; dx++) {
            world.setBlockState(new BlockPos(x + dx, y, z), Blocks.WATER.getDefaultState());
        }
        for (int dz = 1; dz <= 4; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                BlockPos p = new BlockPos(x + dx, y, z + dz);
                world.setBlockState(p, Blocks.FARMLAND.getDefaultState());
                world.setBlockState(p.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 7));
            }
        }
    }

    private static void placeJobBlocks(ServerWorld world, int x, int y, int z) {
        world.setBlockState(new BlockPos(x - 3, y + 1, z), Blocks.COMPOSTER.getDefaultState());
        world.setBlockState(new BlockPos(x + 3, y + 1, z), Blocks.GRINDSTONE.getDefaultState());
        world.setBlockState(new BlockPos(x, y + 1, z - 3), Blocks.BLAST_FURNACE.getDefaultState());
        world.setBlockState(new BlockPos(x, y + 1, z + 3), Blocks.LECTERN.getDefaultState());
    }

    /** 初期村の役割割当（建築士を必ず2人含める）。 */
    private static final String[] VILLAGER_ROLES = {
            com.kingdomunique.entity.CitizenVillager.ROLE_BUILDER,
            com.kingdomunique.entity.CitizenVillager.ROLE_FARMER,
            com.kingdomunique.entity.CitizenVillager.ROLE_BUILDER,
            com.kingdomunique.entity.CitizenVillager.ROLE_SMITH,
            com.kingdomunique.entity.CitizenVillager.ROLE_SOLDIER,
            com.kingdomunique.entity.CitizenVillager.ROLE_FARMER
    };

    /** 村人をちょうど6人に調整し、王国民として役割を割り当てる（ホール未指定は村中央）。 */
    public static void ensureVillagers(ServerWorld world, int x, int y, int z, java.util.UUID owner) {
        ensureVillagers(world, x, y, z, owner, null);
    }

    /** 村人をちょうど6人に調整し、王国民として役割を割り当てる。hallOrigin 指定時はホール内に集合させる。 */
    public static void ensureVillagers(ServerWorld world, int x, int y, int z, java.util.UUID owner,
                                       BlockPos hallOrigin) {
        List<VillagerEntity> existing = world.getEntitiesByClass(VillagerEntity.class,
                new net.minecraft.util.math.Box(x - RADIUS, y - 4, z - RADIUS,
                        x + RADIUS, y + 10, z + RADIUS), v -> v.isAlive());
        int missing = VILLAGER_COUNT - existing.size();
        int spawned = 0;
        for (int i = 0; i < missing; i++) {
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            if (villager == null) {
                continue;
            }
            float angle = (float) (i * Math.PI * 2.0D / Math.max(1, missing));
            double vx;
            double vz;
            int groundY;
            if (hallOrigin != null) {
                // タウンホール（11x13）の内部に円状に集合させる。
                vx = hallOrigin.getX() + 5.5D + Math.cos(angle) * 2.0D;
                vz = hallOrigin.getZ() + 6.5D + Math.sin(angle) * 2.0D;
                groundY = hallOrigin.getY();
            } else {
                vx = x + 2.5D + Math.cos(angle) * 2.5D;
                vz = z + 2.5D + Math.sin(angle) * 2.5D;
                groundY = surfaceY(world, (int) Math.floor(vx), (int) Math.floor(vz));
            }
            BlockPos safe = findSafeGround(world, (int) Math.floor(vx), (int) Math.floor(vz), groundY);
            villager.refreshPositionAndAngles(
                    safe == null ? vx : safe.getX() + 0.5D,
                    safe == null ? groundY : safe.getY(),
                    safe == null ? vz : safe.getZ() + 0.5D, 0.0F, 0.0F);
            villager.setPersistent();
            world.spawnEntity(villager);
            existing.add(villager);
            spawned++;
        }
        if (owner == null) {
            return;
        }
        com.kingdomunique.KingdomUniqueMod.LOGGER.info(
                "[KingdomUnique] Assigned village roles (incl. builders) for owner {}", owner);
        // 無所属の村人に王国民としての役割を割り当てる（建築士を必ず含める）。
        int index = 0;
        for (VillagerEntity villager : existing) {
            if (!(villager instanceof com.kingdomunique.entity.CitizenVillager citizen)
                    || !"".equals(citizen.ku$getOwnerUuid())) {
                continue;
            }
            String role = VILLAGER_ROLES[index % VILLAGER_ROLES.length];
            citizen.ku$setOwnerUuid(owner.toString());
            citizen.ku$setRole(role);
            com.kingdomunique.entity.VillagerProfessionUtil.applyRole(villager, role);
            villager.setCustomName(com.kingdomunique.kingdom.KingdomManager.citizenName(
                    world, owner, owner,
                    com.kingdomunique.kingdom.KingdomManager.getKingdomName(world, owner), role));
            villager.setCustomNameVisible(true);
            villager.setPersistent();
            index++;
        }
    }

    private static int surfaceY(ServerWorld world, int x, int z) {
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** 村人の足元／頭上が空で、下が固体の安全な位置を探す。 */
    private static BlockPos findSafeGround(ServerWorld world, int cx, int cz, int baseY) {
        for (int r = 0; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int y = baseY + 4; y >= baseY - 4; y--) {
                        BlockPos feet = new BlockPos(cx + dx, y, cz + dz);
                        net.minecraft.block.BlockState below = world.getBlockState(feet.down());
                        if (world.getBlockState(feet).isAir()
                                && world.getBlockState(feet.up()).isAir()
                                && !below.isAir()
                                && !below.isOf(net.minecraft.block.Blocks.WATER)
                                && !below.isOf(net.minecraft.block.Blocks.LAVA)) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }
    /** 旧API互換（所有者なし）。 */
    public static void ensureVillagers(ServerWorld world, int x, int y, int z) {
        ensureVillagers(world, x, y, z, null);
    }

    private static String roleName(String role) {
        return switch (role) {
            case com.kingdomunique.entity.CitizenVillager.ROLE_BUILDER -> "建築士";
            case com.kingdomunique.entity.CitizenVillager.ROLE_SMITH -> "鍛冶屋";
            case com.kingdomunique.entity.CitizenVillager.ROLE_SOLDIER -> "兵士";
            default -> "農民";
        };
    }
}
