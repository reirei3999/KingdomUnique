package com.kingdomunique.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VillageWeaponChestGameTest {
    private static final String ID = "excalibur";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "village_weapon_chest")
    public void villageChestsHoldNoUniqueWeapons(TestContext context) {
        var world = context.getWorld();
        WorldUniqueData data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        UUID originalOwner = data.getOwner(ID);
        var originalSites = new ArrayList<>(data.getVillageSites());

        BlockPos chestPos = context.getAbsolutePos(new BlockPos(3, 1, 3));
        BlockPos emptyColumn = context.getAbsolutePos(new BlockPos(1, 1, 1));
        var site = new WorldUniqueData.VillageSite(chestPos.getX() - 2, chestPos.getZ() - 2);

        try {
            if (originalOwner != null) {
                data.releaseId(originalOwner, ID);
            }
            data.setVillageSites(List.of(site));

            UniqueWeaponManager.clearVillageChestWeapons(world);
            context.assertFalse(data.isClaimed(ID), "An unclaimed weapon must not be put in a village chest");
            context.assertTrue(world.getBlockEntity(chestPos) == null, "No weapon chest may be placed at a village");

            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
            Inventory inventory = (Inventory) world.getBlockEntity(chestPos);
            ItemStack weapon = new ItemStack(UniqueWeapons.EXCALIBUR);
            weapon.set(ModDataComponents.UNIQUE_WEAPON_ID, ID);
            inventory.setStack(0, weapon);
            data.claimId(UniqueWeaponManager.WORLD_OWNER, ID);

            UniqueWeaponManager.clearVillageChestWeapons(world);
            context.assertTrue(inventory.getStack(0).isEmpty(),
                    "A weapon left in a village chest by an older version must be removed");
            context.assertFalse(data.isClaimed(ID),
                    "The removed weapon must be released so its acquisition condition can grant it");
            context.assertTrue(world.getBlockState(emptyColumn).isAir(), "Nothing else may be placed");
            context.complete();
        } finally {
            if (world.getBlockEntity(chestPos) instanceof Inventory inventory) {
                inventory.clear();
            }
            world.setBlockState(chestPos, Blocks.AIR.getDefaultState());
            data.releaseId(UniqueWeaponManager.WORLD_OWNER, ID);
            if (originalOwner != null) {
                data.claimId(originalOwner, ID);
            }
            data.setVillageSites(originalSites);
        }
    }
}
