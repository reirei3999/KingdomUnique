package com.kingdomunique.entity;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.Optional;

/** 国民の役職に対応するバニラ職業と、完全カスタムの取引内容を設定する。 */
public final class VillagerProfessionUtil {

    private VillagerProfessionUtil() {
    }

    /** 役職に対応するバニラ職業。 */
    public static VillagerProfession professionFor(String role) {
        if (CitizenVillager.ROLE_FARMER.equals(role)) {
            return VillagerProfession.FARMER;
        }
        if (CitizenVillager.ROLE_BUILDER.equals(role)) {
            return VillagerProfession.TOOLSMITH;
        }
        if (CitizenVillager.ROLE_SMITH.equals(role)) {
            return VillagerProfession.WEAPONSMITH;
        }
        return VillagerProfession.NONE;
    }

    /** 役職に応じて職業・取引を適用する。 */
    public static void applyRole(VillagerEntity villager, String role) {
        VillagerProfession profession = professionFor(role);
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(profession));

        TradeOfferList offers = villager.getOffers();
        // バニラの取引は一切残さず、王国仕様の取引だけを登録する。
        offers.clear();

        if (CitizenVillager.ROLE_FARMER.equals(role)) {
            // 農民: 原木→エメラルド / エメラルド→苗木 / エメラルド→小麦の種
            offers.add(new TradeOffer(new TradedItem(Items.OAK_LOG, 4), Optional.empty(),
                    new ItemStack(Items.EMERALD, 1), 24, 3, 0.02F));
            offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), Optional.empty(),
                    new ItemStack(Items.OAK_SAPLING, 4), 24, 3, 0.02F));
            offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 3), Optional.empty(),
                    new ItemStack(Items.WHEAT_SEEDS, 10), 24, 5, 0.02F));
        } else if (CitizenVillager.ROLE_SMITH.equals(role)) {
            // 鍛冶士: レベルが上がるほど豊富な鉱石をエメラルドで買える。
            int level = villager.getVillagerData().getLevel();
            addOreTrade(offers, Items.COAL_ORE, 1, 4, 1);
            if (level >= 2) {
                addOreTrade(offers, Items.COPPER_ORE, 2, 4, 2);
            }
            if (level >= 3) {
                addOreTrade(offers, Items.IRON_ORE, 3, 3, 3);
            }
            if (level >= 4) {
                addOreTrade(offers, Items.GOLD_ORE, 4, 2, 5);
            }
            if (level >= 5) {
                addOreTrade(offers, Items.DIAMOND_ORE, 6, 1, 10);
            }
        }
        // 建築士（道具鍛冶）は取引なしのまま。
        villager.setOffers(offers);
    }

    private static void addOreTrade(TradeOfferList offers, Item ore,
                                    int emeralds, int amount, int xp) {
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(ore, amount), 24, xp, 0.02F));
    }
}
