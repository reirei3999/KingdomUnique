package com.kingdomunique.unique;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.UniqueArmorItem;
import com.kingdomunique.item.unique.UniqueArmorTriggers;
import com.kingdomunique.item.unique.UniqueArmors;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.enums.VaultState;
import net.minecraft.block.vault.VaultConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.BuriedTreasureGenerator;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class UniqueArmorAcquisitionGameTest {
    private static final String BATCH = "unique_armor_acquisition";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void wardenKillGrantsHadesHelmetOncePerWorld(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.HADES_HELMET, player -> {
            kill(context, player, EntityType.ZOMBIE);
            assertNotGranted(context, player, UniqueArmors.HADES_HELMET);
            kill(context, player, EntityType.WARDEN);
            assertGranted(context, player, UniqueArmors.HADES_HELMET);

            var second = player(context);
            kill(context, second, EntityType.WARDEN);
            assertGranted(context, player, UniqueArmors.HADES_HELMET);
            context.assertFalse(hasArmor(second, UniqueArmors.HADES_HELMET), "The second player must not get a copy");
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void onlyAnEmptyHandRavagerKillGrantsNemeanHide(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.NEMEAN_LION_HIDE, player -> {
            player.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
            kill(context, player, EntityType.RAVAGER);
            assertNotGranted(context, player, UniqueArmors.NEMEAN_LION_HIDE);
            player.getInventory().setStack(0, ItemStack.EMPTY);
            kill(context, player, EntityType.RAVAGER);
            assertGranted(context, player, UniqueArmors.NEMEAN_LION_HIDE);
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void tenPhantomsInOneNightGrantHermesTalaria(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.HERMES_TALARIA, player -> {
            var world = context.getWorld();
            long savedTime = world.getTimeOfDay();
            try {
                world.setTimeOfDay(14000);
                for (int i = 0; i < 9; i++) {
                    kill(context, player, EntityType.PHANTOM);
                }
                assertNotGranted(context, player, UniqueArmors.HERMES_TALARIA);

                world.setTimeOfDay(30000);
                kill(context, player, EntityType.PHANTOM);
                assertNotGranted(context, player, UniqueArmors.HERMES_TALARIA);

                world.setTimeOfDay(38000);
                for (int i = 0; i < 9; i++) {
                    kill(context, player, EntityType.PHANTOM);
                }
                assertNotGranted(context, player, UniqueArmors.HERMES_TALARIA);
                kill(context, player, EntityType.PHANTOM);
                assertGranted(context, player, UniqueArmors.HERMES_TALARIA);
            } finally {
                world.setTimeOfDay(savedTime);
            }
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void tenMaxPowerEnchantsAtTheSameTableGrantMimirHelmet(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.MIMIR_HELMET, player -> {
            BlockPos table = context.getAbsolutePos(new BlockPos(3, 1, 3));
            BlockPos otherTable = table.up(4);
            var world = context.getWorld();
            var shelves = EnchantingTableBlock.POWER_PROVIDER_OFFSETS.subList(0, 15);
            world.setBlockState(table, Blocks.ENCHANTING_TABLE.getDefaultState());
            world.setBlockState(otherTable, Blocks.ENCHANTING_TABLE.getDefaultState());
            try {
                for (var offset : shelves) {
                    world.setBlockState(table.add(offset), Blocks.BOOKSHELF.getDefaultState());
                    world.setBlockState(otherTable.add(offset), Blocks.BOOKSHELF.getDefaultState());
                }
                BlockPos removedShelf = table.add(shelves.getLast());
                world.setBlockState(removedShelf, Blocks.AIR.getDefaultState());
                var handler = new EnchantmentScreenHandler(1, player.getInventory(), ScreenHandlerContext.create(world, table));
                enchantBook(context, player, handler, 2, false);
                world.setBlockState(removedShelf, Blocks.BOOKSHELF.getDefaultState());

                enchantBook(context, player, handler, 0, false);
                var other = new EnchantmentScreenHandler(2, player.getInventory(), ScreenHandlerContext.create(world, otherTable));
                enchantBook(context, player, other, 2, true);
                for (int i = 0; i < 9; i++) {
                    enchantBook(context, player, handler, 2, true);
                }
                assertNotGranted(context, player, UniqueArmors.MIMIR_HELMET);
                enchantBook(context, player, handler, 2, true);
                assertGranted(context, player, UniqueArmors.MIMIR_HELMET);
            } finally {
                for (var offset : shelves) {
                    world.setBlockState(table.add(offset), Blocks.AIR.getDefaultState());
                    world.setBlockState(otherTable.add(offset), Blocks.AIR.getDefaultState());
                }
                world.setBlockState(table, Blocks.AIR.getDefaultState());
                world.setBlockState(otherTable, Blocks.AIR.getDefaultState());
            }
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void fiveDistinctOminousVaultsInAChamberGrantJotunLeggings(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.JOTUN_LEGGINGS, player -> {
            var world = context.getWorld();
            var trialChambers = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.TRIAL_CHAMBERS);
            var anchor = context.getAbsolutePos(new BlockPos(1, 1, 1));
            var chunk = world.getChunk(anchor);
            var origin = chunk.getPos().getBlockPos(2, anchor.getY(), 2);
            var piece = new BuriedTreasureGenerator.Piece(origin);
            // GameTest worlds do not generate chambers. Install only the structure metadata the vault consults.
            piece.getBoundingBox().encompass(origin.add(5, 0, 0));
            var chamber = new StructureStart(trialChambers, chunk.getPos(), 0, new StructurePiecesList(List.of(piece)));
            var starts = new HashMap<>(chunk.getStructureStarts());
            var references = new HashMap<>(chunk.getStructureReferences());
            try {
                chunk.setStructureStart(trialChambers, chamber);
                var fixtureReferences = new HashMap<>(references);
                fixtureReferences.remove(trialChambers);
                chunk.setStructureReferences(fixtureReferences);
                chunk.addStructureReference(trialChambers, chunk.getPos().toLong());

                unlockVault(context, player, origin.south(2), true);
                unlockVault(context, player, origin.east(5), false);
                for (int i = 0; i < 4; i++) {
                    unlockVault(context, player, origin.east(i), true);
                }
                unlockVault(context, player, origin, true);
                assertNotGranted(context, player, UniqueArmors.JOTUN_LEGGINGS);
                unlockVault(context, player, origin.east(4), true);
                assertGranted(context, player, UniqueArmors.JOTUN_LEGGINGS);
            } finally {
                chunk.setStructureStarts(starts);
                chunk.setStructureReferences(references);
            }
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
    public void striderTravelAcrossRidesGrantsBootsAt333LavaBlocks(TestContext context) {
        withUnclaimedArmor(context, UniqueArmors.SALAMANDER_BOOTS, player -> {
            var world = context.getWorld();
            var start = context.getAbsolutePos(new BlockPos(2, 12, 3));
            var strider = context.spawnMob(EntityType.STRIDER, 2, 2, 3);
            try {
                for (int i = 0; i <= 3; i++) {
                    world.setBlockState(start.east(i), Blocks.STONE.getDefaultState());
                    world.setBlockState(start.east(i).up(), Blocks.AIR.getDefaultState());
                }
                context.assertTrue(player.startRiding(strider, true), "Test player must mount the strider");
                rideBackAndForth(player, strider, start, 2);
                assertNotGranted(context, player, UniqueArmors.SALAMANDER_BOOTS);
                player.stopRiding();
                UniqueArmorTriggers.tick(player);

                for (int i = 0; i < 4; i++) {
                    world.setBlockState(start.east(i), Blocks.LAVA.getDefaultState());
                }
                context.assertTrue(player.startRiding(strider, true), "Test player must mount over lava");
                rideBackAndForth(player, strider, start, 27);
                assertNotGranted(context, player, UniqueArmors.SALAMANDER_BOOTS);
                player.stopRiding();
                UniqueArmorTriggers.tick(player);
                context.assertTrue(player.startRiding(strider, true), "Test player must remount");
                rideBackAndForth(player, strider, start, 28);
                assertNotGranted(context, player, UniqueArmors.SALAMANDER_BOOTS);

                moveStrider(player, strider, start, 2);
                assertNotGranted(context, player, UniqueArmors.SALAMANDER_BOOTS);
                moveStrider(player, strider, start, 3);
                assertGranted(context, player, UniqueArmors.SALAMANDER_BOOTS);
            } finally {
                player.stopRiding();
                UniqueArmorTriggers.resetTravel(player);
                strider.discard();
                for (int i = 0; i <= 3; i++) {
                    world.setBlockState(start.east(i), Blocks.AIR.getDefaultState());
                }
            }
        });
    }

    private static void enchantBook(TestContext context, ServerPlayerEntity player, EnchantmentScreenHandler handler,
            int option, boolean maxPower) {
        player.experienceLevel = 30;
        handler.getSlot(0).setStack(new ItemStack(Items.BOOK));
        handler.getSlot(1).setStack(new ItemStack(Items.LAPIS_LAZULI, option + 1));
        context.assertTrue((handler.enchantmentPower[option] == 30) == maxPower, "Unexpected enchantment offer");
        context.assertTrue(handler.onButtonClick(player, option), "The enchantment must succeed");
        context.assertTrue(handler.getSlot(0).getStack().isOf(Items.ENCHANTED_BOOK)
                && handler.getSlot(1).getStack().isEmpty(), "The book and last lapis must be consumed");
    }

    private static void unlockVault(TestContext context, ServerPlayerEntity player, BlockPos pos, boolean ominous) {
        var world = context.getWorld();
        var original = world.getBlockState(pos);
        var state = Blocks.VAULT.getDefaultState().with(VaultBlock.OMINOUS, ominous)
                .with(VaultBlock.VAULT_STATE, VaultState.ACTIVE);
        world.setBlockState(pos, state);
        try {
            var vault = (VaultBlockEntity) world.getBlockEntity(pos);
            var key = new ItemStack(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY);
            var config = new VaultConfig(ominous ? LootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_CHEST
                    : LootTables.TRIAL_CHAMBERS_REWARD_CHEST, 4, 4.5, key.copy(), Optional.empty());
            vault.setConfig(config);
            VaultBlockEntity.Server.tryUnlock(world, pos, state, config, vault.getServerData(), vault.getSharedData(),
                    player, key);
            context.assertTrue(key.isEmpty(), "The test vault must actually unlock");
        } finally {
            world.setBlockState(pos, original);
        }
    }

    private static void rideBackAndForth(ServerPlayerEntity player, MobEntity strider, BlockPos start, int trips) {
        moveStrider(player, strider, start, 0);
        for (int i = 0; i < trips; i++) {
            moveStrider(player, strider, start, 3);
            moveStrider(player, strider, start, 0);
        }
    }

    private static void moveStrider(ServerPlayerEntity player, MobEntity strider, BlockPos start, int distance) {
        strider.setPosition(start.getX() + 0.5 + distance, start.getY() + 1, start.getZ() + 0.5);
        UniqueArmorTriggers.tick(player);
    }

    private static void kill(TestContext context, ServerPlayerEntity player, EntityType<? extends MobEntity> type) {
        var mob = context.spawnMob(type, 1, 1, 1);
        try {
            mob.setHealth(1);
            mob.damage(player.getDamageSources().playerAttack(player), 100);
            context.assertFalse(mob.isAlive(), "The test mob must die from player damage");
        } finally {
            mob.discard();
        }
    }

    private static void withUnclaimedArmor(TestContext context, UniqueArmorItem armor, Consumer<ServerPlayerEntity> test) {
        var player = player(context);
        var ownership = WorldUniqueData.getOrCreate(player.getServer().getOverworld());
        String id = Registries.ITEM.getId(armor).getPath();
        UUID previousOwner = ownership.getOwner(id);
        if (previousOwner != null) {
            ownership.releaseId(previousOwner, id);
        }
        try {
            test.accept(player);
        } finally {
            UUID owner = ownership.getOwner(id);
            if (owner != null) {
                ownership.releaseId(owner, id);
            }
            if (previousOwner != null) {
                ownership.claimId(previousOwner, id);
            }
        }
        context.complete();
    }

    private static void assertNotGranted(TestContext context, ServerPlayerEntity player, UniqueArmorItem armor) {
        var ownership = WorldUniqueData.getOrCreate(player.getServer().getOverworld());
        context.assertFalse(ownership.isClaimed(Registries.ITEM.getId(armor).getPath()), "The reward must remain unclaimed");
        context.assertFalse(hasArmor(player, armor), "The player must not receive the armor yet");
    }

    private static void assertGranted(TestContext context, ServerPlayerEntity player, UniqueArmorItem armor) {
        var ownership = WorldUniqueData.getOrCreate(player.getServer().getOverworld());
        context.assertTrue(ownership.hasId(player.getUuid(), Registries.ITEM.getId(armor).getPath()),
                "The armor must be claimed for this player");
        context.assertTrue(hasArmor(player, armor), "The armor must be placed in the player's inventory");
    }

    private static boolean hasArmor(ServerPlayerEntity player, UniqueArmorItem armor) {
        return player.getInventory().contains(stack -> stack.isOf(armor));
    }

    private static ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), "armor_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        return player;
    }
}
