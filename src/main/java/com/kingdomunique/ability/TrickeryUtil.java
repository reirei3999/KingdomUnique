package com.kingdomunique.ability;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;

import java.util.concurrent.ThreadLocalRandom;

/** 奇術（trickery）のエンチャント上限拡張ロジック。 */
public final class TrickeryUtil {

    /** 拡張後のエンチャントレベル上限。 */
    public static final int BOOSTED_MAX_LEVEL = 10;

    private TrickeryUtil() {
    }

    /**
     * アイテムの全エンチャントを本来のレベルの1.5〜2.0倍（切り上げ）まで引き上げる。
     */
    public static void boost(ItemStack stack) {
        ItemEnchantmentsComponent current = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (current == null || current.isEmpty()) {
            return;
        }

        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(current);
        for (RegistryEntry<Enchantment> enchantment : current.getEnchantments()) {
            int level = current.getLevel(enchantment);
            double factor = 1.5D + ThreadLocalRandom.current().nextDouble(0.5D);
            int boosted = Math.min(BOOSTED_MAX_LEVEL, (int) Math.ceil(level * factor));
            builder.set(enchantment, Math.max(1, boosted));
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

        // 奇術は防具・動物防具の防御力／耐久にも及ぶ（GENERIC_ARMOR,
        // GENERIC_ARMOR_TOUGHNESS, MAX_DAMAGE を更新）。
        com.kingdomunique.util.EnhancementUtil.boostArmor(stack, 1.5D);
    }
}
