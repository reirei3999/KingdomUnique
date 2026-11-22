package com.kingdomunique.world;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;

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

/** 役場と民家を配置できる用地に、王国の設計図で村を生成する。 */
public final class VillageBuilder {

    public static final int VILLAGER_COUNT = 6;
    static final int MAX_VILLAGE_RADIUS = 96;
    private static final int RADIUS = 16;

    private static final Identifier WELL =
            Identifier.of("minecraft", "village/common/well_bottom");

    private VillageBuilder() {
    }

    /** 既存 API 互換のエントリ。 */
    public static void build(ServerWorld world, int centerX, int centerZ) {
        generateStarterVillage(world, centerX, centerZ);
    }

    /** 初期村で生成された王国の建物（設計図と同じ形・同じ原点）。 */
    public record StarterBuilding(BuildingType type, BlockPos origin, Direction facing) {
    }

    public record VillageResult(List<StarterBuilding> buildings, int placedBuildings) {
    }

    /** 井戸を囲む初期村の建物（井戸からの建物中心のずれ）。役場は井戸の北に建つ。 */
    private static final Object[][] STARTER_LAYOUT = {
            {BuildingType.HOUSE, 14, -2},
            {BuildingType.HOUSE, -14, -2},
            {BuildingType.BLACKSMITH, 13, 13},
            {BuildingType.HOUSE, -13, 13},
    };

    public static BlockPos generateDebugVillage(ServerWorld world, int x, int z) {
        BlockPos center = VillageSiteFinder.findNearby(world, x, z, 72, 24, candidate -> true);
        if (center == null || generateVillage(world, center.getX(), center.getZ(), null).placedBuildings() == 0) {
            return null;
        }
        return center;
    }

    /** 初期村／中立村を生成する（王国の設計図と同じ建物で構成）。 */
    public static List<StarterBuilding> generateStarterVillage(ServerWorld world, int centerX, int centerZ) {
        return generateStarterVillage(world, centerX, centerZ, null);
    }

    /** 所有者付きで初期村を生成し、村人を国民（建築士含む）として割り当てる。 */
    public static List<StarterBuilding> generateStarterVillage(ServerWorld world, int centerX, int centerZ,
                                                               java.util.UUID owner) {
        return generateVillage(world, centerX, centerZ, owner).buildings();
    }

    public static VillageResult generateVillage(ServerWorld world, int centerX, int centerZ,
                                                 java.util.UUID owner) {
        VillagePlan plan = planVillage(world, centerX, centerZ);
        if (plan == null) {
            return new VillageResult(List.of(), 0);
        }
        forceLoadChunks(world, centerX, centerZ);
        List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class,
                new Box(centerX - MAX_VILLAGE_RADIUS, world.getBottomY(), centerZ - MAX_VILLAGE_RADIUS,
                        centerX + MAX_VILLAGE_RADIUS + 1, world.getTopY(), centerZ + MAX_VILLAGE_RADIUS + 1),
                animal -> animal.isAlive() && !VillageTerrain.obstructed(world, animal));
        int baseY = surfaceY(world, centerX, centerZ);
        placeWell(world, plan.well());
        for (StarterBuilding building : plan.buildings()) {
            StructurePlacer.place(world, building.origin(), building.type(), building.facing());
        }
        if (plan.farm() != null) {
            placeFarm(world, plan.farm());
        }
        VillageTerrain.freeObstructedAnimals(world, animals);
        ensureVillagers(world, centerX, baseY, centerZ, owner, plan.hallOrigin());
        com.kingdomunique.KingdomUniqueMod.LOGGER.info(
                "[KingdomUnique] Generated kingdom village with {} blueprint buildings at ({}, {}).",
                plan.buildings().size(), centerX, centerZ);
        return new VillageResult(plan.buildings(), plan.buildings().size());
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

    private record VillagePlan(BlockPos hallOrigin, WellPlot well, List<StarterBuilding> buildings,
                               BlockPos farm) {
    }

