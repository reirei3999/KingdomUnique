package com.kingdomunique.effect;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.util.EnhancementUtil;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class ArmorMigrationGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void legacyEnhancementMigratesOnceBeforeFurtherSynthesis(TestContext context) {
        ItemStack armor = legacyArmor(2);
        armor.remove(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        armor.set(DataComponentTypes.MAX_DAMAGE, 717);
        EnhancementUtil.migrateLegacy(armor);
        assertArmor(context, armor, 9.68D);
        context.assertTrue(armor.contains(ModDataComponents.ATTR_VERSION), "Migration must mark repaired armor");
        var modifiers = armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        EnhancementUtil.migrateLegacy(armor);
        context.assertEquals(modifiers, armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS), "Migration must be idempotent");
        context.assertTrue(armor.getMaxDamage() == 717, "Migration must not rescale durability");

        ItemStack right = legacyArmor(2);
        var result = EnhancementUtil.tryEnhance(armor, right, null, false);
        context.assertTrue(result != null, "Migrated and legacy armor must combine");
        assertArmor(context, right, 9.68D);
        assertArmor(context, result.stack(), 8.0D * EnhancementUtil.multiplier(3));
        context.assertTrue(right.contains(ModDataComponents.ATTR_VERSION), "Anvil must migrate both inputs");
        System.out.println("Armor migration: legacy +2=9.68, next synthesis +3=" + 8.0D * EnhancementUtil.multiplier(3));
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void legacyDivineBonusRestoresBaseArmor(TestContext context) {
        ItemStack armor = legacyArmor(0);
        armor.set(ModDataComponents.DIVINE_ARMOR, 0.25F);
        armor.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.builder().add(
                EntityAttributes.GENERIC_ARMOR,
                new EntityAttributeModifier(Identifier.of("kingdomunique", "divine_armor"), 0.25D,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), AttributeModifierSlot.CHEST).build());
        int durability = armor.getMaxDamage();
        EnhancementUtil.migrateLegacy(armor);
        assertArmor(context, armor, 10.0D);
        EnhancementUtil.migrateLegacy(armor);
        assertArmor(context, armor, 10.0D);
        context.assertTrue(armor.getMaxDamage() == durability, "Divine migration must not change durability");
        ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
        EnhancementUtil.migrateLegacy(plain);
        context.assertFalse(plain.contains(ModDataComponents.ATTR_VERSION), "Plain armor must remain unmarked");
        EnhancementUtil.migrateLegacy(ItemStack.EMPTY);
        EnhancementUtil.migrateLegacy(null);
        ItemStack tool = new ItemStack(Items.DIAMOND_SWORD);
        tool.set(ModDataComponents.ENHANCE_LEVEL, 2);
        EnhancementUtil.migrateLegacy(tool);
        context.assertFalse(tool.contains(ModDataComponents.ATTR_VERSION), "Migration must only repair armor");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void inventoryTickMigratesWornHeldAndStoredArmor(TestContext context) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "armor_migration"), SyncedClientOptions.createDefault());
        ItemStack worn = legacyArmor(2);
        player.equipStack(EquipmentSlot.CHEST, worn);
        player.equipStack(EquipmentSlot.MAINHAND, legacyArmor(2));
        player.equipStack(EquipmentSlot.OFFHAND, legacyArmor(2));
        player.getInventory().setStack(12, legacyArmor(2));
        for (int tick = 0; tick < 4; tick++) {
            player.getInventory().updateItems();
            assertArmor(context, worn, 9.68D);
        }
        for (int slot : new int[]{0, 12, 38, 40}) {
            context.assertTrue(player.getInventory().getStack(slot).contains(ModDataComponents.ATTR_VERSION),
                    "Inventory tick must migrate slot " + slot);
        }
        var result = EnhancementUtil.tryEnhance(worn, worn.copy(), player, false);
        context.assertTrue(result != null, "Tick-migrated armor must combine");
        assertArmor(context, result.stack(), 8.0D * EnhancementUtil.multiplier(3));
        System.out.println("Inventory migration: worn/held/stored armor repaired; four ticks do not rescale");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void smithingRetainsAccumulatedArmorBoost(TestContext context) {
        ItemStack diamond = new ItemStack(Items.DIAMOND_CHESTPLATE);
        EnhancementUtil.boostArmor(diamond, 2.0D);
        EnhancementUtil.boostArmor(diamond, 1.25D);
        diamond = EnhancementUtil.applyLevel(diamond, 0, 1);
        context.assertTrue(diamond.contains(ModDataComponents.ATTR_VERSION), "Fixed writers must mark attributes");
        var input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                diamond, new ItemStack(Items.NETHERITE_INGOT));
        var recipe = context.getWorld().getRecipeManager().getFirstMatch(RecipeType.SMITHING, input, context.getWorld());
        context.assertTrue(recipe.isPresent(), "Netherite smithing recipe must match");
        ItemStack netherite = recipe.orElseThrow().value().craft(input, context.getWorld().getRegistryManager());
        context.assertEquals(2.5D, netherite.get(ModDataComponents.ARMOR_BOOST), "Smithing must preserve accumulated boost");
        var wearer = context.spawnEntity(EntityType.ARMOR_STAND, 1, 2, 1);
        wearer.equipStack(EquipmentSlot.CHEST, netherite);
        context.waitAndRun(2, () -> {
            assertNear(context, wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR), 22.0D);
            assertNear(context, wearer.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS), 8.25D);
            assertNear(context, wearer.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 0.11D);
            context.assertTrue(netherite.contains(ModDataComponents.ATTR_VERSION), "Smithing must mark rebuilt attributes");
            EnhancementUtil.migrateLegacy(netherite);
            assertArmor(context, netherite, 22.0D);
            System.out.println("Boosted netherite: factor=2.5, armor=22.0, toughness=8.25, knockback=0.11");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void legacyCustomModifiersArePreserved(TestContext context) {
        var custom = new EntityAttributeModifier(Identifier.of("kingdomunique", "custom_legacy_armor"),
                2.0D, EntityAttributeModifier.Operation.ADD_VALUE);
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, armor.getItem().getAttributeModifiers()
                .with(EntityAttributes.GENERIC_ARMOR, custom, AttributeModifierSlot.CHEST).withShowInTooltip(false));
        armor = EnhancementUtil.applyLevel(armor, 0, 3);
        armor.remove(ModDataComponents.ATTR_VERSION);
        var original = armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        int durability = armor.getMaxDamage();
        EnhancementUtil.migrateLegacy(armor);
        context.assertEquals(original, armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "Legacy custom and default entries must remain unchanged when defaults already exist");
        context.assertTrue(armor.contains(ModDataComponents.ATTR_VERSION), "Unchanged legacy armor must still be marked");
        context.assertTrue(armor.getMaxDamage() == durability, "Custom armor migration must preserve durability");
        EnhancementUtil.migrateLegacy(armor);
        context.assertEquals(original, armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "Marked custom armor must remain unchanged");

        ItemStack missingDefaults = legacyArmor(2);
        var customOnly = AttributeModifiersComponent.builder().add(
                EntityAttributes.GENERIC_ARMOR, custom, AttributeModifierSlot.CHEST).build().withShowInTooltip(false);
        missingDefaults.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, customOnly);
        EnhancementUtil.migrateLegacy(missingDefaults);
        var repaired = missingDefaults.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        context.assertTrue(repaired.modifiers().containsAll(customOnly.modifiers()),
                "Adding missing defaults must not rescale or remove existing custom entries");
        context.assertFalse(repaired.showInTooltip(), "Migration must retain tooltip visibility");
        assertArmor(context, missingDefaults, 9.68D + 2.0D);
        context.assertTrue(repaired.modifiers().size() == customOnly.modifiers().size()
                        + missingDefaults.getItem().getAttributeModifiers().modifiers().size(),
                "Migration must add every missing default, including entries sharing an ID");
        EnhancementUtil.migrateLegacy(missingDefaults);
        context.assertEquals(repaired, missingDefaults.get(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "Custom armor repaired with missing defaults must be idempotent");
        System.out.println("Legacy custom armor: existing defaults/custom entries unchanged; missing defaults added without rescaling custom entries");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "armor_migration")
    public void legacySharedModifierIdDoesNotHideMissingArmor(TestContext context) {
        ItemStack armor = legacyArmor(2);
        var defaults = armor.getItem().getAttributeModifiers();
        var armorEntry = defaults.modifiers().stream()
                .filter(entry -> entry.attribute().equals(EntityAttributes.GENERIC_ARMOR)).findFirst().orElseThrow();
        var customToughness = new EntityAttributeModifier(armorEntry.modifier().id(), 7.0D,
                EntityAttributeModifier.Operation.ADD_VALUE);
        var customOnly = AttributeModifiersComponent.builder().add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                customToughness, AttributeModifierSlot.CHEST).build();
        armor.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, customOnly);
        EnhancementUtil.migrateLegacy(armor);
        var repaired = armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        assertArmor(context, armor, 9.68D);
        context.assertTrue(repaired.modifiers().containsAll(customOnly.modifiers()),
                "Custom toughness sharing the vanilla armor ID must be preserved unchanged");
        context.assertTrue(repaired.modifiers().stream()
                        .filter(entry -> entry.attribute().equals(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)).count() == 1,
                "Existing toughness must not be duplicated");
        context.assertTrue(armor.contains(ModDataComponents.ATTR_VERSION), "Repaired armor must be marked");
        EnhancementUtil.migrateLegacy(armor);
        context.assertEquals(repaired, armor.get(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "Shared-ID migration must be idempotent");
        System.out.println("Shared-ID legacy armor: missing armor=9.68 added; custom toughness=7.0 preserved");
        context.complete();
    }

    private static ItemStack legacyArmor(int level) {
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.set(ModDataComponents.ENHANCE_LEVEL, level);
        armor.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        return armor;
    }

    private static void assertArmor(TestContext context, ItemStack stack, double expected) {
        var modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        context.assertTrue(modifiers != null, "Armor must have rebuilt modifiers");
        var armor = new EntityAttributeInstance(EntityAttributes.GENERIC_ARMOR, instance -> {});
        modifiers.applyModifiers(EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.equals(EntityAttributes.GENERIC_ARMOR)) {
                armor.addTemporaryModifier(modifier);
            }
        });
        assertNear(context, armor.getValue(), expected);
    }

    private static void assertNear(TestContext context, double actual, double expected) {
        context.assertTrue(Math.abs(actual - expected) < 1.0E-6, "Expected " + expected + ", got " + actual);
    }
}
