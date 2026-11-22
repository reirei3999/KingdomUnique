package com.kingdomunique.effect;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.ModItems;
import com.kingdomunique.registry.CurseRegistry;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

public final class DestructionPickaxeGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void destructionPickaxeOnlyVoidsItsOwnDrops(TestContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
        Block.dropStacks(Blocks.STONE.getDefaultState(), world, pos, null, null,
                new ItemStack(ModItems.DESTRUCTION_PICKAXE));
        context.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1), e -> true).isEmpty(),
                "Blocks broken with the destruction pickaxe must drop nothing");
        Block.dropStacks(Blocks.STONE.getDefaultState(), world, pos, null, null,
                new ItemStack(Items.DIAMOND_PICKAXE));
        context.assertTrue(!world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1), e -> true).isEmpty(),
                "Other tools must keep their drops");
        BlockPos emptyToolPos = context.getAbsolutePos(new BlockPos(4, 2, 4));
        Block.dropStacks(Blocks.DIRT.getDefaultState(), world, emptyToolPos, null, null, ItemStack.EMPTY);
        context.assertTrue(!world.getEntitiesByClass(ItemEntity.class, new Box(emptyToolPos).expand(1), e -> true).isEmpty(),
                "Empty-hand drops must remain unaffected");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "destruction_pickaxe")
    public void miningBonusRequiresMainHandAndRemovesLegacyBonus(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "pickaxe_test"), SyncedClientOptions.createDefault());
        var data = WorldUniqueData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.DESTRUCTION), "Curse must be claimable");
        try {
            var speed = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
            speed.addTemporaryModifier(new EntityAttributeModifier(
                    Identifier.of(KingdomUniqueMod.MOD_ID, "destruction_mining"), 401,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            player.getInventory().setStack(9, new ItemStack(ModItems.DESTRUCTION_PICKAXE));
            CurseEffects.applyAttributes(player);
            context.assertTrue(speed.getValue() == speed.getBaseValue(),
                    "Ownership and inventory storage must not boost mining; legacy modifier must be removed");
            var modifiers = new ItemStack(ModItems.DESTRUCTION_PICKAXE)
                    .getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
            var mining = modifiers.modifiers().stream()
                    .filter(entry -> entry.attribute().equals(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED)).toList();
            context.assertTrue(mining.size() == 1, "Pickaxe must carry one mining bonus");
            var bonus = mining.getFirst();
            context.assertTrue(bonus.slot() == AttributeModifierSlot.MAINHAND,
                    "Mining bonus must not apply from offhand or inventory");
            context.assertTrue(bonus.modifier().value() == 401.0D
                            && bonus.modifier().operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    "Main's 402x mining speed must be preserved while held");
            player.getInventory().clear();
            CurseEffects.applyAttributes(player);
            context.assertTrue(speed.getValue() == speed.getBaseValue(), "Empty hands must not receive a curse bonus");
            context.complete();
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.DESTRUCTION);
        }
    }
}
