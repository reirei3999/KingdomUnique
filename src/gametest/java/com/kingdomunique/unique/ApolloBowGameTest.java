package com.kingdomunique.unique;

import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class ApolloBowGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "apollo_bow")
    public void detonatesInFlightDuringFiringCooldown(TestContext context) {
        ServerPlayerEntity player = shooter(context);
        ArrowEntity arrow = fire(context, player);
        Vec3d start = arrow.getPos();
        var target = context.spawnMob(EntityType.PIG, 5, 1, 3);
        target.setAiDisabled(true);

        context.runAtTick(2, () -> {
            context.assertTrue(arrow.isAlive() && arrow.getPos().distanceTo(start) > 1.0D,
                    "The light arrow must still be flying");
            context.assertTrue(player.getItemCooldownManager().isCoolingDown(UniqueWeapons.APOLLO_BOW),
                    "The firing cooldown must still be active");
            rightClick(player);
            context.assertTrue(arrow.isRemoved(), "Right-click must detonate a flying light arrow during cooldown");
            context.assertFalse(target.isAlive(), "The detonation must damage nearby targets");
            context.assertTrue(player.getItemCooldownManager().isCoolingDown(UniqueWeapons.APOLLO_BOW),
                    "Detonation must not reset the firing cooldown");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "apollo_bow")
    public void cannotDetonateAfterHittingBlock(TestContext context) {
        context.setBlockState(3, 2, 4, Blocks.STONE);
        ServerPlayerEntity player = shooter(context);
        ArrowEntity arrow = fire(context, player);

        context.runAtTick(8, () -> {
            context.assertTrue(arrow.isAlive() && arrow.writeNbt(new NbtCompound()).getBoolean("inGround"),
                    "The light arrow must be stuck in the wall");
            player.getItemCooldownManager().remove(UniqueWeapons.APOLLO_BOW);
            rightClick(player);
            context.assertTrue(arrow.isAlive(), "A grounded light arrow must not detonate");
            arrow.discard();
            context.complete();
        });
    }

    private static ServerPlayerEntity shooter(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "apollo_test"), SyncedClientOptions.createDefault()) {
            @Override
            protected ItemCooldownManager createCooldownManager() {
                return new ItemCooldownManager();
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        Vec3d pos = context.getAbsolute(new Vec3d(3.5D, 1.0D, 0.5D));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.APOLLO_BOW));
        return player;
    }

    private static ArrowEntity fire(TestContext context, ServerPlayerEntity player) {
        UniqueWeaponManager.fireLightArrowWithCooldown(player);
        var arrows = context.getWorld().getEntitiesByClass(ArrowEntity.class,
                new Box(player.getEyePos(), player.getEyePos()).expand(2.0D),
                arrow -> arrow.getCommandTags().contains("ku_light_arrow"));
        context.assertEquals(arrows.size(), 1, "The bow must fire one light arrow");
        return arrows.getFirst();
    }

    private static void rightClick(ServerPlayerEntity player) {
        player.interactionManager.interactItem(player, player.getWorld(), player.getMainHandStack(), Hand.MAIN_HAND);
    }
}
