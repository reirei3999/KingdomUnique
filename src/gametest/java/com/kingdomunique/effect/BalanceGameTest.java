package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.ability.AbilityPassives;
import com.kingdomunique.ability.AbilityState;
import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.FinalEventHandler;
import com.kingdomunique.mixin.ForgingScreenHandlerAccessor;
import com.kingdomunique.util.EnhancementUtil;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.UUID;

public final class BalanceGameTest {
    private static ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), "balance_player");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 1)), 0, 0);
        return player;
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void miningBuffsWorkWithoutEfficiency(TestContext context) {
        var player = player(context);
        var data = WorldUniqueData.getOrCreate(context.getWorld().getServer().getOverworld());
        String[] ids = {CurseRegistry.GLASS_CANNON, CurseRegistry.SOLITUDE, CurseRegistry.DESTRUCTION,
                UniqueContentRegistry.SITE_SUPERVISOR};
        double[] multipliers = {5.0D, 3.0D, 402.0D, 3.0D};
        for (int i = 0; i < ids.length; i++) {
            context.assertTrue(data.claimId(player.getUuid(), ids[i]), "Mining ability must be available");
            try {
                CurseEffects.applyAttributes(player);
                AbilityPassives.applyAttributes(player);
                context.assertTrue(player.getAttributeValue(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED) == multipliers[i],
                        "Mining multiplier must work without Efficiency: " + ids[i]);
            } finally {
                data.releaseId(player.getUuid(), ids[i]);
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void hammerProducesRealEnchantments(TestContext context) throws ReflectiveOperationException {
        var world = context.getWorld();
        var generated = new java.util.ArrayList<ItemStack>();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "hammer_test"), SyncedClientOptions.createDefault()) {
            @Override
            public boolean giveItemStack(ItemStack stack) {
                generated.add(stack);
                return true;
            }
        };
        var method = RelicEffects.class.getDeclaredMethod("tickHammer", ServerPlayerEntity.class);
        method.setAccessible(true);
        long originalTime = world.getTime();
        try {
            world.getServer().getSaveProperties().getMainWorldProperties().setTime(600L);
            for (int i = 0; i < 1000 && generated.isEmpty(); i++) {
                method.invoke(null, player);
            }
            context.assertTrue(generated.size() == 1, "Hammer must generate a book");
            var enchantments = generated.getFirst().get(DataComponentTypes.STORED_ENCHANTMENTS);
            context.assertTrue(enchantments != null && !enchantments.isEmpty(), "Book must contain enchantments");
            for (var entry : enchantments.getEnchantmentEntries()) {
                context.assertTrue(entry.getIntValue() >= entry.getKey().value().getMinLevel()
                                && entry.getIntValue() <= entry.getKey().value().getMaxLevel(),
                        "Generated enchantment level must be valid");
            }
            world.getServer().getSaveProperties().getMainWorldProperties().setTime(601L);
            method.invoke(null, player);
            context.assertTrue(generated.size() == 1, "Hammer only checks every 600 ticks");
        } finally {
            world.getServer().getSaveProperties().getMainWorldProperties().setTime(originalTime);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void slaughterOnlyGrowsOnNeutralAndHostileMobs(TestContext context) {
        var world = context.getWorld();
        context.assertFalse(FinalEventHandler.isSlaughterGrowthTarget(EntityType.COW.create(world)), "Cows are excluded");
        context.assertFalse(FinalEventHandler.isSlaughterGrowthTarget(EntityType.VILLAGER.create(world)), "Villagers are excluded");
        context.assertTrue(FinalEventHandler.isSlaughterGrowthTarget(EntityType.ZOMBIE.create(world)), "Hostiles count");
        context.assertTrue(FinalEventHandler.isSlaughterGrowthTarget(EntityType.WOLF.create(world)), "Neutral wolves count");
        context.assertTrue(FinalEventHandler.isSlaughterGrowthTarget(EntityType.LLAMA.create(world)), "Neutral llamas count");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void citizenCountRequiresMatchingRegistration(TestContext context) {
        var world = context.getWorld();
        var player = player(context);
        var villagers = new java.util.ArrayList<net.minecraft.entity.passive.VillagerEntity>();
        context.assertTrue(RelicEffects.countCitizens(player) == 0, "No registered citizens means zero");
        try {
            for (String owner : java.util.List.of("", UUID.randomUUID().toString(), player.getUuidAsString())) {
                var villager = EntityType.VILLAGER.create(world);
                villager.refreshPositionAndAngles(player.getBlockPos(), 0, 0);
                ((CitizenVillager) villager).ku$setOwnerUuid(owner);
                world.spawnEntity(villager);
                villagers.add(villager);
            }
            context.assertTrue(RelicEffects.countCitizens(player) == 1, "Only the matching owned citizen counts");
        } finally {
            villagers.forEach(net.minecraft.entity.Entity::discard);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void indomitableResistanceAndDelayedRegeneration(TestContext context) throws ReflectiveOperationException {
        var player = player(context);
        var method = AbilityPassives.class.getDeclaredMethod("tickIndomitable", ServerPlayerEntity.class);
        method.setAccessible(true);
        var state = AbilityState.get(player.getUuid());
        try {
            state.markDamaged(System.currentTimeMillis());
            method.invoke(null, player);
            context.assertTrue(player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() == 1,
                    "Resistance II is active immediately after damage");
            context.assertFalse(player.hasStatusEffect(StatusEffects.REGENERATION), "Damage delays regeneration");
            state.markDamaged(System.currentTimeMillis() - 10001L);
            method.invoke(null, player);
            context.assertTrue(player.getStatusEffect(StatusEffects.REGENERATION).getAmplifier() == 2,
                    "Ten seconds without damage grants regeneration III");
        } finally {
            AbilityState.remove(player.getUuid());
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void divineSmithShiftClickRetainsUpgrade(TestContext context) {
        var player = player(context);
        player.getAbilities().creativeMode = true;
        var data = WorldUniqueData.getOrCreate(context.getWorld().getServer().getOverworld());
        context.assertTrue(data.claimId(player.getUuid(), "balance_first_claim"), "First claim must succeed");
        context.assertTrue(data.claimId(player.getUuid(), UniqueContentRegistry.DIVINE_SMITH), "Smith must be available");
        boolean bonusSeen = false;
        try {
            for (int i = 0; i < 64 && !bonusSeen; i++) {
                var handler = new AnvilScreenHandler(0, player.getInventory(),
                        ScreenHandlerContext.create(context.getWorld(), player.getBlockPos()));
                var inputs = ((ForgingScreenHandlerAccessor) handler).ku$input();
                inputs.setStack(0, new ItemStack(Items.DIAMOND_PICKAXE));
                inputs.setStack(1, new ItemStack(Items.DIAMOND_PICKAXE));
                handler.updateResult();
                context.assertTrue(EnhancementUtil.getLevel(handler.getSlot(2).getStack()) == 1,
                        "Preview remains a fixed +1");
                handler.quickMove(player, 2);
                int results = 0;
                for (int slot = 0; slot < player.getInventory().size(); slot++) {
                    var result = player.getInventory().getStack(slot);
                    if (result.isOf(Items.DIAMOND_PICKAXE)) {
                        results++;
                        int level = EnhancementUtil.getLevel(result);
                        context.assertTrue(level >= 1 && level <= 3, "Transferred result has a real upgrade");
                        context.assertTrue(result.getMaxDamage() == Math.round(1561 * EnhancementUtil.multiplier(level)),
                                "Upgrade must increase actual durability");
                        var tool = result.get(DataComponentTypes.TOOL);
                        context.assertTrue(tool.defaultMiningSpeed() > 2.0F, "Upgrade must increase actual tool speed");
                        bonusSeen |= level > 1;
                    }
                }
                context.assertTrue(results == 1, "Transfer must produce exactly one upgraded item");
                player.getInventory().clear();
            }
            context.assertTrue(bonusSeen, "Smith must receive bonus levels even when not the first claimed ability");
        } finally {
            data.releaseId(player.getUuid(), UniqueContentRegistry.DIVINE_SMITH);
            data.releaseId(player.getUuid(), "balance_first_claim");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void phoenixClaimSurvivesReload(TestContext context) {
        var data = new WorldUniqueData(new NbtCompound());
        var owner = UUID.randomUUID();
        context.assertTrue(data.claimPhoenixRevival(owner, 0), "First revival is available on day zero");
        context.assertFalse(data.claimPhoenixRevival(owner, 0), "Same-day revival cannot repeat");
        var saved = data.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        saved.getCompound("phoenix_revivals").putLong("invalid-uuid", 0);
        var loaded = new WorldUniqueData(saved);
        context.assertFalse(loaded.claimPhoenixRevival(owner, 0), "Reload must retain the used revival");
        context.assertTrue(loaded.claimPhoenixRevival(owner, 1), "Next day must allow revival");
        context.assertTrue(loaded.claimPhoenixRevival(UUID.randomUUID(), 0), "Other players are independent");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void curseMultipliersRespectOwnership(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "balance_test"), SyncedClientOptions.createDefault());
        var data = WorldUniqueData.getOrCreate(world.getServer().getOverworld());
        context.assertTrue(CurseEffects.incomingMultiplier(player) == 1.0D, "No shackles means normal damage");
        context.assertTrue(CurseEffects.rollChaosMultiplier(player) == 1.0D, "No dice means normal damage");
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.SHACKLES), "Shackles are available");
        context.assertTrue(data.claimId(player.getUuid(), CurseRegistry.CHAOS), "Dice are available");
        try {
            context.assertTrue(CurseEffects.incomingMultiplier(player) == 0.4D, "Shackles cut incoming damage by 60%");
            player.getRandom().setSeed(12345L);
            boolean downside = false;
            for (int i = 0; i < 1000; i++) {
                double roll = CurseEffects.rollChaosMultiplier(player);
                context.assertTrue(roll >= 0.2D && roll < 2.7D, "Dice stay within the new range");
                downside |= roll < 0.7D;
            }
            context.assertTrue(downside, "Dice must roll below the old minimum");
        } finally {
            data.releaseId(player.getUuid(), CurseRegistry.SHACKLES);
            data.releaseId(player.getUuid(), CurseRegistry.CHAOS);
        }
        context.complete();
    }

}
