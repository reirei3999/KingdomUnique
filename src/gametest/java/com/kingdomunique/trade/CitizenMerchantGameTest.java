package com.kingdomunique.trade;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.GameMode;

import java.util.Optional;

public final class CitizenMerchantGameTest {
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
