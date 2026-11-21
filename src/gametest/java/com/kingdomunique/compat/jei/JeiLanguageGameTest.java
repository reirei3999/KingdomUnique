package com.kingdomunique.compat.jei;

import com.kingdomunique.building.BuildingType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JeiLanguageGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "jei_language")
    public void informationAndRequirementsFollowLanguageChanges(TestContext context) {
        Language original = Language.getInstance();
        try {
            Language english = load("en_us");
            Language japanese = load("ja_jp");
            var recipe = new KingdomInformationRecipe(List.of(), "jei.kingdomunique.obtain.excalibur");
            var description = recipe.getDescription().getFirst();
            Text requirement = KingdomJeiPlugin.requirementText(List.of(
                    Set.of(BuildingType.TOWN_HALL, BuildingType.BLACKSMITH), Set.of(BuildingType.ROYAL_PALACE)));
            Text noBuilding = KingdomJeiPlugin.requirementText(List.of(Set.of()));
            Text buildTime = Text.translatable("jei.kingdomunique.build_time", 20);
            Language.setInstance(english);
            String englishBody = description.getString();
            context.assertEquals(englishBody, english.get("jei.kingdomunique.obtain.excalibur"),
                    "Information body must resolve the current English translation");
            context.assertEquals(requirement.getString(), "Completed buildings: (Blacksmith AND Town Hall) OR (Royal Palace)",
                    "Requirements must preserve AND/OR grouping and use building names");
            context.assertEquals(noBuilding.getString(), english.get("jei.kingdomunique.no_building"),
                    "Unconditional trades must not list building requirements");
            String englishTime = buildTime.getString();
            Language.setInstance(japanese);
            context.assertEquals(description.getString(), japanese.get("jei.kingdomunique.obtain.excalibur"),
                    "An existing information body must retranslate after changing language");
            context.assertEquals(recipe.getDescription().getFirst().getString(), description.getString(),
                    "Reopening information must use the new language too");
            context.assertFalse(requirement.getString().contains("Town Hall"), "Requirements must not retain English names");
            context.assertTrue(requirement.getString().contains("王城・役場"), "Requirements must use Japanese building names");
            context.assertFalse(buildTime.getString().equals(englishTime), "Category notes must retranslate too");
            context.assertEquals(noBuilding.getString(), japanese.get("jei.kingdomunique.no_building"),
                    "Unconditional trade text must retranslate too");
            boolean rejected = false;
            try {
                KingdomJeiPlugin.requirementText(List.of());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            context.assertTrue(rejected, "Missing unlock alternatives must be rejected");
        } finally {
            Language.setInstance(original);
        }
        context.complete();
    }

    private static Language load(String language) {
        Map<String, String> translations = new HashMap<>();
        try (var input = JeiLanguageGameTest.class.getResourceAsStream(
                "/assets/kingdomunique/lang/" + language + ".json")) {
            if (input == null) {
                throw new IllegalStateException("Missing language resource: " + language);
            }
            Language.load(input, translations::put);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new Language() {
            @Override
            public String get(String key, String fallback) {
                return translations.getOrDefault(key, fallback);
            }

            @Override
            public boolean hasTranslation(String key) {
                return translations.containsKey(key);
            }

            @Override
            public boolean isRightToLeft() {
                return false;
            }

            @Override
            public OrderedText reorder(StringVisitable text) {
                return OrderedText.EMPTY;
            }
        };
    }
}
