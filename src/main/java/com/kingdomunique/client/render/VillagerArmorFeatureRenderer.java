package com.kingdomunique.client.render;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

/** 村人の体形に合わせた防具をバニラの染色・装飾・光沢付きで描画する。 */
public class VillagerArmorFeatureRenderer extends FeatureRenderer<VillagerEntity, VillagerResemblingModel<VillagerEntity>> {

    private final BipedEntityModel<VillagerEntity> innerModel;
    private final BipedEntityModel<VillagerEntity> outerModel;
    private final ArmorDelegateRenderer delegate;

    public VillagerArmorFeatureRenderer(
            FeatureRendererContext<VillagerEntity, VillagerResemblingModel<VillagerEntity>> context,
            BakedModelManager modelManager) {
        super(context);
        this.innerModel = createArmorModel(0.35f);
        this.outerModel = createArmorModel(0.7f);
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

        this.delegate.render(matrices, vertexConsumers, light, entity, limbAngle, limbDistance,
                tickDelta, animationProgress, headYaw, headPitch);
    }

    private static BipedEntityModel<VillagerEntity> createArmorModel(float dilation) {
        ModelData data = ArmorEntityModel.getModelData(new Dilation(dilation));
        // 袖と前腕は村人の交差した腕と同じ支点を使う。
        data.getRoot().addChild("right_arm", ModelPartBuilder.create().uv(40, 16)
                .cuboid(-8, -2, -2, 4, 8, 4, new Dilation(dilation)), ModelTransform.NONE);
        data.getRoot().addChild("left_arm", ModelPartBuilder.create().uv(40, 16).mirrored()
                .cuboid(4, -2, -2, 4, 8, 4, new Dilation(dilation))
                .uv(40, 16).cuboid(-4 + dilation, 2, -2, 8 - 2 * dilation, 4, 4,
                        new Dilation(0, dilation, dilation)), ModelTransform.NONE);
        return new BipedEntityModel<>(TexturedModelData.of(data, 64, 32).createModel());
    }

    /** 村人モデルの姿勢を防具用 Biped モデルへ同期する。 */
    private static void syncVillagerPoseToBiped(VillagerResemblingModel<VillagerEntity> villagerModel,
                                                BipedEntityModel<VillagerEntity> armorModel) {
        villagerModel.copyStateTo(armorModel);
        // 村人の幼体縮尺は本体レンダラー側で適用済み。
        armorModel.child = false;
        ModelPart root = villagerModel.getPart();
        if (root.hasChild("head")) {
            ModelPart vHead = root.getChild("head");
            armorModel.head.copyTransform(vHead);
            armorModel.hat.copyTransform(vHead);
            armorModel.head.yScale *= 1.25f;
            armorModel.hat.yScale *= 1.25f;
        }
        if (root.hasChild("right_leg")) {
            armorModel.rightLeg.copyTransform(root.getChild("right_leg"));
        }
        if (root.hasChild("left_leg")) {
            armorModel.leftLeg.copyTransform(root.getChild("left_leg"));
        }
        armorModel.body.copyTransform(root.getChild("body"));
        armorModel.body.zScale *= 1.4f;
        armorModel.rightArm.copyTransform(root.getChild("arms"));
        armorModel.leftArm.copyTransform(root.getChild("arms"));
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
