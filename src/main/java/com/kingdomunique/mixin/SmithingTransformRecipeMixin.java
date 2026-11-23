package com.kingdomunique.mixin;

import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {
    @Inject(method = "craft(Lnet/minecraft/recipe/input/SmithingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN"))
    private void kingdomUnique$rebuildAttributes(SmithingRecipeInput input, RegistryWrapper.WrapperLookup registries,
                                                CallbackInfoReturnable<ItemStack> cir) {
        EnhancementUtil.rebuildSmithingAttributes(cir.getReturnValue());
    }
}
