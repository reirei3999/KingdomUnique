package com.kingdomunique.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

final class KingdomRecipeCategory implements IRecipeCategory<KingdomJeiPlugin.DisplayRecipe> {
    private final RecipeType<KingdomJeiPlugin.DisplayRecipe> type;
    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawable slot;

    KingdomRecipeCategory(RecipeType<KingdomJeiPlugin.DisplayRecipe> type, IGuiHelper gui, ItemStack icon) {
        this.type = type;
        this.icon = gui.createDrawableItemStack(icon);
        this.background = gui.createBlankDrawable(172, 100);
        this.slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<KingdomJeiPlugin.DisplayRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Text getTitle() {
        return Text.translatable("jei.kingdomunique." + type.getUid().getPath());
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return 172;
    }

    @Override
    public int getHeight() {
        return 100;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, KingdomJeiPlugin.DisplayRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.inputs().size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 2 + i % 5 * 20, 2 + i / 5 * 20)
                    .setBackground(slot, -1, -1).addItemStack(recipe.inputs().get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 148, 12)
                .setBackground(slot, -1, -1).addItemStack(recipe.output());
    }

    @Override
    public void draw(KingdomJeiPlugin.DisplayRecipe recipe, IRecipeSlotsView slots,
                     DrawContext context, double mouseX, double mouseY) {
        var font = MinecraftClient.getInstance().textRenderer;
        context.drawText(font, Text.translatable("jei.kingdomunique.arrow"), 118, 16, 0x404040, false);
        int y = 48;
        for (Text note : recipe.notes()) {
            for (var line : font.wrapLines(note, getWidth())) {
                context.drawText(font, line, 0, y, 0x404040, false);
                y += 10;
            }
        }
    }
}
