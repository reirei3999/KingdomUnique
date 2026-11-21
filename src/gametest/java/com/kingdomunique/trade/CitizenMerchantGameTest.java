package com.kingdomunique.trade;

import com.kingdomunique.building.BuildingType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.GameMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class CitizenMerchantGameTest {
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
    public void registeredModItemsHaveNoAnvilEnhancementExamples(TestContext context) {
        net.minecraft.registry.Registries.ITEM.getIds().stream()
                .filter(id -> id.getNamespace().equals("kingdomunique"))
                .forEach(id -> context.assertFalse(com.kingdomunique.util.EnhancementUtil.isEnhanceable(
                        new ItemStack(net.minecraft.registry.Registries.ITEM.get(id))),
                        "Mod item unexpectedly supports enhancement: " + id));
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
