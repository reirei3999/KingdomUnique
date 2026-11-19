package com.kingdomunique.building;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/** 4種類の設計図ブロックの定義。 */
public enum BuildingType {
    HOUSE("民家", 5, 20 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 32);
            map.put(Items.OAK_PLANKS, 24);
            map.put(Items.GLASS, 8);
            return map;
        }
    },
    BARRACKS("兵舎", 0, 30 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 64);
            map.put(Items.IRON_INGOT, 12);
            map.put(Items.OAK_PLANKS, 16);
            return map;
        }
    },
    BLACKSMITH("鍛冶工房", 0, 25 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 40);
            map.put(Items.IRON_INGOT, 16);
            map.put(Items.COAL, 8);
            return map;
        }
    },
    TOWN_HALL("王城・役場", 10, 40 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 128);
            map.put(Items.OAK_PLANKS, 64);
            map.put(Items.GLASS, 32);
            map.put(Items.GOLD_INGOT, 8);
            return map;
        }
    };

    private final String displayName;
    private final int populationCapBonus;
    private final int baseBuildTicks;

    BuildingType(String displayName, int populationCapBonus, int baseBuildTicks) {
        this.displayName = displayName;
        this.populationCapBonus = populationCapBonus;
        this.baseBuildTicks = baseBuildTicks;
    }

    public abstract Map<Item, Integer> materials();

    public String getDisplayName() {
        return displayName;
    }

    public int getPopulationCapBonus() {
        return populationCapBonus;
    }

    public int getBaseBuildTicks() {
        return baseBuildTicks;
    }

    /** 整地・建築が行われる範囲の半径（プレビュー表示用）。 */
    public int getBuildRadius() {
        return switch (this) {
            case HOUSE -> 9;
            case BARRACKS -> 12;
            case BLACKSMITH -> 10;
            case TOWN_HALL -> 12;
        };
    }
}
