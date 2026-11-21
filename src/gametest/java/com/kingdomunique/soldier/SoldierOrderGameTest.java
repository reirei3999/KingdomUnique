package com.kingdomunique.soldier;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.network.SoldierOrderPayload;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class SoldierOrderGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 240)
    public void soldierWaitsAfterMovingAndReturnsAfterCombat(TestContext context) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 2; z++) {
                context.setBlockState(x, 8, z, Blocks.STONE);
            }
        }
        var world = context.getWorld();
        var king = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "soldier_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        var soldier = context.spawnEntity(EntityType.VILLAGER, 1.5f, 9.0f, 1.5f);
        CitizenVillager citizen = (CitizenVillager) soldier;
        citizen.ku$setRole(CitizenVillager.ROLE_SOLDIER);
        citizen.ku$setOwnerUuid(king.getUuid().toString());
        BlockPos destination = context.getAbsolutePos(new BlockPos(8, 9, 1));

        SoldierOrderManager.handleOrder(king, new SoldierOrderPayload(false, soldier.getId(), destination, 0));

        context.runAtTick(80, () -> {
            context.assertTrue(soldier.getBlockPos().getSquaredDistance(destination) <= 6.25D,
                    "The soldier must reach the ordered position");
            context.assertTrue(soldier.getNavigation().isIdle(), "The soldier must stop navigating on arrival");
            soldier.getBrain().remember(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(context.getAbsolutePos(new BlockPos(1, 9, 1)), 1.0F, 0));
        });
        context.runAtTick(100, () -> {
            context.assertFalse(soldier.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET),
                    "Holding position must suppress the villager's own walk targets");
            context.assertTrue(soldier.getNavigation().isIdle(), "The soldier must stay idle while safe");

            var enemy = context.spawnEntity(EntityType.HUSK, 1.5f, 9.0f, 1.5f);
            enemy.setAiDisabled(true);
            context.waitAndRun(20, () -> {
                context.assertEquals(soldier.getTarget(), enemy, "A holding soldier must still react to threats");
                enemy.discard();
            });
        });
        context.runAtTick(200, () -> {
            context.assertTrue(soldier.getTarget() == null, "The soldier must clear the removed threat");
            context.assertTrue(soldier.getBlockPos().getSquaredDistance(destination) <= 6.25D,
                    "The soldier must return to the ordered position after combat");
            context.assertTrue(soldier.getNavigation().isIdle(), "The soldier must resume waiting after combat");
            context.complete();
        });
    }
}
