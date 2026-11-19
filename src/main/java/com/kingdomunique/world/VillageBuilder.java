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

    /** 既存 API 互換のエントリ。 */
    public static void build(ServerWorld world, int centerX, int centerZ) {
        generateStarterVillage(world, centerX, centerZ);
    }

    /** 初期村／中立村をバニラテンプレートから生成する。 */
    public static void generateStarterVillage(ServerWorld world, int centerX, int centerZ) {
        forceLoadChunks(world, centerX, centerZ);

        StructureTemplateManager manager = world.getStructureTemplateManager();
        int baseY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ);

        // 中心の井戸（テンプレートの基準点は床面）。
        placeTemplate(manager, world, WELL, centerX, centerZ);

        // 家々を円状に配置。地形を一律に削ったり埋め立てたりせず、
        // 各建物の位置の地表高に合わせて配置する。
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
            placeJobBlocks(world, centerX, baseY, centerZ);
            buildFarm(world, centerX - 9, baseY, centerZ + 9);
        }
        ensureVillagers(world, centerX, baseY, centerZ);
    }

    /**
     * バニラテンプレートを1つ配置する。テンプレートの実サイズから
     * 必要チャンクをすべてロードし、地形の高さに合わせて配置する。
     */
    private static boolean placeTemplate(StructureTemplateManager manager, ServerWorld world,
                                         Identifier id, int centerX, int centerZ) {
        StructureTemplate template = manager.getTemplate(id).orElse(null);
        if (template == null) {
            return false;
        }
        net.minecraft.util.math.Vec3i size = template.getSize();
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;
        int minX = centerX - halfX;
        int maxX = centerX + halfX;
        int minZ = centerZ - halfZ;
        int maxZ = centerZ + halfZ;

        // 構造物がまたがる全チャンクを確実にロード／生成する。
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }

        int groundY = world.getTopY(Heightmap.Type.WORLD_SURFACE, centerX, centerZ);

        StructurePlacementData data = new StructurePlacementData()
                .setRotation(BlockRotation.NONE)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(true)
                .setInitializeMobs(false)
                .setUpdateNeighbors(true);
        BlockPos pos = new BlockPos(centerX - halfX, groundY, centerZ - halfZ);
        return template.place(world, pos, BlockPos.ORIGIN, data, world.getRandom(), Block.NOTIFY_ALL);
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
        buildWell(world, centerX, baseY, centerZ);
        buildHouse(world, centerX - 8, baseY, centerZ - 8, Blocks.RED_BED);
        buildHouse(world, centerX + 8, baseY, centerZ - 8, Blocks.BLUE_BED);
        buildHouse(world, centerX, baseY, centerZ + 8, Blocks.GREEN_BED);
        buildFarm(world, centerX - 8, baseY, centerZ + 8);
        placeJobBlocks(world, centerX, baseY, centerZ);
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

    /** 村人をちょうど6人に調整する（不足分のみスポーン）。 */
    public static void ensureVillagers(ServerWorld world, int x, int y, int z) {
        List<VillagerEntity> existing = world.getEntitiesByClass(VillagerEntity.class,
                new net.minecraft.util.math.Box(x - RADIUS, y - 4, z - RADIUS,
                        x + RADIUS, y + 10, z + RADIUS), v -> v.isAlive());
        int missing = VILLAGER_COUNT - existing.size();
        for (int i = 0; i < missing; i++) {
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            if (villager == null) {
                continue;
            }
            float angle = (float) (i * Math.PI * 2.0D / Math.max(1, missing));
            double vx = x + 2.5D + Math.cos(angle) * 2.5D;
            double vz = z + 2.5D + Math.sin(angle) * 2.5D;
            int villagerY = world.getTopY(Heightmap.Type.WORLD_SURFACE,
                    BlockPos.ofFloored(vx, y, vz).getX(), BlockPos.ofFloored(vx, y, vz).getZ());
            villager.refreshPositionAndAngles(vx, villagerY, vz, 0.0F, 0.0F);
            villager.setPersistent();
            world.spawnEntity(villager);
        }
    }
}

