package com.kingdomunique.world;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * 外部NBTテンプレートに依存せず、Javaコードの setBlockState だけで村を確実に生成するビルダー。
 *
 *  - チャンクを強制ロードしてから MOTION_BLOCKING_NO_LEAVES で地上Yを確定
 *  - 半径12を整地し、井戸・家3棟・畑・職業ブロックを配置
 *  - 村人を「ちょうど6人」スポーン（永続化）
 */
public final class VillageBuilder {

    public static final int VILLAGER_COUNT = 6;
    private static final int RADIUS = 12;
    private static final int HOUSE_RADIUS = 2;

    private VillageBuilder() {
    }

    /** 既存 API 互換のエントリ。 */
    public static void build(ServerWorld world, int centerX, int centerZ) {
        generateStarterVillage(world, centerX, centerZ);
    }

    /** 初期村／中立村を直接ブロック配置で生成する。 */
    public static void generateStarterVillage(ServerWorld world, int centerX, int centerZ) {
        forceLoadChunks(world, centerX, centerZ);

        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(centerX, 0, centerZ));
        int baseY = top.getY();

        flatten(world, centerX, baseY, centerZ);
        buildWell(world, centerX, baseY, centerZ);
        buildHouse(world, centerX - 8, baseY, centerZ - 8, Blocks.RED_BED);
        buildHouse(world, centerX + 8, baseY, centerZ - 8, Blocks.BLUE_BED);
        buildHouse(world, centerX, baseY, centerZ + 8, Blocks.GREEN_BED);
        buildFarm(world, centerX - 8, baseY, centerZ + 8);
        placeJobBlocks(world, centerX, baseY, centerZ);
        ensureVillagers(world, centerX, baseY, centerZ);
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

    /** 木・障害物を除去し、地上を草ブロックで平らにする。 */
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

    /** 石レンガと丸石の井戸（村の中心シンボル）。 */
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

    /** オークの家（床・壁・ガラス・ドア・松明・ベッド付き）。 */
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
        if (height == 1) {
            world.setBlockState(pos, Blocks.OAK_DOOR.getDefaultState()
                    .with(DoorBlock.FACING, Direction.SOUTH)
                    .with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        } else {
            world.setBlockState(pos, Blocks.OAK_DOOR.getDefaultState()
                    .with(DoorBlock.FACING, Direction.SOUTH)
                    .with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
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

    /** 水路・耕地・小麦の畑。 */
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

    /** 職業ブロック。 */
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
            villager.refreshPositionAndAngles(vx, y + 1, vz, 0.0F, 0.0F);
            villager.setPersistent();
            world.spawnEntity(villager);
        }
    }
}
