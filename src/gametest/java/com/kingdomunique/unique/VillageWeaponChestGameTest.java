package com.kingdomunique.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.world.VillageGenState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VillageWeaponChestGameTest {
    private static final String ID = "excalibur";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_weapon_chest")
    public void weaponChestPlacedOnSurfaceOnceVillageIsBuilt(TestContext context) {
        var world = context.getWorld();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        VillageGenState gen = VillageGenState.get(world);
        UUID originalOwner = data.getOwner(ID);
        var originalSites = new ArrayList<>(data.getVillageSites());

        BlockPos ground = context.getAbsolutePos(new BlockPos(3, 1, 3));
        world.setBlockState(ground, Blocks.STONE.getDefaultState());
        int chestX = ground.getX();
        int chestZ = ground.getZ();
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, chestX, chestZ);
        BlockPos chestPos = new BlockPos(chestX, surfaceY, chestZ);
        var site = new WorldUniqueData.VillageSite(chestX - 2, chestZ - 2);
        var entry = new VillageGenState.VillageEntry(site.x(), site.z(), false);

        try {
            if (originalOwner != null) {
                data.releaseId(originalOwner, ID);
            }
            data.setVillageSites(List.of(site));
            gen.pendingVillages.add(entry);

            UniqueWeaponManager.distributeToVillages(world);
            context.assertFalse(data.isClaimed(ID), "No weapon may be placed before the village is built");
            context.assertTrue(world.getBlockState(chestPos).isAir(), "No chest may be placed before the village is built");

            entry.built = true;
            UniqueWeaponManager.distributeToVillages(world);
            context.assertTrue(UniqueWeaponManager.WORLD_OWNER.equals(data.getOwner(ID)),
                    "The village weapon must be claimed by the village once it is built");
            context.assertTrue(world.getBlockEntity(chestPos) instanceof Inventory inventory
                            && ID.equals(inventory.getStack(0).get(ModDataComponents.UNIQUE_WEAPON_ID)),
                    "The weapon chest must sit on the surface at y=" + surfaceY);
            context.complete();
        } finally {
            if (world.getBlockEntity(chestPos) instanceof Inventory inventory) {
                inventory.clear();
            }
            world.setBlockState(chestPos, Blocks.AIR.getDefaultState());
            gen.pendingVillages.remove(entry);
            data.releaseId(UniqueWeaponManager.WORLD_OWNER, ID);
            if (originalOwner != null) {
                data.claimId(originalOwner, ID);
            }
            data.setVillageSites(originalSites);
        }
    }
}
