package com.kingdomunique.item.unique;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.component.ModDataComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** 世界に一つだけの武器登録。 */
public final class UniqueWeapons {

    public static final ExcaliburItem EXCALIBUR = register("excalibur",
            new ExcaliburItem(ToolMaterials.NETHERITE, base("excalibur", 4000)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 7, -2.2F))));

    public static final GaeBolgItem GAE_BOLG = register("gae_bolg",
            new GaeBolgItem(base("gae_bolg", 3000)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            net.minecraft.item.TridentItem.createAttributeModifiers())));

    public static final MuramasaItem MURAMASA = register("muramasa",
            new MuramasaItem(ToolMaterials.DIAMOND, base("muramasa", 4000)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 6, -2.0F))));

    public static final ApolloBowItem APOLLO_BOW = register("apollo_bow",
            new ApolloBowItem(base("apollo_bow", 2500)));

    public static final MjolnirItem MJOLNIR = register("mjolnir",
            new MjolnirItem(ToolMaterials.NETHERITE, base("mjolnir", 2000)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 5, -2.6F))));

    public static final ChronosDaggerItem CHRONOS_DAGGER = register("chronos_dagger",
            new ChronosDaggerItem(ToolMaterials.DIAMOND, base("chronos_dagger", 2500)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 3, -1.2F))));

    public static final TrishulaItem TRISHULA = register("trishula",
            new TrishulaItem(base("trishula", 3000)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            net.minecraft.item.TridentItem.createAttributeModifiers())));

    public static final KusanagiItem KUSANAGI = register("kusanagi",
            new KusanagiItem(ToolMaterials.DIAMOND, base("kusanagi", 2500)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 3, -2.4F))));

    public static final PoseidonTridentItem POSEIDON_TRIDENT = register("poseidon_trident",
            new PoseidonTridentItem(base("poseidon_trident", 2500)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            net.minecraft.item.TridentItem.createAttributeModifiers())));

    public static final TwilightScytheItem TWILIGHT_SCYTHE = register("twilight_scythe",
            new TwilightScytheItem(ToolMaterials.DIAMOND, base("twilight_scythe", 2500)
                    .component(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 3, -2.6F))));

    public static final String[] ALL_IDS = {
            "excalibur", "gae_bolg", "muramasa", "apollo_bow", "mjolnir", "chronos_dagger",
            "kusanagi", "poseidon_trident", "twilight_scythe", "trishula"
    };

    private static Item.Settings base(String uniqueId, int durability) {
        return new Item.Settings()
                .maxDamage(durability)
                .component(ModDataComponents.UNIQUE_WEAPON_ID, uniqueId)
                .component(DataComponentTypes.UNBREAKABLE,
                        new net.minecraft.component.type.UnbreakableComponent(true))
                .component(DataComponentTypes.CUSTOM_NAME,
                        Text.translatable("item.kingdomunique." + uniqueId)
                                .styled(style -> style.withColor(Formatting.GOLD)
                                        .withBold(true).withItalic(false)));
    }

    private static <T extends Item> T register(String path, T item) {
        return Registry.register(Registries.ITEM,
                Identifier.of(KingdomUniqueMod.MOD_ID, path), item);
    }

    private static boolean initialized = false;

    private UniqueWeapons() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered {} unique weapons.", ALL_IDS.length);
    }
}
