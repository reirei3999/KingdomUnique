package com.kingdomunique.item;

import com.kingdomunique.KingdomUniqueMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** 遺物・呪物に紐づく専用アイテム。 */
public final class ModItems {

    /** 殺戮の宝剣（撃破ごとに攻撃力が上昇する）。 */
    public static final SwordItem SLAUGHTER_BLADE = register("slaughter_blade",
            new SwordItem(ToolMaterials.DIAMOND,
                    new Item.Settings()
                            .maxDamage(ToolMaterials.DIAMOND.getDurability())
                            .component(com.kingdomunique.component.ModDataComponents.UNIQUE_WEAPON_ID,
                                    "slaughter_blade")
                            .component(DataComponentTypes.UNBREAKABLE,
                                    new net.minecraft.component.type.UnbreakableComponent(true))
                            .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                                    SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 3, -2.4F))));

    /** 破壊衝動のツルハシ（あらゆるブロックを即破壊する）。 */
    public static final PickaxeItem DESTRUCTION_PICKAXE = register("destruction_pickaxe",
            new PickaxeItem(ToolMaterials.DIAMOND,
                    new Item.Settings()
                            .maxDamage(ToolMaterials.DIAMOND.getDurability())
                            .component(com.kingdomunique.component.ModDataComponents.UNIQUE_WEAPON_ID,
                                    "destruction_pickaxe")
                            .component(DataComponentTypes.UNBREAKABLE,
                                    new net.minecraft.component.type.UnbreakableComponent(true))
                            .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                                    SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 5, -2.8F))));

    /** 王国の証（村人を国民化する）。 */
    public static final KingdomEmblemItem KINGDOM_EMBLEM = register("kingdom_emblem",
            new KingdomEmblemItem(new Item.Settings().maxCount(16)));

    /** 英雄召喚の角笛。 */
    public static final HeroSummonHornItem HERO_SUMMON_HORN = register("hero_summon_horn",
            new HeroSummonHornItem(new Item.Settings().maxCount(1)));

    /** 憑依のオーブ。 */
    public static final PossessionOrbItem POSSESSION_ORB = register("possession_orb",
            new PossessionOrbItem(new Item.Settings().maxCount(1)));

    private static <T extends Item> T register(String path, T item) {
        return Registry.register(Registries.ITEM,
                Identifier.of(KingdomUniqueMod.MOD_ID, path), item);
    }

    private static boolean initialized = false;

    private ModItems() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered relic/curse items.");
    }
}