    private record WellPlot(BlockPos plot, StructureTemplate template) {
    }

    static boolean hasStarterPlots(ServerWorld world, int x, int z) {
        return planVillage(world, x, z) != null;
    }

    private static VillagePlan planVillage(ServerWorld world, int centerX, int centerZ) {
        BlockPos hallOrigin = TownHallBuilder.findVillageOrigin(world, centerX, centerZ);
        if (hallOrigin == null) {
            return null;
        }
        List<BlockBox> reserved = new ArrayList<>();
        reserved.add(TownHallBuilder.villageFootprint(hallOrigin));
        WellPlot well = reserveWell(world, centerX, centerZ, reserved);
        List<StarterBuilding> buildings = new ArrayList<>();
        buildings.add(new StarterBuilding(BuildingType.TOWN_HALL, hallOrigin.north(), Direction.SOUTH));
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
            buildings.add(new StarterBuilding(type, plot.add(1, 0, 1), facing));
        }
        if (buildings.stream().noneMatch(building -> building.type() == BuildingType.HOUSE)) {
            return null;
        }

        BlockPos farm = reservePlot(world, centerX - 3, centerZ + 12, 7, 5, reserved);
        return new VillagePlan(hallOrigin, well, List.copyOf(buildings), farm);
    }

    private static WellPlot reserveWell(ServerWorld world, int x, int z, List<BlockBox> reserved) {
        StructureTemplate template = world.getStructureTemplateManager().getTemplate(WELL).orElse(null);
        if (template != null) {
            var size = template.getSize();
            BlockPos plot = reservePlot(world, x - size.getX() / 2 - 1, z - size.getZ() / 2 - 1,
                    size.getX() + 2, size.getZ() + 2, reserved);
            if (plot != null) {
                return new WellPlot(plot, template);
            }
        }
        BlockPos plot = reservePlot(world, x - 1, z - 1, 3, 3, reserved);
        return plot == null ? null : new WellPlot(plot, null);
    }

    private static void placeWell(ServerWorld world, WellPlot well) {
        if (well == null) {
            return;
        }
        if (well.template() != null) {
            var size = well.template().getSize();
            BlockPos origin = well.plot().add(1, -1, 1);
            VillageTerrain.clearPlants(world, origin, size.getX(), Math.max(size.getY(), 3), size.getZ());
            placeTemplate(world, well.template(), origin);
        } else {
            BlockPos plot = well.plot();
            buildWell(world, plot.getX() + 1, plot.getY() - 1, plot.getZ() + 1);
        }
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

    static void buildWell(ServerWorld world, int x, int y, int z) {
        VillageTerrain.clearPlants(world, new BlockPos(x - 1, y, z - 1), 3, 3, 3);
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(new BlockPos(x + dx, y + 2, z + dz), Blocks.OAK_PLANKS.getDefaultState());
            }
        }
        world.setBlockState(new BlockPos(x, y + 3, z), Blocks.BELL.getDefaultState());
    }

    static void buildFarm(ServerWorld world, int x, int y, int z) {
        BlockPos irrigation = new BlockPos(x, y, z + 2);
        for (int dz = 0; dz <= 4; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                BlockPos p = new BlockPos(x + dx, y, z + dz);
                if (p.equals(irrigation)) {
                    continue;
                }
                world.setBlockState(p, Blocks.FARMLAND.getDefaultState());
                world.setBlockState(p.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 7));
            }
        }
        // 水源は耕地で囲み、段差や地面の空洞へ流れ出さないようにする。
        world.setBlockState(irrigation.down(), Blocks.COBBLESTONE.getDefaultState());
        world.setBlockState(irrigation, Blocks.WATER.getDefaultState());
    }

    private static void placeFarm(ServerWorld world, BlockPos plot) {
        VillageTerrain.clearPlants(world, plot.down(), 7, 3, 5);
        buildFarm(world, plot.getX() + 3, plot.getY() - 1, plot.getZ());
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
