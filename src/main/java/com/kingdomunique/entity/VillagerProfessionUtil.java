package com.kingdomunique.entity;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.TradedItem;
import java.util.Optional;

/** 国民の役職に対応するバニラ職業と取引内容を設定する。 */
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
                .withProfession(profession).withLevel(1));

        TradeOfferList offers = villager.getOffers();
        if (CitizenVillager.ROLE_BUILDER.equals(role)) {
            // 建築士（道具鍛冶）は取引不可。
            offers.clear();
        } else if (CitizenVillager.ROLE_FARMER.equals(role)) {
            // 農民: オークの苗木をエメラルドと交換する取引を追加。
            boolean hasOak = offers.stream().anyMatch(o ->
                    o.getOriginalFirstBuyItem().isOf(Items.OAK_SAPLING));
            if (!hasOak) {
                offers.add(new TradeOffer(
                        new TradedItem(Items.OAK_SAPLING, 8), Optional.empty(),
                        new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
            }
        } else if (CitizenVillager.ROLE_SMITH.equals(role)) {
            // 鍛冶士（武器鍛造）は鉱石・地金系の取引だけ残す。
            offers.removeIf(offer -> !isOreTrade(offer));
        }
        villager.setOffers(offers);
    }

    /** 取引に鉱石／地金が含まれるか。 */
    private static boolean isOreTrade(TradeOffer offer) {
        Item buy = offer.getOriginalFirstBuyItem().getItem();
        Item sell = offer.getSellItem().getItem();
        return isOreItem(buy) || isOreItem(sell);
    }

    private static boolean isOreItem(Item item) {
        return item == Items.COAL || item == Items.CHARCOAL
                || item == Items.RAW_IRON || item == Items.IRON_INGOT
                || item == Items.RAW_COPPER || item == Items.COPPER_INGOT
                || item == Items.RAW_GOLD || item == Items.GOLD_INGOT
                || item == Items.DIAMOND || item == Items.EMERALD
                || item == Items.NETHERITE_INGOT || item == Items.ANCIENT_DEBRIS
                || item == Items.LAPIS_LAZULI || item == Items.REDSTONE
                || item == Items.QUARTZ;
    }
}
