package com.kingdomunique.trade;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** 国民交易で販売する設計図アイテムを生成する。 */
public final class TradeRewardFactory {

    private TradeRewardFactory() {
    }

    /** 建築種別に対応する設計図アイテムを返す。 */
    public static ItemStack createBlueprintStack(BuildingType type) {
        Block block = switch (type) {
            case HOUSE -> ModBlocks.HOUSE;
            case BARRACKS -> ModBlocks.BARRACKS;
            case BLACKSMITH -> ModBlocks.BLACKSMITH;
            case TOWN_HALL -> ModBlocks.TOWN_HALL;
            case WATCHTOWER -> ModBlocks.WATCHTOWER;
            case GATE -> ModBlocks.GATE;
            case WALL -> ModBlocks.WALL;
            case HERO_SANCTUARY -> ModBlocks.HERO_SANCTUARY;
            case GRANARY -> ModBlocks.GRANARY;
            case INFIRMARY -> ModBlocks.INFIRMARY;
            case MARKET -> ModBlocks.MARKET;
            case ARSENAL -> ModBlocks.ARSENAL;
            case TRAINING_GROUND -> ModBlocks.TRAINING_GROUND;
            case ROYAL_PALACE -> ModBlocks.ROYAL_PALACE;
        };
        return new ItemStack(block);
    }
}
