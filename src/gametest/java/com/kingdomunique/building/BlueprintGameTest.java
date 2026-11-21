package com.kingdomunique.building;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

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
        ServerPlayerEntity creative = player(world, true);
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

    private static ServerPlayerEntity player(ServerWorld world) {
        return player(world, false);
    }

    private static ServerPlayerEntity player(ServerWorld world, boolean creative) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "blueprint_test"), SyncedClientOptions.createDefault()) {
            @Override
            public boolean isCreative() {
                return creative;
            }

            @Override
            public void sendMessage(Text message, boolean overlay) {
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
