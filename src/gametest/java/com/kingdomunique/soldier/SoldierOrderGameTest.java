package com.kingdomunique.soldier;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.item.ModItems;
import com.kingdomunique.network.SoldierOrderPayload;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void massAndIndividualOrdersOnlyReachTheIssuingOwnersSoldiers(TestContext context) {
        int y = 32;
        floor(context, y);
        var king = king(context, y);
        var otherKing = king(context, y);
        var first = soldier(context, king, 1.5f, y, 1.5f);
        var second = soldier(context, king, 3.5f, y, 1.5f);
        var foreign = soldier(context, otherKing, 5.5f, y, 1.5f);
        BlockPos destination = context.getAbsolutePos(new BlockPos(3, y, 1));
        WalkTarget wander = new WalkTarget(destination, 1.0f, 0);

        foreign.getBrain().remember(MemoryModuleType.WALK_TARGET, wander);
        SoldierOrderManager.handleOrder(king, new SoldierOrderPayload(false, foreign.getId(), destination, 0));
        SoldierOrderManager.waitAtHoldPosition(foreign);
        context.assertTrue(foreign.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET),
                "An individual order must not control a foreign soldier");

        first.getBrain().remember(MemoryModuleType.WALK_TARGET, wander);
        second.getBrain().remember(MemoryModuleType.WALK_TARGET, wander);
        SoldierOrderManager.handleOrder(king, new SoldierOrderPayload(true, -1, destination, 0));
        SoldierOrderManager.waitAtHoldPosition(first);
        SoldierOrderManager.waitAtHoldPosition(second);
        SoldierOrderManager.waitAtHoldPosition(foreign);
        context.assertFalse(first.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET),
                "A mass order must reach the first owned soldier");
        context.assertFalse(second.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET),
                "A mass order must reach the second owned soldier");
        context.assertTrue(foreign.getBrain().hasMemoryModule(MemoryModuleType.WALK_TARGET),
                "The same order must not reach a foreign soldier");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
    public void hiddenEnemyDoesNotMaskAnEnemyThatFightsBack(TestContext context) {
        int y = 44;
        floor(context, y);
        var soldier = soldier(context, king(context, y), 1.5f, y, 1.5f);
        for (int offset = 0; offset <= 2; offset++) {
            context.setBlockState(1, y + offset, 4, Blocks.STONE);
        }
        var hidden = context.spawnEntity(EntityType.HUSK, 1.5f, y, 7.5f);
        hidden.setAiDisabled(true);
        var visible = context.spawnEntity(EntityType.HUSK, 9.5f, y, 1.5f);
        context.assertFalse(soldier.canSee(hidden), "The nearby enemy must be hidden by the wall");
        context.assertTrue(soldier.canSee(visible), "The farther enemy must be visible");
        context.assertEquals(SoldierOrderManager.findVisibleEnemyInSight(context.getWorld(), soldier, 14),
                visible, "A nearer hidden enemy must not block acquisition of a visible target");
        visible.setPosition(soldier.getX() + 1.5D, soldier.getY(), soldier.getZ());
        visible.setTarget(soldier);
        visible.tryAttack(soldier);
        context.runAtTick(85, () -> {
            context.assertTrue(soldier.getHealth() < soldier.getMaxHealth(), "The active enemy must damage the soldier");
            context.assertTrue(visible.getHealth() < visible.getMaxHealth(), "The soldier must fight the active enemy");
            visible.discard();
            hidden.discard();
        });
        context.runAtTick(105, () -> {
            context.assertTrue(soldier.getTarget() == null, "The soldier must clear despawned enemies");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void slaughterBladeSweepDoesNotChainPastTheOriginalTarget(TestContext context) {
        int y = 56;
        floor(context, y);
        var soldier = soldier(context, king(context, y), 1.5f, y, 1.5f);
        soldier.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SLAUGHTER_BLADE));
        var primary = context.spawnEntity(EntityType.HUSK, 3.5f, y, 1.5f);
        var nearby = context.spawnEntity(EntityType.HUSK, 6.5f, y, 1.5f);
        var distant = context.spawnEntity(EntityType.HUSK, 9.5f, y, 1.5f);
        primary.damage(context.getWorld().getDamageSources().mobAttack(soldier), 8.0f);
        context.assertTrue(nearby.getHealth() < nearby.getMaxHealth(), "The sweep must damage an enemy near the primary target");
        context.assertEquals(distant.getHealth(), distant.getMaxHealth(), "A secondary hit must not trigger its own sweep");
        context.complete();
    }

    private static void floor(TestContext context, int y) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 8; z++) {
                context.setBlockState(x, y - 1, z, Blocks.STONE);
                context.setBlockState(x, y, z, Blocks.AIR);
                context.setBlockState(x, y + 1, z, Blocks.AIR);
            }
        }
    }

    private static ServerPlayerEntity king(TestContext context, int y) {
        var world = context.getWorld();
        var king = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "soldier_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        var pos = context.getAbsolutePos(new BlockPos(0, y, 0));
        king.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return king;
    }

    private static VillagerEntity soldier(TestContext context, ServerPlayerEntity king, float x, int y, float z) {
        var soldier = context.spawnEntity(EntityType.VILLAGER, x, y, z);
        CitizenVillager citizen = (CitizenVillager) soldier;
        citizen.ku$setRole(CitizenVillager.ROLE_SOLDIER);
        citizen.ku$setOwnerUuid(king.getUuid().toString());
        return soldier;
    }
}
