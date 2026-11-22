package com.kingdomunique.item.unique;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class UniqueArmorItem extends ArmorItem {
    private final Item enhancementMaterial;

    UniqueArmorItem(Type type, Item enhancementMaterial) {
        super(ArmorMaterials.NETHERITE, type, new Settings()
                .maxDamage(type.getMaxDamage(37))
                .component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(true))
                .rarity(net.minecraft.util.Rarity.EPIC)
                .fireproof());
        this.enhancementMaterial = enhancementMaterial;
    }

    public boolean acceptsEnhancementMaterial(ItemStack stack) {
        return stack.isOf(enhancementMaterial);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable(getTranslationKey() + ".effect").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable(getTranslationKey() + ".acquisition").formatted(Formatting.GRAY));
    }
}
