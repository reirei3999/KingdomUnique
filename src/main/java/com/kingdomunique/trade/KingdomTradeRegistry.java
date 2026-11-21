package com.kingdomunique.trade;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import com.kingdomunique.entity.CitizenVillager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.Optional;
import java.util.UUID;

/**
 * インフレを排除したシビアな国民交易。
 * 割引なし・兵士は交易対象外・建材は割高・鍛冶屋は素材のみ販売。
 */
public final class KingdomTradeRegistry {

    private KingdomTradeRegistry() {
    }

    /** 国民の交易リストを構築する（SOLDIER は装備UI側で扱うためここでは扱わない）。 */
    public static TradeOfferList buildOffersForCitizen(ServerPlayerEntity player, VillagerEntity villager,
                                                       String citizenRole) {
        ServerWorld world = player.getServerWorld();
        UUID ownerUuid = resolveOwner(villager, player);
        KingdomBuildingState buildings = KingdomBuildingState.get(world);

        boolean hasMarket = buildings.hasCompletedBuilding(ownerUuid, BuildingType.MARKET);
        boolean hasGranary = buildings.hasCompletedBuilding(ownerUuid, BuildingType.GRANARY);
        boolean hasBlacksmith = buildings.hasCompletedBuilding(ownerUuid, BuildingType.BLACKSMITH);
        boolean hasTownHall = buildings.hasCompletedBuilding(ownerUuid, BuildingType.TOWN_HALL);
        boolean hasRoyalPalace = buildings.hasCompletedBuilding(ownerUuid, BuildingType.ROYAL_PALACE);

        TradeOfferList offers = new TradeOfferList();
        switch (citizenRole) {
            case CitizenVillager.ROLE_FARMER -> addFarmerTrades(offers, hasGranary);
            case CitizenVillager.ROLE_BUILDER -> addBuilderTrades(offers, hasTownHall, hasRoyalPalace);
            case CitizenVillager.ROLE_SMITH -> addSmithTrades(offers, hasBlacksmith, hasRoyalPalace);
            default -> addBasicCitizenTrades(offers);
        }

        // 交易場（MARKET）完成時は通貨交換のみ（割引なし・取引回数制限あり）。
        if (hasMarket) {
            offers.add(createOffer(Items.DIAMOND, 2, Items.EMERALD, 3, 6, 4));
            offers.add(createOffer(Items.GOLD_INGOT, 8, Items.EMERALD, 1, 8, 3));
        }
        return offers;
    }

    static UUID resolveOwner(VillagerEntity villager, ServerPlayerEntity player) {
        if (villager instanceof CitizenVillager citizen) {
            String owner = citizen.ku$getOwnerUuid();
            if (owner != null && !owner.isEmpty()) {
                try {
                    return UUID.fromString(owner);
                } catch (IllegalArgumentException ignored) {
                    // 壊れたUUIDは話しかけたプレイヤーを所有者とみなす。
                }
            }
        }
        return player.getUuid();
    }

    /** 農民：大量の作物でわずかなエメラルド、食料購入はやや高め。 */
    private static void addFarmerTrades(TradeOfferList offers, boolean hasGranary) {
        offers.add(createOffer(Items.WHEAT, 32, Items.EMERALD, 1, 8, 2));
        offers.add(createOffer(Items.CARROT, 32, Items.EMERALD, 1, 8, 2));
        offers.add(createOffer(Items.POTATO, 32, Items.EMERALD, 1, 8, 2));
        offers.add(createOffer(Items.EMERALD, 3, Items.BREAD, 4, 8, 2));
        if (hasGranary) {
            offers.add(createOffer(Items.HAY_BLOCK, 12, Items.EMERALD, 1, 6, 3));
            offers.add(createOffer(Items.EMERALD, 8, Items.GOLDEN_CARROT, 4, 6, 4));
            offers.add(createOffer(Items.EMERALD, 24, Items.GOLDEN_APPLE, 1, 2, 6));
        }
    }

