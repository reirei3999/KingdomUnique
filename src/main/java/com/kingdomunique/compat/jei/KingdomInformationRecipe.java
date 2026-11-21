package com.kingdomunique.compat.jei;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.util.List;

public record KingdomInformationRecipe(List<ITypedIngredient<?>> ingredients, String translationKey)
        implements IJeiIngredientInfoRecipe {
    @Override
    public List<ITypedIngredient<?>> getIngredients() {
        return ingredients;
    }

    @Override
    public List<StringVisitable> getDescription() {
        return List.of(Text.translatable(translationKey));
    }
}
