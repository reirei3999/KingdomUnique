package com.kingdomunique.entity;

import com.kingdomunique.KingdomUniqueMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** カスタムエンティティ登録。 */
public final class ModEntities {

    public static final EntityType<HeroEntity> HERO = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "hero"),
            EntityType.Builder.create(HeroEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .maxTrackingRange(10)
                    .trackingTickInterval(3)
                    .build("hero")
    );

    private static boolean initialized = false;

    private ModEntities() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered hero entity.");
    }
}
