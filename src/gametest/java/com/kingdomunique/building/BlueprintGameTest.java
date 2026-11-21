package com.kingdomunique.building;

import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.network.BuildingBoxesSyncPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerSyncHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class BlueprintGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativePlacementCompletesWithoutMaterialsOrPermission(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 64, 0));
        ServerPlayerEntity player = player(world, true);
        context.assertFalse(player.hasPermissionLevel(2), "Creative construction must not require OP");
        place(ModBlocks.WATCHTOWER, world, pos, player);
        assertCompleted(context, pos, player);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void survivalPlacementStaysUnreadyWithoutMaterials(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 96, 0));
        ServerPlayerEntity player = player(world);
        BlueprintBlockEntity blueprint = place(ModBlocks.WATCHTOWER, world, pos, player);
        context.assertFalse(blueprint.isReady() || site(world, pos).ready(), "Survival placement must stay unready");
        context.assertTrue(blueprint.inventory().isEmpty(), "Placement must not supply materials");
        context.assertFalse(KingdomBuildingState.get(world).hasCompletedBuilding(player.getUuid(), BuildingType.WATCHTOWER),
                "Survival placement must not complete a building");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeClickCompletesExistingUnreadyAndReadySites(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity survival = player(world);
        ServerPlayerEntity creative = player(world, true, survival.getUuid());
        for (int height : new int[]{128, 160}) {
            BlockPos pos = context.getAbsolutePos(new BlockPos(0, height, 0));
            BlueprintBlockEntity blueprint = place(ModBlocks.WATCHTOWER, world, pos, survival);
            if (height == 160) {
                BuildData data = BuildData.getOrCreate(world);
                BuildData.BuildSite site = site(world, pos);
                data.remove(site);
                data.add(site.withReady(true));
                blueprint.setReady(true);
            }
            var state = world.getBlockState(pos);
            context.assertEquals(state.onUseWithItem(ItemStack.EMPTY, world, creative, Hand.MAIN_HAND, hit(pos)),
                    ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "Empty-hand use must reach construction controls");
            state.onUse(world, creative, hit(pos));
            assertCompleted(context, pos, creative);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeHouseCompletionDoesNotEmbedThePlayerInTheFoundation(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 184, 0));
        ServerPlayerEntity survival = player(world);
        ServerPlayerEntity creative = player(world, true, survival.getUuid());
        place(ModBlocks.HOUSE, world, pos, survival);
        BlockPos feet = pos.east(2);
        creative.setPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        context.assertTrue(world.isSpaceEmpty(creative), "The player must start clear of blocks");

        world.getBlockState(pos).onUse(world, creative, hit(pos));

        context.assertTrue(KingdomBuildingState.get(world).hasCompletedBuilding(creative.getUuid(), BuildingType.HOUSE),
                "The house must complete");
        context.assertFalse(world.getBlockState(feet.down()).isAir(),
                "The house foundation must lie under the player's former position");
        context.assertTrue(world.isSpaceEmpty(creative), "Instant construction must not leave the player inside blocks");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeNonOwnerCannotCompleteExistingSites(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = player(world);
        ServerPlayerEntity creative = player(world, true);
        for (int height : new int[]{256, 288}) {
            BlockPos pos = context.getAbsolutePos(new BlockPos(0, height, 0));
            BlueprintBlockEntity blueprint = place(ModBlocks.WATCHTOWER, world, pos, owner);
            boolean ready = height == 288;
            if (ready) {
                BuildData data = BuildData.getOrCreate(world);
                BuildData.BuildSite site = site(world, pos);
                data.remove(site);
                data.add(site.withReady(true));
                blueprint.setReady(true);
            }
            var state = world.getBlockState(pos);
            context.assertEquals(state.onUseWithItem(ItemStack.EMPTY, world, creative, Hand.MAIN_HAND, hit(pos)),
                    ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "Empty-hand use must reach construction controls");
            state.onUse(world, creative, hit(pos));
            context.assertEquals(site(world, pos).ready(), ready, "Non-owner clicks must preserve construction readiness");
            context.assertEquals(site(world, pos).owner(), owner.getUuid(), "Non-owner clicks must preserve ownership");
            context.assertTrue(world.getBlockEntity(pos) == blueprint, "Non-owner clicks must not replace the blueprint");
            context.assertTrue(blueprint.inventory().isEmpty(), "Non-owner clicks must not supply free materials");
            context.assertFalse(CompletedBuildingsData.getOrCreate(world).entries().stream()
                            .anyMatch(entry -> entry.pos().equals(pos)),
                    "Non-owner clicks must not register a completed building");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativePlacementAndClickRespectValidation(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 192, 0));
        ServerPlayerEntity creative = player(world, true);
        world.setBlockState(pos.up(), Blocks.BEDROCK.getDefaultState());
        BlueprintBlockEntity blueprint = place(ModBlocks.WATCHTOWER, world, pos, creative);
        context.assertFalse(site(world, pos).ready(), "Invalid creative placement must leave an unready site");
        ModBlocks.WATCHTOWER.onUse(world.getBlockState(pos), world, pos, creative, hit(pos));
        context.assertFalse(site(world, pos).ready() || blueprint.isReady(), "Creative clicks must also validate");
        context.assertTrue(blueprint.inventory().isEmpty(), "Invalid creative clicks must not synthesize materials");
        world.removeBlock(pos.up(), false);
        ModBlocks.WATCHTOWER.onUse(world.getBlockState(pos), world, pos, creative, hit(pos));
        assertCompleted(context, pos, creative);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void readyTownHallDoesNotCountItselfAgainstLimit(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 224, 0));
        ServerPlayerEntity player = player(world);
        place(ModBlocks.TOWN_HALL, world, pos, player);
        BuildData data = BuildData.getOrCreate(world);
        BuildData.BuildSite site = site(world, pos);
        data.remove(site);
        site = site.withReady(true);
        data.add(site);
        context.assertTrue(BuildingValidator.validate(world, site).isEmpty(), "A ready town hall must exclude itself from its limit");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void previewSyncReflectsEachKingdomsTownHallAndPalaceLimits(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 216, 0));
        UUID owner = UUID.randomUUID();
        var kingdom = KingdomData.getOrCreate(world);
        kingdom.put(owner, new KingdomData.KingdomInfo(pos.getX(), pos.getZ(), 30, 40, 0, false, 0,
                false, 100, false, ""));

        try {
            assertPreviewLimits(context, pos, owner);
        } finally {
            // The temporary center would otherwise protect nearby villagers in other GameTests.
            kingdom.remove(owner);
        }
        context.complete();
    }

    private static void assertPreviewLimits(TestContext context, BlockPos pos, UUID owner) {
        ServerWorld world = context.getWorld();
        var buildings = KingdomBuildingState.get(world);
        context.assertTrue(BuildingValidator.restrictedTypes(world, owner).contains(BuildingType.ROYAL_PALACE),
                "A palace needs a completed town hall even when the population requirement is met");
        buildings.add(owner, BuildingType.TOWN_HALL, pos, Direction.SOUTH,
                StructureRotationUtil.computeBoundingBox(pos, BuildingType.TOWN_HALL, Direction.SOUTH), null);
        context.assertFalse(BuildingValidator.restrictedTypes(world, owner).contains(BuildingType.ROYAL_PALACE),
                "The first palace is available once the owner meets its requirements");

        BlockPos palace = pos.up(25);
        buildings.add(owner, BuildingType.ROYAL_PALACE, palace, Direction.SOUTH,
                StructureRotationUtil.computeBoundingBox(palace, BuildingType.ROYAL_PALACE, Direction.SOUTH), null);
        var payload = new BuildingBoxesSyncPayload(world.getRegistryKey().getValue(), java.util.List.of(),
                BuildingValidator.restrictedTypes(world, owner));
        var buffer = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
        try {
            BuildingBoxesSyncPayload.TYPE.codec().encode(buffer, payload);
            var snapshot = BuildingBoxesSyncPayload.TYPE.codec().decode(buffer);
            for (BuildingType type : new BuildingType[]{BuildingType.TOWN_HALL, BuildingType.ROYAL_PALACE}) {
                var second = new BuildData.BuildSite(pos.up(50), type, 0, false, owner, Direction.SOUTH);
                context.assertTrue(BuildingValidator.validate(world, second).isPresent()
                                && snapshot.restrictedTypes().contains(type),
                        "Both the server and the synchronized preview must reject a second " + type);
            }
        } finally {
            buffer.release();
        }
        context.assertFalse(BuildingValidator.restrictedTypes(world, UUID.randomUUID()).contains(BuildingType.TOWN_HALL),
                "One kingdom's town hall must not block another kingdom's first town hall");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void overLimitBlueprintItemsDoNotPlaceBlocksOrConsumeItems(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos existing = context.getAbsolutePos(new BlockPos(0, 240, 0));
        ServerPlayerEntity survival = player(world);
        ServerPlayerEntity creative = player(world, true, survival.getUuid());
        AtomicReference<ItemStack> syncedHotbarStack = new AtomicReference<>();
        survival.currentScreenHandler.updateSyncHandler(new ScreenHandlerSyncHandler() {
            @Override
            public void updateState(ScreenHandler handler, DefaultedList<ItemStack> stacks, ItemStack cursor, int[] properties) {
                int slot = handler.getSlotIndex(survival.getInventory(), survival.getInventory().selectedSlot).orElseThrow();
                syncedHotbarStack.set(stacks.get(slot));
            }

            @Override
            public void updateSlot(ScreenHandler handler, int slot, ItemStack stack) {
            }

            @Override
            public void updateCursorStack(ScreenHandler handler, ItemStack stack) {
            }

            @Override
            public void updateProperty(ScreenHandler handler, int property, int value) {
            }
        });
        var buildings = KingdomBuildingState.get(world);
        buildings.add(survival.getUuid(), BuildingType.TOWN_HALL, existing, Direction.SOUTH,
                StructureRotationUtil.computeBoundingBox(existing, BuildingType.TOWN_HALL, Direction.SOUTH), null);
        BuildData data = BuildData.getOrCreate(world);
        data.add(new BuildData.BuildSite(existing.up(18), BuildingType.ROYAL_PALACE, 0, true,
                survival.getUuid(), Direction.SOUTH));

        BlockPos pos = existing.up(36);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        for (BlueprintBlock block : new BlueprintBlock[]{ModBlocks.TOWN_HALL, ModBlocks.ROYAL_PALACE}) {
            for (ServerPlayerEntity placer : new ServerPlayerEntity[]{survival, creative}) {
                ItemStack stack = new ItemStack(block, 2);
                placer.setStackInHand(Hand.MAIN_HAND, stack);
                if (placer == survival) {
                    survival.currentScreenHandler.syncState();
                    syncedHotbarStack.set(null);
                }
                var use = new ItemUsageContext(placer, Hand.MAIN_HAND, hit(pos.down()));
                context.assertEquals(stack.useOnBlock(use), ActionResult.FAIL, "An over-limit blueprint must fail to place");
                context.assertTrue(world.isAir(pos), "A refused placement must leave the destination empty");
                context.assertTrue(data.sites().stream().noneMatch(site -> site.pos().equals(pos)),
                        "A refused placement must not register a building site");
                context.assertEquals(stack.getCount(), 2, "A refused placement must not consume the blueprint item");
                if (placer == survival) {
                    ItemStack synced = syncedHotbarStack.get();
                    context.assertTrue(synced != null && ItemStack.areEqual(synced, stack),
                            "A refused placement must resend the unchanged survival hotbar stack");
                }
            }
        }

        ServerPlayerEntity otherOwner = player(world);
        ItemStack firstTownHall = new ItemStack(ModBlocks.TOWN_HALL, 2);
        otherOwner.setStackInHand(Hand.MAIN_HAND, firstTownHall);
        firstTownHall.useOnBlock(new ItemUsageContext(otherOwner, Hand.MAIN_HAND, hit(pos.down())));
        context.assertTrue(world.getBlockState(pos).isOf(ModBlocks.TOWN_HALL), "Another owner must still be able to place their first town hall");
        context.assertEquals(firstTownHall.getCount(), 1, "Successful survival placement must consume one item");
        context.complete();
    }

    private static void assertCompleted(TestContext context, BlockPos pos, ServerPlayerEntity player) {
        ServerWorld world = context.getWorld();
        context.assertTrue(BuildData.getOrCreate(world).sites().stream().noneMatch(site -> site.pos().equals(pos)),
                "Instant completion must remove the construction site");
        context.assertTrue(CompletedBuildingsData.getOrCreate(world).entries().stream()
                        .anyMatch(entry -> entry.pos().equals(pos) && entry.owner().equals(player.getUuid())),
                "Instant completion must register the completed building");
        context.assertTrue(KingdomBuildingState.get(world).hasCompletedBuilding(player.getUuid(), BuildingType.WATCHTOWER),
                "Instant completion must register kingdom building state");
        context.assertFalse(world.getBlockState(pos).getBlock() instanceof BlueprintBlock,
                "Instant completion must replace the blueprint with the structure");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void reloadedTownHallAcceptsFullOakPlankRequirement(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 8, 0));
        ServerPlayerEntity player = player(world);
        BlueprintBlock block = ModBlocks.TOWN_HALL;
        BlueprintBlockEntity original = place(block, world, pos, player);
        var lookup = world.getRegistryManager();
        var restored = (BlueprintBlockEntity) BlockEntity.createFromNbt(pos, block.getDefaultState(),
                original.createNbtWithIdentifyingData(lookup), lookup);
        context.assertTrue(restored != null, "The town hall blueprint must deserialize");
        world.addBlockEntity(restored);

        ItemStack planks = new ItemStack(Items.OAK_PLANKS, 64);
        deposit(block, world, pos, player, planks);
        context.assertTrue(planks.isEmpty(), "A reloaded town hall must accept all 64 oak planks, not the house's 24");
        context.assertEquals(restored.countOf(Items.OAK_PLANKS), 64, "All deposited oak planks must be stored");
        context.assertFalse(restored.isReady(), "Planks alone must not start construction");

        ItemStack extra = new ItemStack(Items.OAK_PLANKS);
        deposit(block, world, pos, player, extra);
        context.assertEquals(extra.getCount(), 1, "Oak planks beyond the recipe must not be consumed");
        ItemStack unrelated = new ItemStack(Items.DIRT);
        deposit(block, world, pos, player, unrelated);
        context.assertEquals(unrelated.getCount(), 1, "Unrelated materials must not be consumed");
        context.assertFalse(restored.insert(ItemStack.EMPTY), "Empty stacks must not be accepted");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyReloadedBlueprintAcceptsItsOwnRecipe(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlueprintBlock[] blocks = {ModBlocks.HOUSE, ModBlocks.BARRACKS, ModBlocks.BLACKSMITH,
                ModBlocks.TOWN_HALL, ModBlocks.WATCHTOWER, ModBlocks.GATE, ModBlocks.WALL,
                ModBlocks.HERO_SANCTUARY, ModBlocks.GRANARY, ModBlocks.INFIRMARY, ModBlocks.MARKET,
                ModBlocks.ARSENAL, ModBlocks.TRAINING_GROUND, ModBlocks.ROYAL_PALACE};
        var lookup = world.getRegistryManager();
        for (BlueprintBlock block : blocks) {
            BlueprintBlockEntity original = (BlueprintBlockEntity) block.createBlockEntity(pos, block.getDefaultState());
            var restored = (BlueprintBlockEntity) BlockEntity.createFromNbt(pos, block.getDefaultState(),
                    original.createNbtWithIdentifyingData(lookup), lookup);
            context.assertTrue(restored != null && restored.buildingType() == block.getType(),
                    "Reload must preserve the recipe for " + block.getType());
            block.getType().materials().forEach((item, count) -> {
                int remaining = count;
                while (remaining > 0) {
                    int size = Math.min(remaining, item.getMaxCount());
                    ItemStack stack = new ItemStack(item, size);
                    context.assertTrue(restored.insert(stack) && stack.isEmpty(),
                            "Reloaded " + block.getType() + " must accept its full " + item + " requirement");
                    remaining -= size;
                }
            });
            context.assertTrue(restored.hasAllMaterials(block.getType(), 1.0),
                    "Reloaded " + block.getType() + " must have room for the entire recipe");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void townHallStartsWhenSurvivalPlayerInsertsLastMaterial(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 8, 0));
        ServerPlayerEntity player = player(world);
        BlueprintBlock block = ModBlocks.TOWN_HALL;
        BlueprintBlockEntity blueprint = place(block, world, pos, player);

        deposit(block, world, pos, player, new ItemStack(Items.COBBLESTONE, 64));
        deposit(block, world, pos, player, new ItemStack(Items.OAK_PLANKS, 64));
        deposit(block, world, pos, player, new ItemStack(Items.GLASS, 32));
        ItemStack secondCobblestone = new ItemStack(Items.COBBLESTONE, 64);
        deposit(block, world, pos, player, secondCobblestone);
        context.assertTrue(secondCobblestone.isEmpty(), "Town hall must accept a second stack of cobblestone");
        context.assertFalse(blueprint.isReady(), "Construction must not start with gold missing");

        ItemStack gold = new ItemStack(Items.GOLD_INGOT, 9);
        deposit(block, world, pos, player, gold);
        context.assertEquals(gold.getCount(), 1, "The player's extra gold must not be taken");
        context.assertTrue(blueprint.isReady() && site(world, pos).ready(),
                "The blueprint and the registered site must both start when the materials are complete");
        context.assertTrue(blueprint.inventory().isEmpty(), "Construction must consume the recipe exactly once");
        context.assertEquals(KingdomBuildingState.get(world).countBuildings(world, player.getUuid(), BuildingType.TOWN_HALL),
                1, "The town hall must count toward the limit after starting");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void overlappingSiteBlocksConstructionUntilRemoved(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 32, 0));
        ServerPlayerEntity player = player(world);
        player.setYaw(0);
        BlueprintBlock block = ModBlocks.BARRACKS;
        BlueprintBlockEntity blueprint = place(block, world, pos, player);
        BlockPos obstacle = pos.east(10);
        place(ModBlocks.HOUSE, world, obstacle, player);

        player.setYaw(90);
        block.getType().materials().forEach((item, count) ->
                deposit(block, world, pos, player, new ItemStack(item, count)));
        context.assertFalse(site(world, pos).ready(), "A different blueprint in the saved footprint must block the start");
        context.assertTrue(blueprint.hasAllMaterials(block.getType(), 1.0), "A blocked start must not consume materials");

        world.removeBlock(obstacle, false);
        var state = world.getBlockState(pos);
        context.assertEquals(state.onUseWithItem(ItemStack.EMPTY, world, player, Hand.MAIN_HAND, hit(pos)),
                ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "Empty-hand clicks must reach the retry handler");
        state.onUse(world, player, hit(pos));
        context.assertTrue(blueprint.isReady() && site(world, pos).ready(),
                "A funded blueprint must retry without another deposit once the overlap is gone");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void reloadPreservesTheRecipeAndMultipleUnstackableItems(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlueprintBlock block = ModBlocks.TRAINING_GROUND;
        BlueprintBlockEntity blueprint = (BlueprintBlockEntity) block.createBlockEntity(pos, block.getDefaultState());
        blueprint.insert(new ItemStack(Items.OAK_LOG, 32));
        blueprint.insert(new ItemStack(Items.TARGET, 4));
        for (int i = 0; i < 3; i++) {
            context.assertTrue(blueprint.insert(new ItemStack(Items.IRON_SWORD)), "Each sword needs its own slot");
        }

        var lookup = world.getRegistryManager();
        var nbt = blueprint.createNbtWithIdentifyingData(lookup);
        var restored = (BlueprintBlockEntity) BlockEntity.createFromNbt(pos, block.getDefaultState(), nbt, lookup);
        context.assertTrue(restored != null && restored.buildingType() == BuildingType.TRAINING_GROUND,
                "Deserialization must use the recipe of the saved block");
        context.assertEquals(restored.countOf(Items.IRON_SWORD), 3, "No sword may be lost when loading");
        context.assertTrue(restored.insert(new ItemStack(Items.IRON_SWORD)), "The restored blueprint must accept more swords");
        context.assertTrue(restored.hasAllMaterials(block.getType(), 1.0), "All deposited materials must survive loading");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "hero_sanctuary")
    public void heroSanctuaryStartsAndCompletesWithItsCore(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 8, 0));
        ServerPlayerEntity player = player(world);
        BlueprintBlock block = ModBlocks.HERO_SANCTUARY;
        BlueprintBlockEntity blueprint = place(block, world, pos, player);
        block.getType().materials().forEach((item, count) ->
                deposit(block, world, pos, player, new ItemStack(item, count)));
        context.assertTrue(blueprint.isReady() && site(world, pos).ready(),
                "The altar must start once its materials are deposited: "
                        + BuildingValidator.validate(world, site(world, pos)).map(t -> t.getString()).orElse("valid"));
        context.assertTrue(BuildingManager.completeNow(world, site(world, pos), player),
                "The altar must complete");
        BlockPos core = pos.add(com.kingdomunique.world.HeroSanctuaryBuilder.CORE_OFFSET);
        context.assertTrue(world.getBlockState(core).isOf(net.minecraft.block.Blocks.LODESTONE),
                "The altar core must be a lodestone, was " + world.getBlockState(core));
        context.assertTrue(com.kingdomunique.kingdom.SanctuaryData.getOrCreate(world).isSanctuary(core),
                "The altar core must be registered");

        BlockPos next = pos.add(20, 0, 0);
        BlueprintBlockEntity second = place(block, world, next, player);
        block.getType().materials().forEach((item, count) ->
                deposit(block, world, next, player, new ItemStack(item, count)));
        context.assertFalse(second.isReady(), "A standing altar must keep the one-per-kingdom limit");

        world.setBlockState(core, net.minecraft.block.Blocks.AIR.getDefaultState());
        world.breakBlock(next, false);
        BlueprintBlockEntity rebuilt = place(block, world, next, player);
        block.getType().materials().forEach((item, count) ->
                deposit(block, world, next, player, new ItemStack(item, count)));
        context.assertTrue(rebuilt.isReady() && site(world, next).ready(),
                "A destroyed altar must not block building a new one");
        context.assertFalse(com.kingdomunique.kingdom.SanctuaryData.getOrCreate(world).isSanctuary(core),
                "A destroyed altar core must be unregistered");
        context.complete();
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return player(world, false);
    }

    private static ServerPlayerEntity player(ServerWorld world, boolean creative) {
        return player(world, creative, UUID.randomUUID());
    }

    private static ServerPlayerEntity player(ServerWorld world, boolean creative, UUID uuid) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(uuid, "blueprint_test"), SyncedClientOptions.createDefault()) {
            @Override
            public boolean isCreative() {
                return creative;
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
            }

            @Override
            public void requestTeleport(double x, double y, double z) {
                setPosition(x, y, z);
            }
        };
    }

    private static BlueprintBlockEntity place(BlueprintBlock block, ServerWorld world, BlockPos pos,
                                              ServerPlayerEntity player) {
        var state = block.getDefaultState();
        world.setBlockState(pos, state);
        block.onPlaced(world, pos, state, player, new ItemStack(block));
        return (BlueprintBlockEntity) world.getBlockEntity(pos);
    }

    private static void deposit(BlueprintBlock block, ServerWorld world, BlockPos pos,
                                ServerPlayerEntity player, ItemStack stack) {
        block.onUseWithItem(stack, block.getDefaultState(), world, pos, player, Hand.MAIN_HAND, hit(pos));
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
    }

    private static BuildData.BuildSite site(ServerWorld world, BlockPos pos) {
        return BuildData.getOrCreate(world).sites().stream().filter(site -> site.pos().equals(pos)).findFirst().orElseThrow();
    }
}
