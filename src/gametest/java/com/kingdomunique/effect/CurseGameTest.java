package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

public final class CurseGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void glassCannonRestoresMaxHealthWhenLost(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "curse_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        WorldUniqueData data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        UUID previous = data.getOwner(CurseRegistry.GLASS_CANNON);
        if (previous != null) {
            data.releaseId(previous, CurseRegistry.GLASS_CANNON);
        }
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.GLASS_CANNON),
                "The test player must be able to claim glass cannon");
        try {
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 10.0F, "Glass cannon must cap max health at 10");
            context.assertTrue(player.getHealth() == 10.0F, "Current health must be clamped to max health");

            UUID previousReaper = data.getOwner(CurseRegistry.REAPER_HOURGLASS);
            if (previousReaper != null) {
                data.releaseId(previousReaper, CurseRegistry.REAPER_HOURGLASS);
            }
            context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.REAPER_HOURGLASS),
                    "The test player must be able to claim reaper hourglass");
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 7.0F, "Glass cannon with reaper must give 7 max health");
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 7.0F, "Repeated application must keep 7 max health");
            data.releaseId(player.getUuid(), CurseRegistry.REAPER_HOURGLASS);

            data.releaseId(player.getUuid(), CurseRegistry.GLASS_CANNON);
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 20.0F, "Max health must return to 20 without the curse");

            player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(10.0D);
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 20.0F, "Legacy max health must be restored to 20");

            player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40.0D);
            CurseEffects.applyAttributes(player);
            context.assertTrue(player.getMaxHealth() == 40.0F, "An admin-set max health must not be overwritten");
            context.complete();
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.GLASS_CANNON);
            data.releaseId(player.getUuid(), CurseRegistry.REAPER_HOURGLASS);
        }
    }
}
