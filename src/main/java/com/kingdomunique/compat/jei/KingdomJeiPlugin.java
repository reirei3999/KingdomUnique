package com.kingdomunique.compat.jei;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.trade.TradeRewardFactory;
import com.kingdomunique.trade.TradeDisplayOffers;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@JeiPlugin
public final class KingdomJeiPlugin implements IModPlugin {
    static final RecipeType<DisplayRecipe> CONSTRUCTION =
            RecipeType.create("kingdomunique", "construction", DisplayRecipe.class);
    static final RecipeType<DisplayRecipe> TRADES =
            RecipeType.create("kingdomunique", "citizen_trades", DisplayRecipe.class);

    record DisplayRecipe(List<ItemStack> inputs, ItemStack output, List<Text> notes) {
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.of("kingdomunique", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> items = Registries.ITEM.getIds().stream()
                .filter(id -> id.getNamespace().equals("kingdomunique"))
                .map(id -> new ItemStack(Registries.ITEM.get(id))).toList();
        if (!items.isEmpty()) {
            runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, items);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new KingdomRecipeCategory(CONSTRUCTION, gui, TradeRewardFactory.createBlueprintStack(BuildingType.HOUSE)),
                new KingdomRecipeCategory(TRADES, gui, new ItemStack(Items.EMERALD)));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (BuildingType type : BuildingType.values()) {
            registration.addRecipeCatalyst(TradeRewardFactory.createBlueprintStack(type), CONSTRUCTION);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<DisplayRecipe> construction = new ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            List<ItemStack> inputs = new ArrayList<>();
            type.materials().forEach((item, count) -> {
                int remaining = count;
                while (remaining > 0) {
                    int size = Math.min(remaining, item.getMaxCount());
                    inputs.add(new ItemStack(item, size));
                    remaining -= size;
                }
            });
            construction.add(new DisplayRecipe(inputs, TradeRewardFactory.createBlueprintStack(type),
                    List.of(Text.translatable("jei.kingdomunique.build_time", type.getBaseBuildTicks() / 20),
                            Text.translatable("jei.kingdomunique.base_costs"))));
        }
        registration.addRecipes(CONSTRUCTION, construction);
        List<DisplayRecipe> trades = new ArrayList<>();
        for (String role : List.of("farmer", "builder", "smith", "citizen")) {
            for (TradeDisplayOffers.Entry entry : TradeDisplayOffers.build(role)) {
                var offer = entry.offer();
                List<ItemStack> inputs = new ArrayList<>();
                inputs.add(offer.getDisplayedFirstBuyItem());
                if (!offer.getDisplayedSecondBuyItem().isEmpty()) {
                    inputs.add(offer.getDisplayedSecondBuyItem());
                }
                trades.add(new DisplayRecipe(inputs, offer.getSellItem().copy(),
                        List.of(Text.translatable("jei.kingdomunique.role." + role), requirementText(entry.alternatives()))));
            }
        }
        registration.addRecipes(TRADES, trades);
        Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals("kingdomunique")).forEach(id -> {
            String path = id.getPath();
            String info = switch (path) {
                case "excalibur", "gae_bolg", "muramasa", "apollo_bow", "mjolnir", "chronos_dagger",
                     "slaughter_blade", "destruction_pickaxe" -> path;
                default -> path.startsWith("blueprint_") ? "blueprint" : "admin";
            };
            registration.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(Registries.ITEM.get(id))).ifPresent(ingredient ->
                    registration.addRecipes(RecipeTypes.INFORMATION, List.of(new KingdomInformationRecipe(
                            List.of(ingredient), "jei.kingdomunique.obtain." + info))));
        });
    }

    static Text requirementText(List<Set<BuildingType>> alternatives) {
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("A trade must have at least one unlock alternative");
        }
        if (alternatives.stream().anyMatch(Set::isEmpty)) {
            return Text.translatable("jei.kingdomunique.no_building");
        }
        Text result = null;
        for (Set<BuildingType> required : alternatives) {
            Text conjunction = null;
            for (BuildingType building : required.stream().sorted().toList()) {
                Text name = Text.translatable("jei.kingdomunique.building." + building.name().toLowerCase(Locale.ROOT));
                conjunction = conjunction == null ? name
                        : Text.translatable("jei.kingdomunique.and", conjunction, name);
            }
            result = result == null ? conjunction : Text.translatable("jei.kingdomunique.or", result, conjunction);
        }
        return Text.translatable("jei.kingdomunique.unlock", result);
    }
}
