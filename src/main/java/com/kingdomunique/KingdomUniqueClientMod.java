package com.kingdomunique;

import com.kingdomunique.client.BlueprintClientOverlay;
import com.kingdomunique.client.KeyBindings;
import com.kingdomunique.client.SelectionClient;
import com.kingdomunique.client.render.HeroEntityRenderer;
import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.BowItem;
import net.minecraft.util.Identifier;

/** KingdomUnique のクライアントサイドエントリポイント。 */
public class KingdomUniqueClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // カスタムコンポーネントのネットワークコーデックをクライアント側でも登録する。
        ModDataComponents.initialize();

        // 能力発動キー(R) / モード切替キー(V) を登録する。
        KeyBindings.initialize();

        // 選択GUIのパケット受信を登録する。
        SelectionClient.initialize();

        // 英雄NPCのレンダラーを登録する（Bipedモデル＋防具/手持ち描画）。
        EntityRendererRegistry.register(ModEntities.HERO, HeroEntityRenderer::new);

        // 設計図の建材HUDと3Dプレビュー。
        BlueprintClientOverlay.initialize();

        // アポロン弓の引き絞りテクスチャ（バニラ bow モデルと同じ predicate）。
        ModelPredicateProviderRegistry.register(UniqueWeapons.APOLLO_BOW,
                Identifier.of("pulling"), (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem()
                                && entity.getActiveItem() == stack ? 1.0F : 0.0F);
        ModelPredicateProviderRegistry.register(UniqueWeapons.APOLLO_BOW,
                Identifier.of("pull"), (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem()
                                && entity.getActiveItem() == stack
                                ? BowItem.getPullProgress(entity.getItemUseTime()) : 0.0F);

        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Client entrypoint initialized.");
    }
}
