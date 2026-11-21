package com.kingdomunique.trade;

import com.kingdomunique.entity.CitizenVillager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** 国民化済み村人を通常右クリックした際に、役職・施設連動の独自交易画面を開く。 */
public final class CitizenTradeHandler {

    private CitizenTradeHandler() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND
                    || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(entity instanceof VillagerEntity villager)
                    || !(villager instanceof CitizenVillager citizen)) {
                return ActionResult.PASS;
            }
            // 未国民はバニラ交易のまま。スニーク時は国民化処理を優先する。
            if (citizen.ku$getOwnerUuid().isEmpty() || player.isSneaking()) {
                return ActionResult.PASS;
            }
            String role = citizen.ku$getRole();
            // 兵士は商取引を行わない。専用の装備UI（SoldierInteractionHandler）へ委譲する。
            if (CitizenVillager.ROLE_SOLDIER.equals(role)) {
                return ActionResult.PASS;
            }
            openKingdomMerchantScreen(serverPlayer, villager, role, formatRoleTitleJa(role));
            return ActionResult.SUCCESS;
        });
    }

    private static String formatRoleTitleJa(String role) {
        return switch (role) {
            case CitizenVillager.ROLE_FARMER -> "国民交易【農民】";
            case CitizenVillager.ROLE_BUILDER -> "国民交易【建築士】";
            case CitizenVillager.ROLE_SMITH -> "国民交易【鍛冶屋】";
            case CitizenVillager.ROLE_SOLDIER -> "国民交易【兵士】";
            default -> "国民交易【一般国民】";
        };
    }

    private static void openKingdomMerchantScreen(ServerPlayerEntity player, VillagerEntity villager,
                                                  String role, String title) {
        var offers = KingdomTradeRegistry.buildOffersForCitizen(player, villager, role);

        net.minecraft.village.SimpleMerchant merchant =
                new net.minecraft.village.SimpleMerchant(player) {
                    @Override
                    public net.minecraft.village.TradeOfferList getOffers() {
                        return offers;
                    }
                };

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new MerchantScreenHandler(syncId, playerInventory, merchant),
                Text.literal("§6" + title)));
    }
}
