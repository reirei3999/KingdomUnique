package com.kingdomunique.building;

import com.kingdomunique.KingdomUniqueMod;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** 設計図ブロックの BlockEntityType 登録。 */
public final class ModBlueprintEntities {

    public static final BlockEntityType<BlueprintBlockEntity> BLUEPRINT_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "blueprint"),
            BlockEntityType.Builder.create(
                    BlueprintBlockEntity::new,
                    ModBlocks.HOUSE, ModBlocks.BARRACKS, ModBlocks.BLACKSMITH, ModBlocks.TOWN_HALL)
                    .build(null)
    );

    private static boolean initialized = false;

    private ModBlueprintEntities() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered blueprint BlockEntity.");
    }
}
