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
            BlockBox box = new BlockBox(center.getX() - 96, world.getBottomY(), center.getZ() - 96,
                    center.getX() + 96, world.getTopY(), center.getZ() + 96);
            for (StructurePiece piece : position.get().generate().toList().pieces()) {
                piece.generate(world, world.getStructureAccessor(), generator,
                        world.getRandom(), box, new ChunkPos(center), center);
            }
            return true;
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

    /** 初期村／中立村を生成する（バニラのジグソー生成を優先）。 */
    public static void generateStarterVillage(ServerWorld world, int centerX, int centerZ) {
        generateStarterVillage(world, centerX, centerZ, null);
    }

    /** 所有者付きで初期村を生成し、村人を国民（建築士含む）として割り当てる。 */
    public static void generateStarterVillage(ServerWorld world, int centerX, int centerZ,
                                              java.util.UUID owner) {
        forceLoadChunks(world, centerX, centerZ);
        int baseY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ) + 1;

        // バニラのジグソー生成（バイオーム別の村プール）を最優先で試す。
        boolean jigsaw = buildVillage(world, new BlockPos(centerX, baseY, centerZ));
        if (jigsaw) {
            // バニラ村の広場北側に王国の集会所（タウンホール）を配置する。
            TownHallBuilder.buildTownHall(world, new BlockPos(centerX - 5, baseY, centerZ - 14));
            ensureVillagers(world, centerX, baseY, centerZ, owner);
            com.kingdomunique.KingdomUniqueMod.LOGGER.info("[KingdomUnique] Generated jigsaw village at ({}, {}).",
                    centerX, centerZ);
            return;
        }
        com.kingdomunique.KingdomUniqueMod.LOGGER.warn("[KingdomUnique] Jigsaw village failed at ({}, {}); using templates.",
                centerX, centerZ);

        StructureTemplateManager manager = world.getStructureTemplateManager();

        // 中心の井戸（テンプレートの基準点は床面）。
        placeTemplate(manager, world, WELL, centerX, centerZ);

        // 家々を円状に配置。テンプレート実サイズから必要チャンクをロードし、
        // 地表〜上空をクリアした上で基礎を埋め立てる。
        int placed = 0;
        for (int i = 0; i < HOUSE_TEMPLATES.length; i++) {
            double angle = (i * 2.0D * Math.PI / HOUSE_TEMPLATES.length) + Math.PI / 4.0D;
            int hx = centerX + (int) Math.round(Math.cos(angle) * 10.0D);
            int hz = centerZ + (int) Math.round(Math.sin(angle) * 10.0D);
            if (placeTemplate(manager, world,
                    Identifier.of("minecraft", HOUSE_TEMPLATES[i]), hx, hz)) {
                placed++;
            }
        }

        // テンプレートが全く読めない環境では従来の直接配置で村を保証する。
        if (placed == 0) {
            legacyVillage(world, centerX, baseY, centerZ);
        } else {
            // 初期村では役職ブロックを生成しない（村人の役職が上書きされるため）。
            buildFarm(world, centerX - 9, baseY, centerZ + 9);
        }
        ensureVillagers(world, centerX, baseY, centerZ, owner);
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

        StructurePlacementData data = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(true)
                .setInitializeMobs(false)
                .setUpdateNeighbors(true);
        BlockPos pos = new BlockPos(centerX - halfX, groundY, centerZ - halfZ);
        return template.place(world, pos, BlockPos.ORIGIN, data, world.getRandom(), Block.NOTIFY_ALL);
    }

    /** 他クラスからバニラテンプレートを配置する簡易API。 */
    public static boolean placeTemplate(ServerWorld world, Identifier id, int centerX, int centerZ) {
        return placeTemplate(world.getStructureTemplateManager(), world, id, centerX, centerZ);
    }
    /** 旧方式の基礎整地（フォールバック用）。 */
    private static void prepareFoundation(ServerWorld world, int x, int y, int z) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos p = new BlockPos(x + dx, y, z + dz);
                for (int h = 1; h <= 8; h++) {
                    world.setBlockState(p.up(h), Blocks.AIR.getDefaultState());
                }
                world.setBlockState(p.down(1), Blocks.DIRT.getDefaultState());
                world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
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

    /** テンプレートが無い場合の旧方式（直接ブロック配置）。 */
    private static void legacyVillage(ServerWorld world, int centerX, int baseY, int centerZ) {
        flatten(world, centerX, baseY, centerZ);
        buildWell(world, centerX, baseY, centerZ);
        buildHouse(world, centerX - 8, baseY, centerZ - 8, Blocks.RED_BED);
        buildHouse(world, centerX + 8, baseY, centerZ - 8, Blocks.BLUE_BED);
        buildHouse(world, centerX, baseY, centerZ + 8, Blocks.GREEN_BED);
        buildFarm(world, centerX - 8, baseY, centerZ + 8);
    }

    private static void flatten(ServerWorld world, int centerX, int y, int centerZ) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos p = new BlockPos(centerX + dx, y, centerZ + dz);
                for (int h = 1; h <= 10; h++) {
                    world.setBlockState(p.up(h), Blocks.AIR.getDefaultState());
                }
                for (int h = 2; h >= 1; h--) {
                    world.setBlockState(p.down(h), Blocks.DIRT.getDefaultState());
                }
                world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
            }
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

    /** 村人をちょうど6人に調整し、王国民として役割を割り当てる。 */
    public static void ensureVillagers(ServerWorld world, int x, int y, int z, java.util.UUID owner) {
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
            double vx = x + 2.5D + Math.cos(angle) * 2.5D;
            double vz = z + 2.5D + Math.sin(angle) * 2.5D;
            BlockPos safe = findSafeGround(world, (int) Math.floor(vx), (int) Math.floor(vz), y + 1);
            villager.refreshPositionAndAngles(
                    safe == null ? vx : safe.getX() + 0.5D,
                    safe == null ? y + 1 : safe.getY(),
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
