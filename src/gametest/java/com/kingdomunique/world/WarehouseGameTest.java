package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class WarehouseGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "warehouse")
    public void starterVillageIncludesAKingdomWarehouse(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(new BlockPos(2040, 70, 2040));
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                world.setBlockState(center.add(x, 0, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        UUID owner = UUID.randomUUID();
        VillageBuilder.generateStarterVillage(world, center.getX(), center.getZ(), owner);

        KingdomBuildingState buildings = KingdomBuildingState.get(world);
        context.assertTrue(buildings.hasCompletedBuilding(owner, BuildingType.WAREHOUSE),
                "The starter village must register a kingdom warehouse");
        List<Inventory> storages = KingdomTreasuryUtil.getKingdomStorages(world, owner);
        context.assertTrue(storages.size() >= 6, "The warehouse chests and barrels must count as kingdom storage (found "
                + storages.size() + ")");

        context.assertTrue(KingdomTreasuryUtil.countTreasuryItem(world, owner, Items.BREAD) == 0,
                "The warehouse must start without supplies");
        context.assertTrue(KingdomTreasuryUtil.getKingdomStorages(world, UUID.randomUUID()).isEmpty(),
                "Other kingdoms must not use the owner's warehouse");
        storages.get(0).setStack(0, new ItemStack(Items.BREAD, 5));
        context.assertTrue(KingdomTreasuryUtil.countTreasuryItem(world, owner, Items.BREAD) == 5,
                "Items stored in the warehouse must be counted in the treasury");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "warehouse_unowned")
    public void unownedWarehouseDoesNotRegisterTreasury(TestContext context) {
        ServerWorld world = context.getWorld();
        int before = KingdomBuildingState.get(world).entries().size();
        WarehouseBuilder.build(world, context.getAbsolutePos(new BlockPos(0, 4, 0)), null);
        context.assertTrue(KingdomBuildingState.get(world).entries().size() == before,
                "An unowned warehouse must not register kingdom storage");
        context.complete();
    }
}
