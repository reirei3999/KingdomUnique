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
    },
    WATCHTOWER("国境監視塔", 0, 18 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 48);
            map.put(Items.OAK_PLANKS, 16);
            map.put(Items.IRON_INGOT, 8);
            map.put(Items.LANTERN, 4);
            return map;
        }
    },
    GATE("国の門", 0, 25 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 40);
            map.put(Items.IRON_INGOT, 24);
            return map;
        }
    },
    WALL("防壁", 0, 12 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.COBBLESTONE, 64);
            map.put(Items.STONE_BRICKS, 32);
            return map;
        }
    },
    HERO_SANCTUARY("英雄召喚の祭壇", 0, 20 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.QUARTZ_BLOCK, 32);
            map.put(Items.GOLD_INGOT, 8);
            map.put(Items.DIAMOND, 2);
            return map;
        }
    },
    GRANARY("王立穀物庫", 0, 13 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.OAK_LOG, 24);
            map.put(Items.HAY_BLOCK, 12);
            map.put(Items.COBBLESTONE, 24);
            return map;
        }
    },
    INFIRMARY("王立救護院", 0, 14 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.WHITE_WOOL, 16);
            map.put(Items.CALCITE, 24);
            map.put(Items.GOLDEN_APPLE, 2);
            return map;
        }
    },
    MARKET("王都交易場", 0, 16 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.SPRUCE_PLANKS, 32);
            map.put(Items.RED_WOOL, 8);
            map.put(Items.EMERALD, 4);
            return map;
        }
    },
    ARSENAL("王立軍事庫", 0, 18 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.STONE_BRICKS, 48);
            map.put(Items.IRON_BLOCK, 2);
            map.put(Items.SHIELD, 2);
            return map;
        }
    },
    TRAINING_GROUND("英雄訓練場", 0, 21 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.OAK_LOG, 32);
            map.put(Items.TARGET, 4);
            map.put(Items.IRON_SWORD, 4);
            return map;
        }
    },
    ROYAL_PALACE("王宮", 0, 40 * 20) {
        @Override
        public Map<Item, Integer> materials() {
            Map<Item, Integer> map = new LinkedHashMap<>();
            map.put(Items.QUARTZ_BLOCK, 64);
            map.put(Items.GOLD_BLOCK, 4);
            map.put(Items.DIAMOND_BLOCK, 1);
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

    public static int requiredAmount(int baseAmount, double costMultiplier) {
        return Math.max(1, (int) Math.ceil(baseAmount * costMultiplier));
    }

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
            case WATCHTOWER -> 5;
            case GATE -> 4;
            case WALL -> 5;
            case HERO_SANCTUARY -> 6;
            case GRANARY, INFIRMARY, MARKET -> 6;
            case ARSENAL -> 6;
            case TRAINING_GROUND -> 7;
            case ROYAL_PALACE -> 9;
        };
    }
}
