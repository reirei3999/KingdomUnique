package com.kingdomunique.item.unique;

import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class UniqueArmors {
    public static final UniqueArmorItem HADES_HELMET = register("hades_helmet",
            new UniqueArmorItem(ArmorItem.Type.HELMET, Items.NETHERITE_HELMET));
    public static final UniqueArmorItem MIMIR_HELMET = register("mimir_helmet",
            new UniqueArmorItem(ArmorItem.Type.HELMET, Items.NETHERITE_HELMET));
    public static final UniqueArmorItem DRAGONSCALE_WINGPLATE = register("dragonscale_wingplate",
            new Wingplate());
    public static final UniqueArmorItem NEMEAN_LION_HIDE = register("nemean_lion_hide",
            new UniqueArmorItem(ArmorItem.Type.CHESTPLATE, Items.NETHERITE_CHESTPLATE));
    public static final UniqueArmorItem JOTUN_LEGGINGS = register("jotun_leggings",
            new UniqueArmorItem(ArmorItem.Type.LEGGINGS, Items.NETHERITE_LEGGINGS));
    public static final UniqueArmorItem HERMES_TALARIA = register("hermes_talaria",
            new UniqueArmorItem(ArmorItem.Type.BOOTS, Items.NETHERITE_BOOTS));
    public static final UniqueArmorItem SALAMANDER_BOOTS = register("salamander_boots",
            new UniqueArmorItem(ArmorItem.Type.BOOTS, Items.NETHERITE_BOOTS));

    private UniqueArmors() {
    }

    public static void initialize() {
    }

    public static boolean isWearing(LivingEntity entity, UniqueArmorItem item) {
        EquipmentSlot slot = item.getSlotType();
        return entity.getEquippedStack(slot).isOf(item);
    }

    private static UniqueArmorItem register(String id, UniqueArmorItem item) {
        return Registry.register(Registries.ITEM, Identifier.of("kingdomunique", id), item);
    }

    private static final class Wingplate extends UniqueArmorItem implements FabricElytraItem {
        private Wingplate() {
            super(Type.CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        }
    }
}
