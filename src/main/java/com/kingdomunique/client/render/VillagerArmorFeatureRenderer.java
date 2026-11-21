package com.kingdomunique.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

/** 兵士村人に防具4部位を描画する（Gemini 提示を 1.21.1 の型制約へ適合）。 */
public class VillagerArmorFeatureRenderer extends FeatureRenderer<VillagerEntity, VillagerResemblingModel<VillagerEntity>> {

    private final BipedEntityModel<VillagerEntity> innerModel;
    private final BipedEntityModel<VillagerEntity> outerModel;
    private final ArmorDelegateRenderer delegate;

    public VillagerArmorFeatureRenderer(
            FeatureRendererContext<VillagerEntity, VillagerResemblingModel<VillagerEntity>> context,
            EntityModelLoader modelLoader,
            BakedModelManager modelManager) {
        super(context);
        this.innerModel = new BipedEntityModel<>(modelLoader.getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR));
        this.outerModel = new BipedEntityModel<>(modelLoader.getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
        // ArmorFeatureRenderer は M も BipedEntityModel を要求するため、
        // 村人モデルを直接コンテキストに渡せない。Biped用コンテキストを自前で用意する。
        FeatureRendererContext<VillagerEntity, BipedEntityModel<VillagerEntity>> bipedContext =
                new FeatureRendererContext<>() {
                    @Override
                    public BipedEntityModel<VillagerEntity> getModel() {
                        return VillagerArmorFeatureRenderer.this.outerModel;
                    }

                    @Override
                    public Identifier getTexture(VillagerEntity entity) {
                        return context.getTexture(entity);
                    }
                };
        this.delegate = new ArmorDelegateRenderer(bipedContext, innerModel, outerModel, modelManager);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       VillagerEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
            return;
        }
        VillagerResemblingModel<VillagerEntity> villagerModel = this.getContextModel();
        syncVillagerPoseToBiped(villagerModel, this.innerModel);
        syncVillagerPoseToBiped(villagerModel, this.outerModel);

        matrices.push();
        matrices.scale(1.08f, 1.0f, 1.08f);
        this.delegate.render(matrices, vertexConsumers, light, entity, limbAngle, limbDistance,
                tickDelta, animationProgress, headYaw, headPitch);
        matrices.pop();
    }

    /** 村人モデルの姿勢を防具用 Biped モデルへ同期する。 */
    private static void syncVillagerPoseToBiped(VillagerResemblingModel<VillagerEntity> villagerModel,
                                                BipedEntityModel<VillagerEntity> armorModel) {
        ModelPart root = villagerModel.getPart();
        if (root.hasChild("head")) {
            ModelPart vHead = root.getChild("head");
            armorModel.head.copyTransform(vHead);
            armorModel.hat.copyTransform(vHead);
            armorModel.head.pivotY -= 1.5f;
            armorModel.hat.pivotY -= 1.5f;
        }
        if (root.hasChild("right_leg")) {
            armorModel.rightLeg.copyTransform(root.getChild("right_leg"));
        }
        if (root.hasChild("left_leg")) {
            armorModel.leftLeg.copyTransform(root.getChild("left_leg"));
        }
        armorModel.body.pitch = 0.0f;
        armorModel.body.yaw = 0.0f;
        armorModel.body.roll = 0.0f;
        armorModel.rightArm.pitch = -0.35f;
        armorModel.rightArm.yaw = 0.0f;
        armorModel.rightArm.roll = 0.05f;
        armorModel.leftArm.pitch = -0.35f;
        armorModel.leftArm.yaw = 0.0f;
        armorModel.leftArm.roll = -0.05f;
    }

    /** バニラの ArmorFeatureRenderer を村人向けに呼び出すための委譲クラス。 */
    private static class ArmorDelegateRenderer extends ArmorFeatureRenderer<
            VillagerEntity, BipedEntityModel<VillagerEntity>, BipedEntityModel<VillagerEntity>> {
        ArmorDelegateRenderer(
                FeatureRendererContext<VillagerEntity, BipedEntityModel<VillagerEntity>> context,
                BipedEntityModel<VillagerEntity> innerModel,
                BipedEntityModel<VillagerEntity> outerModel,
                BakedModelManager modelManager) {
            super(context, innerModel, outerModel, modelManager);
        }
    }
}
