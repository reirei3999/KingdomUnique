package com.kingdomunique.mixin.client;

import com.kingdomunique.item.unique.UniqueArmorEffects;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class ArmorVeilRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$renderVeil(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (entity instanceof UniqueArmorEffects.VeiledPlayer veil && veil.ku$isVeiled()) {
            ci.cancel();
        }
    }
}