    /** 建築士：かなり割高な緊急調達用建材と設計図。 */
    private static void addBuilderTrades(TradeOfferList offers, boolean hasTownHall, boolean hasRoyalPalace) {
        offers.add(createOffer(Items.EMERALD, 2, Items.COBBLESTONE, 16, 12, 2));
        offers.add(createOffer(Items.EMERALD, 3, Items.OAK_LOG, 8, 12, 2));
        offers.add(createOffer(Items.EMERALD, 4, Items.STONE_BRICKS, 16, 10, 2));
        offers.add(createOffer(Items.EMERALD, 4, Items.CALCITE, 12, 8, 2));
        offers.add(createOffer(Items.EMERALD, 5, Items.GLASS_PANE, 8, 8, 2));
        offers.add(createOffer(Items.EMERALD, 6, Items.LANTERN, 2, 6, 3));

        offers.add(createStackOffer(Items.EMERALD, 12,
                TradeRewardFactory.createBlueprintStack(BuildingType.HOUSE), 4, 4));
        offers.add(createStackOffer(Items.EMERALD, 16,
                TradeRewardFactory.createBlueprintStack(BuildingType.GRANARY), 3, 5));

        if (hasTownHall) {
            offers.add(createOffer(Items.EMERALD, 8, Items.QUARTZ_BLOCK, 4, 8, 4));
            offers.add(createOffer(Items.EMERALD, 6, Items.DEEPSLATE_TILES, 12, 8, 4));
            offers.add(createStackOffer(Items.EMERALD, 20,
                    TradeRewardFactory.createBlueprintStack(BuildingType.BLACKSMITH), 2, 6));
            offers.add(createStackOffer(Items.EMERALD, 24,
                    TradeRewardFactory.createBlueprintStack(BuildingType.ARSENAL), 2, 6));
            offers.add(createStackOffer(Items.EMERALD, 20,
                    TradeRewardFactory.createBlueprintStack(BuildingType.INFIRMARY), 2, 6));
            offers.add(createStackOffer(Items.EMERALD, 24,
                    TradeRewardFactory.createBlueprintStack(BuildingType.MARKET), 2, 6));
        }
        if (hasRoyalPalace) {
            offers.add(createStackOffer(Items.EMERALD, 36,
                    TradeRewardFactory.createBlueprintStack(BuildingType.TRAINING_GROUND), 1, 10));
            offers.add(createStackOffer(Items.EMERALD, 48,
                    TradeRewardFactory.createBlueprintStack(BuildingType.HERO_SANCTUARY), 1, 15));
        }
    }

    /** 鍛冶屋：武器・防具の「素材」のみ販売（完成品・強化品は売らない）。 */
    private static void addSmithTrades(TradeOfferList offers, boolean hasBlacksmith, boolean hasRoyalPalace) {
        offers.add(createOffer(Items.EMERALD, 2, Items.COAL, 8, 12, 2));
        offers.add(createOffer(Items.EMERALD, 4, Items.IRON_INGOT, 3, 10, 3));
        offers.add(createOffer(Items.EMERALD, 3, Items.LEATHER, 4, 10, 2));
        offers.add(createOffer(Items.EMERALD, 2, Items.FLINT, 6, 10, 2));
        if (hasBlacksmith) {
            offers.add(createOffer(Items.EMERALD, 14, Items.DIAMOND, 1, 4, 6));
            offers.add(createOffer(Items.EMERALD, 6, Items.OBSIDIAN, 2, 6, 4));
            offers.add(createOffer(Items.EMERALD, 8, Items.BLAZE_ROD, 1, 4, 5));
        }
        if (hasRoyalPalace) {
            offers.add(createOffer(Items.EMERALD, 32, Items.NETHERITE_SCRAP, 1, 2, 12));
        }
    }

    private static void addBasicCitizenTrades(TradeOfferList offers) {
        offers.add(createOffer(Items.EMERALD, 2, Items.TORCH, 8, 8, 1));
    }

    private static TradeOffer createOffer(Item buyItem, int buyCount, Item sellItem, int sellCount,
                                           int maxUses, int xp) {
        return new TradeOffer(new TradedItem(buyItem, buyCount), Optional.empty(),
                new ItemStack(sellItem, sellCount), maxUses, xp, 0.05f);
    }

    private static TradeOffer createStackOffer(Item buyItem, int buyCount, ItemStack sellStack,
                                                int maxUses, int xp) {
        return new TradeOffer(new TradedItem(buyItem, buyCount), Optional.empty(),
                sellStack, maxUses, xp, 0.05f);
    }
}
