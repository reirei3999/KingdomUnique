package com.kingdomunique.entity;

import com.kingdomunique.KingdomUniqueMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.IronGolemEntity;
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

    public static final EntityType<KingdomGuardianEntity> KINGDOM_GUARDIAN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "kingdom_guardian"),
            EntityType.Builder.create(KingdomGuardianEntity::new, SpawnGroup.MISC)
                    .dimensions(1.4F, 2.7F)
                    .maxTrackingRange(10)
                    .build("kingdom_guardian")
    );

    private static boolean initialized = false;

    private ModEntities() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        FabricDefaultAttributeRegistry.register(HERO, HeroEntity.createHeroAttributes());
        FabricDefaultAttributeRegistry.register(KINGDOM_GUARDIAN, IronGolemEntity.createIronGolemAttributes());
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered hero entity.");
    }
}
