package com.kingdomunique.building;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** 完了時に設計図ブロックを中心として立体構造物を配置する。 */
public final class StructurePlacer {

    private StructurePlacer() {
    }

    public static void place(ServerWorld world, BlockPos center, BuildingType type) {
        place(world, center, type, net.minecraft.util.math.Direction.SOUTH);
    }

    /** 建物の正面を facing に向けて配置する。 */
    public static void place(ServerWorld world, BlockPos center, BuildingType type,
                             net.minecraft.util.math.Direction facing) {
        // 王城は専用の中世ファンタジー風ビルダーで生成する（origin = 北西の角）。
        if (type == BuildingType.TOWN_HALL) {
            for (int cx = (center.getX() - 16) >> 4; cx <= (center.getX() + 16) >> 4; cx++) {
                for (int cz = (center.getZ() - 16) >> 4; cz <= (center.getZ() + 16) >> 4; cz++) {
                    world.getChunk(cx, cz);
                }
            }
            com.kingdomunique.world.TownHallBuilder.buildTownHall(world, center);
            return;
        }
        // 通常建築はバニラの村テンプレートを主体にする。
        if (type == BuildingType.HOUSE || type == BuildingType.BARRACKS
                || type == BuildingType.BLACKSMITH) {
            placeVanillaVillageBuilding(world, center, type);
            return;
        }
        int r = radius(type) + 4;
        // 構造物がまたがる全チャンクを確実にロードする。
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }
        clear(world, center, radius(type));
        foundation(world, center, radius(type));
        switch (type) {
            case HOUSE -> house(world, center);
            case BARRACKS -> barracks(world, center);
            case BLACKSMITH -> blacksmith(world, center);
            case TOWN_HALL -> townHall(world, center);
            case WATCHTOWER -> watchtower(world, center);
            case GATE -> gate(world, center);
            case WALL -> wall(world, center);
            case HERO_SANCTUARY -> {
                int[] d = StructureRotationUtil.getBuildingDimensions(type);
                for (var p : com.kingdomunique.world.HeroSanctuaryBuilder.generatePlacements(center)) {
                    world.setBlockState(
                            StructureRotationUtil.transformPos(center,
                                    p.pos().getX() - center.getX(), p.pos().getY() - center.getY(),
                                    p.pos().getZ() - center.getZ(), d[0], d[2], facing),
                            StructureRotationUtil.transformState(p.state(), facing),
                            net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
            case GRANARY, INFIRMARY, MARKET, ARSENAL, TRAINING_GROUND, ROYAL_PALACE -> {
                int[] d = StructureRotationUtil.getBuildingDimensions(type);
                for (var p : AdditionalStructures.generatePlacements(type, center)) {
                    world.setBlockState(
                            StructureRotationUtil.transformPos(center,
                                    p.pos().getX() - center.getX(), p.pos().getY() - center.getY(),
                                    p.pos().getZ() - center.getZ(), d[0], d[2], facing),
                            StructureRotationUtil.transformState(p.state(), facing),
                            net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    /** 建築種別に対応するバニラ村の家テンプレートを配置する。 */
    private static void placeVanillaVillageBuilding(ServerWorld world, BlockPos center,
                                                    BuildingType type) {
        String path = switch (type) {
            case HOUSE -> "village/plains/houses/plains_small_house_1";
            case BARRACKS -> "village/plains/houses/plains_medium_house_1";
            case BLACKSMITH -> "village/plains/houses/plains_tool_smith_1";
            default -> "village/plains/houses/plains_small_house_1";
        };
        com.kingdomunique.world.VillageBuilder.placeTemplate(world,
                net.minecraft.util.Identifier.of("minecraft", path),
                center.getX(), center.getZ());
    }
    private static int radius(BuildingType type) {
        return switch (type) {
            case HOUSE -> 4;
            case BARRACKS -> 6;
            case BLACKSMITH -> 5;
            case TOWN_HALL -> 8;
            case WATCHTOWER -> 5;
            case GATE -> 4;
            case WALL -> 5;
            case HERO_SANCTUARY -> 6;
            case GRANARY, INFIRMARY, MARKET, ARSENAL -> 6;
            case TRAINING_GROUND -> 7;
            case ROYAL_PALACE -> 9;
            case WAREHOUSE -> 4;
        };
    }

    private static void clear(ServerWorld world, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = -2; y < 14; y++) {
                    BlockPos pos = center.add(dx, y, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    /** 建物下端（center.y-1）から下へ自然な土／丸石の基礎を埋める。 */
    private static void foundation(ServerWorld world, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = center.getY() - 6; y < center.getY() - 1; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    world.setBlockState(pos, y < center.getY() - 2
                            ? Blocks.COBBLESTONE.getDefaultState() : Blocks.DIRT.getDefaultState());
                }
            }
        }
    }

    private static void wallBox(ServerWorld world, BlockPos origin, int radiusX, int radiusZ,
                                int height, net.minecraft.block.BlockState wall,
                                net.minecraft.block.BlockState floor) {
        for (int y = -1; y <= height; y++) {
            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    boolean edge = Math.abs(dx) == radiusX || Math.abs(dz) == radiusZ;
                    if (y == -1) {
                        world.setBlockState(origin.add(dx, y, dz), floor);
                    } else if (y == height) {
                        world.setBlockState(origin.add(dx, y, dz), wall);
                    } else if (edge) {
                        boolean doorway = y <= 1 && dx == 0 && dz == radiusZ;
                        world.setBlockState(origin.add(dx, y, dz),
                                doorway ? Blocks.AIR.getDefaultState() : wall);
                    }
                }
            }
        }
    }

    private static void house(ServerWorld world, BlockPos center) {
        wallBox(world, center, 3, 3, 3, Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        world.setBlockState(center.add(0, 3, 0), Blocks.OAK_STAIRS.getDefaultState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(center.add(dx, 4, dz), Blocks.OAK_STAIRS.getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 1, 0), Blocks.BELL.getDefaultState());
    }

    private static void barracks(ServerWorld world, BlockPos center) {
        wallBox(world, center, 5, 5, 4, Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.setBlockState(center.add(dx, 5, dz), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        for (int x = -3; x <= 3; x += 3) {
            world.setBlockState(center.add(x, 1, 0), Blocks.CHEST.getDefaultState());
        }
    }

    private static void blacksmith(ServerWorld world, BlockPos center) {
        wallBox(world, center, 4, 4, 3, Blocks.BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        world.setBlockState(center.add(0, 1, 0), Blocks.FURNACE.getDefaultState());
        world.setBlockState(center.add(2, 1, 0), Blocks.ANVIL.getDefaultState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.setBlockState(center.add(dx, 4, dz), Blocks.COBBLESTONE.getDefaultState());
            }
        }
    }

    private static void townHall(ServerWorld world, BlockPos center) {
        wallBox(world, center, 7, 7, 5, Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                world.setBlockState(center.add(dx, 6, dz), Blocks.GOLD_BLOCK.getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 7, 0), Blocks.BEACON.getDefaultState());
        for (int x = -5; x <= 5; x += 5) {
            for (int z = -5; z <= 5; z += 5) {
                world.setBlockState(center.add(x, 6, z), Blocks.LANTERN.getDefaultState());
            }
        }
    }

    /** 門扉（閉じているときのブロック）の相対位置定義。 */
    public static final int GATE_LEAF_HALF_WIDTH = 1;
    public static final int GATE_LEAF_HEIGHT = 3;

    /** 国境監視塔：石レンガの柱と木の見張り台。 */
    private static void watchtower(ServerWorld world, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(center.add(dx, -1, dz), Blocks.COBBLESTONE.getDefaultState());
                world.setBlockState(center.add(dx, 7, dz), Blocks.OAK_PLANKS.getDefaultState());
                world.setBlockState(center.add(dx, 9, dz), Blocks.STONE_BRICKS.getDefaultState());
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                for (int y = 0; y <= 6; y++) {
                    world.setBlockState(center.add(dx, y, dz),
                            corner ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.AIR.getDefaultState());
                }
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    world.setBlockState(center.add(dx, 8, dz), Blocks.OAK_PLANKS.getDefaultState());
                }
            }
        }
        world.setBlockState(center.add(0, 8, 0), Blocks.LANTERN.getDefaultState());
    }

    /** 国の門：中央の門扉は DefensesSystem が開閉する。 */
    private static void gate(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            world.setBlockState(center.add(dx, -1, 0), Blocks.COBBLESTONE.getDefaultState());
            world.setBlockState(center.add(dx, 4, 0), Blocks.STONE_BRICKS.getDefaultState());
        }
        for (int y = 0; y <= 3; y++) {
            world.setBlockState(center.add(-2, y, 0), Blocks.STONE_BRICKS.getDefaultState());
            world.setBlockState(center.add(2, y, 0), Blocks.STONE_BRICKS.getDefaultState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 0; y < GATE_LEAF_HEIGHT; y++) {
                world.setBlockState(center.add(dx, y, 0), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
    }

    /** 防壁：幅5・高さ5の石レンガモジュール。 */
    private static void wall(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            world.setBlockState(center.add(dx, -1, 0), Blocks.COBBLESTONE.getDefaultState());
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(center.add(dx, y, 0), Blocks.STONE_BRICKS.getDefaultState());
            }
            if (dx % 2 == 0) {
                world.setBlockState(center.add(dx, 5, 0), Blocks.STONE_BRICK_WALL.getDefaultState());
            }
        }
    }
}
