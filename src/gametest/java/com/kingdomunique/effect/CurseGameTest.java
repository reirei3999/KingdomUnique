package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.ModItems;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

public final class CurseGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void destructionPickaxeOnlyVoidsItsOwnDrops(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        BlockPos pickaxePos = context.getAbsolutePos(new BlockPos(1, 2, 1));
        BlockPos normalPos = context.getAbsolutePos(new BlockPos(4, 2, 4));

        Block.dropStacks(Blocks.STONE.getDefaultState(), world, pickaxePos, null, player,
                new ItemStack(ModItems.DESTRUCTION_PICKAXE));
        context.assertTrue(drops(world, pickaxePos) == 0, "Blocks broken with the destruction pickaxe must drop nothing");

        Block.dropStacks(Blocks.STONE.getDefaultState(), world, normalPos, null, player,
                new ItemStack(Items.DIAMOND_PICKAXE));
        context.assertTrue(drops(world, normalPos) > 0, "Other tools must keep their drops");

        AttributeModifiersComponent modifiers = new ItemStack(ModItems.DESTRUCTION_PICKAXE)
                .getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        context.assertTrue(modifiers.modifiers().stream()
                        .anyMatch(entry -> entry.attribute().equals(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED)),
                "The mining bonus must live on the pickaxe itself");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void glassCannonRestoresMaxHealthWhenLost(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        WorldUniqueData data = data(world);

        claim(context, data, player, CurseRegistry.GLASS_CANNON);
        CurseEffects.applyAttributes(player);
        context.assertTrue(player.getMaxHealth() == 10.0F, "Glass cannon must cap max health at 10");

        data.releaseId(player.getUuid(), CurseRegistry.GLASS_CANNON);
        CurseEffects.applyAttributes(player);
        context.assertTrue(player.getMaxHealth() == 20.0F, "Max health must return to 20 without the curse");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void gravityShacklesCutAllIncomingDamage(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        WorldUniqueData data = data(world);
        claim(context, data, player, CurseRegistry.SHACKLES);

        // 参加直後の無敵時間を貫通する奈落ダメージで検証する。
        player.damage(world.getDamageSources().outOfWorld(), 10.0F);
        context.assertTrue(Math.abs(player.getHealth() - 16.0F) < 0.01F,
                "Shackles must cut all damage by 60% (got " + player.getHealth() + ")");
        data.releaseId(player.getUuid(), CurseRegistry.SHACKLES);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void chaosDiceRollsBothWays(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(world);
        WorldUniqueData data = data(world);
        claim(context, data, player, CurseRegistry.CHAOS);

        for (int i = 0; i < 500; i++) {
            double out = CurseEffects.rollChaosMultiplier(player);
            double in = CurseEffects.rollChaosIncomingMultiplier(player);
            context.assertTrue(out >= 0.1D && out <= 3.0D, "Outgoing roll out of range: " + out);
            context.assertTrue(in >= 0.5D && in <= 4.0D, "Incoming roll out of range: " + in);
        }
        player.damage(world.getDamageSources().outOfWorld(), 2.0F);
        float lost = 20.0F - player.getHealth();
        context.assertTrue(lost >= 0.99F && lost <= 8.01F, "Incoming damage must be rolled 0.5x-4x (lost " + lost + ")");
        data.releaseId(player.getUuid(), CurseRegistry.CHAOS);
        context.complete();
    }

    /** テストワールドは実行間で保存されるため、以前の所有者を解放してから付与する。 */
    private static void claim(TestContext context, WorldUniqueData data, ServerPlayerEntity player, String id) {
        UUID previous = data.getOwner(id);
        if (previous != null) {
            data.releaseId(previous, id);
        }
        context.assertTrue(data.claimId(player.getUuid(), id), "The test player must be able to claim " + id);
    }

    private static int drops(ServerWorld world, BlockPos pos) {
        return world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1.0D), entity -> true).size();
    }

    private static WorldUniqueData data(ServerWorld world) {
        return WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "curse_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
