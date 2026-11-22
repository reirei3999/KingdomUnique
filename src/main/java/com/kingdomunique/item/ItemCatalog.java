package com.kingdomunique.item;

import com.kingdomunique.building.ModBlocks;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** 専用アイテム取得メニューに並べるアイテムのカタログ。 */
public final class ItemCatalog {

    public record Entry(String id, String label, java.util.function.Supplier<ItemStack> factory) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES.add(new Entry("hero_summon_horn", "英雄召喚の角笛", () -> new ItemStack(ModItems.HERO_SUMMON_HORN)));
        ENTRIES.add(new Entry("possession_orb", "憑依のオーブ", () -> new ItemStack(ModItems.POSSESSION_ORB)));
        ENTRIES.add(new Entry("blueprint_house", "設計図：民家", () -> new ItemStack(ModBlocks.HOUSE)));
        ENTRIES.add(new Entry("blueprint_barracks", "設計図：兵舎", () -> new ItemStack(ModBlocks.BARRACKS)));
        ENTRIES.add(new Entry("blueprint_blacksmith", "設計図：鍛冶工房", () -> new ItemStack(ModBlocks.BLACKSMITH)));
        ENTRIES.add(new Entry("blueprint_town_hall", "設計図：王城", () -> new ItemStack(ModBlocks.TOWN_HALL)));
        ENTRIES.add(new Entry("blueprint_watchtower", "設計図：国境監視塔",
                () -> new ItemStack(ModBlocks.WATCHTOWER)));
        ENTRIES.add(new Entry("blueprint_gate", "設計図：国の門", () -> new ItemStack(ModBlocks.GATE)));
        ENTRIES.add(new Entry("blueprint_wall", "設計図：防壁", () -> new ItemStack(ModBlocks.WALL)));
        ENTRIES.add(new Entry("blueprint_hero_sanctuary", "設計図：英雄召喚の祭壇",
                () -> new ItemStack(ModBlocks.HERO_SANCTUARY)));
        ENTRIES.add(new Entry("blueprint_granary", "設計図：王立穀物庫", () -> new ItemStack(ModBlocks.GRANARY)));
        ENTRIES.add(new Entry("blueprint_infirmary", "設計図：王立救護院", () -> new ItemStack(ModBlocks.INFIRMARY)));
        ENTRIES.add(new Entry("blueprint_market", "設計図：王都交易場", () -> new ItemStack(ModBlocks.MARKET)));
        ENTRIES.add(new Entry("blueprint_arsenal", "設計図：王立軍事庫", () -> new ItemStack(ModBlocks.ARSENAL)));
        ENTRIES.add(new Entry("blueprint_training_ground", "設計図：英雄訓練場", () -> new ItemStack(ModBlocks.TRAINING_GROUND)));
        ENTRIES.add(new Entry("blueprint_royal_palace", "設計図：王宮", () -> new ItemStack(ModBlocks.ROYAL_PALACE)));
        ENTRIES.add(new Entry("slaughter_blade", "殺戮の宝剣（遺物）", () -> new ItemStack(ModItems.SLAUGHTER_BLADE)));
        ENTRIES.add(new Entry("destruction_pickaxe", "破壊衝動のツルハシ（呪物）",
                () -> new ItemStack(ModItems.DESTRUCTION_PICKAXE)));
        ENTRIES.add(new Entry("trishula", "トリシューラ", () -> new ItemStack(UniqueWeapons.TRISHULA)));
        ENTRIES.add(new Entry("excalibur", "エクスカリバー", () -> new ItemStack(UniqueWeapons.EXCALIBUR)));
        ENTRIES.add(new Entry("gae_bolg", "ゲイボルグ", () -> new ItemStack(UniqueWeapons.GAE_BOLG)));
        ENTRIES.add(new Entry("muramasa", "ムラマサ", () -> new ItemStack(UniqueWeapons.MURAMASA)));
        ENTRIES.add(new Entry("apollo_bow", "アポロンの太陽弓", () -> new ItemStack(UniqueWeapons.APOLLO_BOW)));
        ENTRIES.add(new Entry("mjolnir", "ミョルニル", () -> new ItemStack(UniqueWeapons.MJOLNIR)));
        ENTRIES.add(new Entry("chronos_dagger", "クロノス・ダガー", () -> new ItemStack(UniqueWeapons.CHRONOS_DAGGER)));
    }

    private ItemCatalog() {
    }

    public static List<Entry> entries() {
        return java.util.Collections.unmodifiableList(ENTRIES);
    }

    public static ItemStack create(String id) {
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                return entry.factory().get();
            }
        }
        return ItemStack.EMPTY;
    }
}
