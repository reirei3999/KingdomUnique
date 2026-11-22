package com.kingdomunique.mixin.client;

import com.kingdomunique.item.unique.UniqueArmors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ElytraFeatureRenderer.class)
public abstract class ArmorElytraRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
    private boolean kingdomUnique$renderWingplateWings(ItemStack stack, Item elytra, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, LivingEntity wearer, float limbAngle, float limbDistance,
            float tickDelta, float animationProgress, float headYaw, float headPitch) {
        return stack.isOf(elytra) || (stack.isOf(UniqueArmors.DRAGONSCALE_WINGPLATE) && wearer.isFallFlying());
    }
}
