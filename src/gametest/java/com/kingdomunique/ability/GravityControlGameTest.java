package com.kingdomunique.ability;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class GravityControlGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void bossesCannotBeLifted(TestContext context) {
        context.assertTrue(AbilityActions.canLift(context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1))),
                "Ordinary mobs must be liftable");
        context.assertFalse(AbilityActions.canLift(context.spawnEntity(EntityType.WARDEN, new BlockPos(3, 2, 3))),
                "The warden must be too heavy to lift");
        context.assertFalse(AbilityActions.canLift(context.spawnEntity(EntityType.WITHER, new BlockPos(5, 2, 5))),
                "The wither must be too heavy to lift");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void heldPlayerBreaksFreeWhenHurt(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity holder = player(world);
        ServerPlayerEntity victim = player(world);
        PlayerAbilityState state = AbilityState.get(holder.getUuid());
        state.setGravityTarget(victim.getUuid());
        victim.setNoGravity(true);

        victim.damage(world.getDamageSources().outOfWorld(), 1.0F);
        context.assertTrue(state.getGravityTarget() == null, "Damage must release a held player");
        context.assertFalse(victim.hasNoGravity(), "The released player must fall normally");
        AbilityState.remove(holder.getUuid());
        context.complete();
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "gravity_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
