package com.kingdomunique.building;

import com.kingdomunique.KingdomUniqueMod;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** 設計図ブロックと対応する BlockItem の登録。 */
public final class ModBlocks {

    public static final BlueprintBlock HOUSE = register("blueprint_house", BuildingType.HOUSE);
    public static final BlueprintBlock BARRACKS = register("blueprint_barracks", BuildingType.BARRACKS);
    public static final BlueprintBlock BLACKSMITH = register("blueprint_blacksmith", BuildingType.BLACKSMITH);
    public static final BlueprintBlock TOWN_HALL = register("blueprint_town_hall", BuildingType.TOWN_HALL);
    public static final BlueprintBlock WATCHTOWER = register("blueprint_watchtower", BuildingType.WATCHTOWER);
    public static final BlueprintBlock GATE = register("blueprint_gate", BuildingType.GATE);
    public static final BlueprintBlock WALL = register("blueprint_wall", BuildingType.WALL);

    private static BlueprintBlock register(String path, BuildingType type) {
        Identifier id = Identifier.of(KingdomUniqueMod.MOD_ID, path);
        BlueprintBlock block = Registry.register(Registries.BLOCK, id,
                new BlueprintBlock(type, Block.Settings.create().strength(0.0F)));
        Registry.register(Registries.ITEM, id,
                new BlockItem(block, new Item.Settings().maxCount(16)));
        return block;
    }

    private static boolean initialized = false;

    private ModBlocks() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModBlueprintEntities.initialize();
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered blueprint blocks.");
    }
}
