package com.kingdomunique.trade;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.item.unique.UniqueArmors;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.GameMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CitizenMerchantGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void recruitmentNeverFallsThroughToVanillaTrading(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.refreshPositionAndAngles(context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1)), 0, 0);
            KingdomManager.setKingdomCenter(player, player.getBlockPos(), true);
            for (var item : List.of(Items.WHEAT, Items.IRON_INGOT)) {
                var villager = context.spawnMob(EntityType.VILLAGER, 1, 1, 1);
                try {
                    player.getInventory().clear();
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item));
                    player.getInventory().setStack(1, new ItemStack(Items.EMERALD));
                    player.setSneaking(true);
                    var first = UseEntityCallback.EVENT.invoker().interact(player, context.getWorld(),
                            Hand.MAIN_HAND, villager, new EntityHitResult(villager));
                    context.assertTrue(first.isAccepted(), "Recruitment must consume interactAt");
                    context.assertEquals(((CitizenVillager) villager).ku$getOwnerUuid(), player.getUuidAsString(),
                            "Villager must be recruited");
                    context.assertEquals(((CitizenVillager) villager).ku$getRole(),
                            item == Items.WHEAT ? CitizenVillager.ROLE_FARMER : CitizenVillager.ROLE_SMITH,
                            "Held item must select the role");
                    assertNoVanillaMerchant(context, player, villager);

                    var second = UseEntityCallback.EVENT.invoker().interact(player, context.getWorld(),
                            Hand.MAIN_HAND, villager, null);
                    if (second == ActionResult.PASS) {
                        second = villager.interact(player, Hand.MAIN_HAND);
                    }
                    context.assertTrue(second.isAccepted(), "Follow-up interact must be consumed");
                    assertNoVanillaMerchant(context, player, villager);
                    for (var hand : Hand.values()) {
                        context.assertTrue(villager.interact(player, hand).isAccepted(),
                                "Citizen interaction must be consumed for either hand");
                        assertNoVanillaMerchant(context, player, villager);
                    }

                    player.setSneaking(false);
                    UseEntityCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND, villager, null);
                    context.assertTrue(player.currentScreenHandler instanceof CitizenMerchantScreenHandler,
                            "Subsequent normal interaction must open kingdom trades");
                    player.closeHandledScreen();
                } finally {
                    villager.discard();
                }
            }
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
            player.discard();
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unaffiliatedVillagersKeepVanillaTradingWhenRecruitmentFails(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var villager = context.spawnMob(EntityType.VILLAGER, 1, 1, 1);
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            KingdomManager.setKingdomCenter(player, player.getBlockPos(), true);
            player.getInventory().clear();
            player.setSneaking(true);
            UseEntityCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    villager, new EntityHitResult(villager));
            context.assertTrue(((CitizenVillager) villager).ku$getOwnerUuid().isEmpty(),
                    "Missing payment must not recruit a villager");
            player.setSneaking(false);
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FARMER));
            villager.interact(player, Hand.MAIN_HAND);
            context.assertTrue(player.currentScreenHandler instanceof MerchantScreenHandler,
                    "Unaffiliated villagers must still open vanilla trades");
            context.assertTrue(villager.getCustomer() == player, "Vanilla control must establish a customer");
        } finally {
            player.closeHandledScreen();
            villager.discard();
            context.getWorld().getServer().getPlayerManager().remove(player);
            player.discard();
        }
        context.complete();
    }

    private static void assertNoVanillaMerchant(TestContext context, ServerPlayerEntity player, VillagerEntity villager) {
        context.assertFalse(player.currentScreenHandler instanceof MerchantScreenHandler,
                "Recruitment must never open the vanilla merchant screen");
        context.assertTrue(villager.getCustomer() == null, "Citizens must never have a vanilla customer");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void displayUnlocksMatchEveryTradeAndBuildingCombination(TestContext context) {
        List<BuildingType> buildings = List.of(BuildingType.MARKET, BuildingType.GRANARY,
                BuildingType.BLACKSMITH, BuildingType.TOWN_HALL, BuildingType.ROYAL_PALACE);
        for (String role : List.of("farmer", "builder", "smith", "citizen", "unknown")) {
            var displays = TradeDisplayOffers.build(role);
            for (int mask = 0; mask < (1 << buildings.size()); mask++) {
                var completed = EnumSet.noneOf(BuildingType.class);
                for (int bit = 0; bit < buildings.size(); bit++) {
                    if ((mask & (1 << bit)) != 0) {
                        completed.add(buildings.get(bit));
                    }
                }
                var actual = KingdomTradeRegistry.buildOffers(role, completed::contains);
                var visible = displays.stream().filter(e -> e.matches(completed)).toList();
                context.assertEquals(visible.size(), actual.size(), "Every trade must have exactly one matching display");
                for (var offer : actual) {
                    context.assertTrue(visible.stream().anyMatch(e -> TradeDisplayOffers.sameOffer(e.offer(), offer)),
                            "Missing trade or inaccurate unlock condition for " + role + " with " + completed);
                }
            }
        }
        var blacksmith = TradeDisplayOffers.build("builder").stream().filter(e -> e.offer().getSellItem().isOf(
                TradeRewardFactory.createBlueprintStack(BuildingType.BLACKSMITH).getItem())).findFirst().orElseThrow();
        context.assertEquals(blacksmith.alternatives(), List.of(java.util.Set.of(BuildingType.TOWN_HALL)),
                "The blacksmith blueprint requires a completed town hall, not a royal palace or a blueprint item");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void staticTradeOffersRespectBuildingRequirements(TestContext context) {
        var base = KingdomTradeRegistry.buildOffers("builder", type -> false);
        var unlocked = KingdomTradeRegistry.buildOffers("builder",
                type -> type == com.kingdomunique.building.BuildingType.TOWN_HALL);
        context.assertEquals(base.size(), 8, "Builder base offers must not require a world");
        context.assertEquals(unlocked.size(), 14, "Town hall should unlock six builder offers");
        var smith = KingdomTradeRegistry.buildOffers("smith", type -> true);
        context.assertEquals(smith.size(), 10, "Smith should include materials and market exchanges");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void unknownCitizenRoleUsesBasicOffers(TestContext context) {
        var offers = KingdomTradeRegistry.buildOffers("unknown", type -> false);
        context.assertEquals(offers.size(), 1, "Unknown roles should receive basic offers");
        context.assertTrue(offers.getFirst().getSellItem().isOf(Items.TORCH), "Basic offer should sell torches");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void onlyUniqueArmorModItemsSupportAnvilEnhancement(TestContext context) {
        Set<Item> enhanceableArmor = Set.of(UniqueArmors.HADES_HELMET, UniqueArmors.MIMIR_HELMET,
                UniqueArmors.DRAGONSCALE_WINGPLATE, UniqueArmors.NEMEAN_LION_HIDE, UniqueArmors.JOTUN_LEGGINGS,
                UniqueArmors.HERMES_TALARIA, UniqueArmors.SALAMANDER_BOOTS);
        Registries.ITEM.getIds().stream()
                .filter(id -> id.getNamespace().equals("kingdomunique"))
                .forEach(id -> {
                    Item item = Registries.ITEM.get(id);
                    context.assertEquals(EnhancementUtil.isEnhanceable(new ItemStack(item)),
                            enhanceableArmor.contains(item), "Unexpected enhancement eligibility: " + id);
                });
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void selectingAnOfferFillsBothInputsAndTakingResultConsumesStock(TestContext context) {
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().setStack(0, new ItemStack(Items.WHEAT, 4));
        player.getInventory().setStack(1, new ItemStack(Items.CARROT, 2));
        var offer = new TradeOffer(new TradedItem(Items.WHEAT, 2), Optional.of(new TradedItem(Items.CARROT)),
                new ItemStack(Items.EMERALD), 1, 1, 0);
        var offers = new TradeOfferList();
        offers.add(offer);
        var info = new CitizenMerchantScreenHandler.CitizenInfo("Test Kingdom", "farmer", "farmer", 1, 0, 0);
        var handler = new CitizenMerchantScreenHandler(1, player.getInventory(), info, offers);

        context.assertEquals(handler.getType(), CitizenMerchantScreenHandler.TYPE, "Wrong screen handler type");
        handler.setRecipeIndex(0);
        handler.switchTo(0);
        context.assertTrue(handler.getSlot(0).getStack().isOf(Items.WHEAT), "First payment should autofill");
        context.assertTrue(handler.getSlot(1).getStack().isOf(Items.CARROT), "Second payment should autofill");
        context.assertTrue(handler.getSlot(2).getStack().isOf(Items.EMERALD), "The result should be available");
        context.assertTrue(handler.quickMove(player, 2).isOf(Items.EMERALD), "Shift-click should take the result");
        context.assertEquals(handler.getSlot(0).getStack().getCount(), 2, "First payment was not consumed");
        context.assertEquals(handler.getSlot(1).getStack().getCount(), 1, "Second payment was not consumed");
        context.assertTrue(offer.isDisabled(), "This offer should be out of stock");
        context.assertTrue(handler.getSlot(2).getStack().isEmpty(), "A sold-out offer must not produce a result");
        context.complete();
    }
}
