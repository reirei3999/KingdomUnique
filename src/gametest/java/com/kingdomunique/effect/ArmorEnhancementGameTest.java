package com.kingdomunique.effect;

import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public final class ArmorEnhancementGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_enhancement")
    public void synthesisIncreasesEquippedDefense(TestContext context) {
        ItemStack base = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var first = EnhancementUtil.tryEnhance(base, base.copy(), null, false);
        context.assertTrue(first != null, "Ordinary chestplates must combine");
        var second = EnhancementUtil.tryEnhance(first.stack(), first.stack().copy(), null, false);
        context.assertTrue(second != null, "Enhanced chestplates must combine again");
        var wearer = context.spawnEntity(EntityType.ARMOR_STAND, 1, 2, 1);
        wearer.equipStack(EquipmentSlot.CHEST, base);
        context.waitAndRun(2, () -> {
            double baseArmor = wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
            context.assertTrue(Math.abs(baseArmor - 8.0D) < 1.0E-6, "Base chestplate must provide 8 armor");
            wearer.equipStack(EquipmentSlot.CHEST, first.stack());
            context.waitAndRun(2, () -> {
                double firstArmor = wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
                context.assertTrue(firstArmor > baseArmor, "Synthesis must increase equipped armor");
                assertArmor(context, firstArmor, baseArmor * EnhancementUtil.multiplier(1));
                wearer.equipStack(EquipmentSlot.CHEST, second.stack());
                context.waitAndRun(2, () -> {
                    double secondArmor = wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
                    assertArmor(context, secondArmor, baseArmor * EnhancementUtil.multiplier(2));
                    System.out.println("Armor enhancement: base=" + baseArmor + ", +1=" + firstArmor
                            + ", +2=" + secondArmor);
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_enhancement")
    public void divineBonusPreservesBaseDefense(TestContext context) {
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        EnhancementUtil.applyDivineBonuses(armor, 0, 0, 0, 0.25F, 0);
        var wearer = context.spawnEntity(EntityType.ARMOR_STAND, 1, 2, 1);
        wearer.equipStack(EquipmentSlot.CHEST, armor);
        context.waitAndRun(2, () -> {
            double defense = wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR);
            context.assertTrue(defense >= 8.0D, "Divine bonus must preserve base armor");
            assertArmor(context, defense, 10.0D);
            System.out.println("Divine armor: base=8.0, bonus=" + defense);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_enhancement")
    public void emptyAndExplicitModifiersAndInvalidInputs(TestContext context) {
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        EnhancementUtil.boostArmor(armor, 2.0D);
        var original = Items.DIAMOND_CHESTPLATE.getAttributeModifiers().modifiers();
        var boosted = armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers();
        context.assertTrue(original.size() == boosted.size(), "Boost must retain default modifiers");
        for (int i = 0; i < original.size(); i++) {
            context.assertEquals(original.get(i).modifier().id(), boosted.get(i).modifier().id(), "Modifier ID");
            context.assertEquals(original.get(i).slot(), boosted.get(i).slot(), "Equipment slot");
            context.assertEquals(original.get(i).modifier().operation(), boosted.get(i).modifier().operation(), "Operation");
        }
        var enhanced = EnhancementUtil.applyLevel(armor, 0, 1);
        var wearer = context.spawnEntity(EntityType.ARMOR_STAND, 1, 2, 1);
        wearer.equipStack(EquipmentSlot.CHEST, enhanced);
        context.assertTrue(EnhancementUtil.tryEnhance(ItemStack.EMPTY, armor, null, false) == null,
                "Empty input must be rejected");
        context.assertTrue(EnhancementUtil.tryEnhance(armor, new ItemStack(Items.DIAMOND_BOOTS), null, false) == null,
                "Different items must be rejected");
        context.assertTrue(EnhancementUtil.tryEnhance(armor, enhanced, null, false) == null,
                "Different levels must be rejected");
        context.waitAndRun(2, () -> {
            assertArmor(context, wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR), 16.0D * 1.1D);
            context.complete();
        });
    }

    private static void assertArmor(TestContext context, double actual, double expected) {
        context.assertTrue(Math.abs(actual - expected) < 1.0E-6,
                "Expected armor " + expected + ", got " + actual);
    }
}
