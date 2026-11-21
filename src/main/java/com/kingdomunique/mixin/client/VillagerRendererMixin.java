package com.kingdomunique.mixin.client;

import com.kingdomunique.client.render.VillagerArmorFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 兵士村人に防具4部位を描画する FeatureRenderer を追加する。 */
@Mixin(VillagerEntityRenderer.class)
public abstract class VillagerRendererMixin
        extends LivingEntityRenderer<VillagerEntity, VillagerResemblingModel<VillagerEntity>> {

    public VillagerRendererMixin(EntityRendererFactory.Context context,
                                 VillagerResemblingModel<VillagerEntity> model,
                                 float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addSoldierArmorRenderer(EntityRendererFactory.Context context, CallbackInfo ci) {
        // 手持ち武器はバニラの VillagerHeldItemFeatureRenderer が描画するため、
        // ここでは防具4部位のみ追加する。
        this.addFeature(new VillagerArmorFeatureRenderer(this, context.getModelLoader(),
                context.getModelManager()));
    }
}
