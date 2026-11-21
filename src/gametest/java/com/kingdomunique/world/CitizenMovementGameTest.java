package com.kingdomunique.world;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.VillagerProfessionUtil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LoseJobOnSiteLossTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;

import java.util.UUID;

public final class CitizenMovementGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
    public void citizenWithoutWorkstationKeepsBrainAndMoves(TestContext context) {
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 3; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
                context.setBlockState(x, 2, z, Blocks.AIR);
                context.setBlockState(x, 3, z, Blocks.AIR);
            }
        }
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, 1.5F, 2, 1.5F);
        CitizenVillager citizen = (CitizenVillager) villager;
        citizen.ku$setOwnerUuid(UUID.randomUUID().toString());
        citizen.ku$setRole(CitizenVillager.ROLE_BUILDER);
        VillagerProfessionUtil.applyRole(villager, CitizenVillager.ROLE_BUILDER);
        var brain = villager.getBrain();
        context.assertFalse(brain.hasMemoryModule(MemoryModuleType.JOB_SITE), "No workstation in regression setup");
        LoseJobOnSiteLossTask.create().tryStarting(context.getWorld(), villager, context.getWorld().getTime());
        context.assertTrue(villager.getBrain() == brain, "A locked citizen job must not reset the brain");
        BlockPos target = context.getAbsolutePos(new BlockPos(8, 2, 1));
        context.runAtEveryTick(() -> {
            context.assertTrue(villager.getBrain() == brain, "Citizen brain must remain stable while walking");
            villager.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
        });
        context.addInstantFinalTask(() -> context.assertTrue(villager.squaredDistanceTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < 4,
                "Citizen must pathfind across the floor"));
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unassignedVillagerStillLosesJobWithoutWorkstation(TestContext context) {
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, 1, 2, 1);
        villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.TOOLSMITH));
        var brain = villager.getBrain();
        context.assertTrue(LoseJobOnSiteLossTask.create().tryStarting(
                context.getWorld(), villager, context.getWorld().getTime()), "Vanilla job loss must still run");
        context.assertEquals(villager.getVillagerData().getProfession(), VillagerProfession.NONE,
                "An unassigned villager must lose its job");
        context.assertTrue(villager.getBrain() != brain, "Vanilla job loss must reinitialize the brain");
        context.complete();
    }
}
