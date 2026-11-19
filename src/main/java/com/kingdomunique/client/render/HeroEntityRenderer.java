package com.kingdomunique.client.render;

import com.kingdomunique.entity.HeroEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/** 英雄NPC用レンダラー。バニラのプレイヤーモデル＋防具/手持ち描画を使用する。 */
public final class HeroEntityRenderer extends LivingEntityRenderer<HeroEntity, BipedEntityModel<HeroEntity>> {

    private static final Identifier STEVE =
            Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private static final Identifier VILLAGER =
            Identifier.of("minecraft", "textures/entity/villager/villager.png");

    public HeroEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5F);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public Identifier getTexture(HeroEntity entity) {
        return entity.getHeroClass() == com.kingdomunique.entity.HeroClass.SMITH ? VILLAGER : STEVE;
    }
}
