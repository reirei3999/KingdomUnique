package com.kingdomunique.trade;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.kingdom.KingdomManager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.village.VillagerProfession;

import java.util.UUID;

/** 国民化済み村人を通常右クリックした際に、役職・施設連動の独自交易画面を開く。 */
public final class CitizenTradeHandler {

    private CitizenTradeHandler() {
    }

    public static void register() {
        CitizenMerchantScreenHandler.initialize();
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
            openKingdomMerchantScreen(serverPlayer, villager, role);
            return ActionResult.SUCCESS;
        });
    }

    private static void openKingdomMerchantScreen(ServerPlayerEntity player, VillagerEntity villager, String role) {
        var offers = KingdomTradeRegistry.buildOffersForCitizen(player, villager, role);
        var villagerData = villager.getVillagerData();
        var profession = villagerData.getProfession();
        int level = profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                ? 0 : villagerData.getLevel();
        UUID owner = KingdomTradeRegistry.resolveOwner(villager, player);
        var citizenInfo = new CitizenMerchantScreenHandler.CitizenInfo(
                KingdomManager.getKingdomName(player.getServerWorld(), owner), role, profession.id(),
                level, villager.getExperience(), villager.getReputation(player));

        player.openHandledScreen(new ExtendedScreenHandlerFactory<CitizenMerchantScreenHandler.CitizenInfo>() {
            @Override
            public CitizenMerchantScreenHandler.CitizenInfo getScreenOpeningData(ServerPlayerEntity player) {
                return citizenInfo;
            }

            @Override
            public Text getDisplayName() {
                return Text.literal("王国民交易");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
                return new CitizenMerchantScreenHandler(syncId, inventory, citizenInfo, offers);
            }
        }).ifPresent(syncId -> {
            if (player.currentScreenHandler instanceof CitizenMerchantScreenHandler handler) {
                handler.sendOffers(player);
            }
        });
    }
}
