package com.kingdomunique;

import com.kingdomunique.client.BlueprintClientOverlay;
import com.kingdomunique.client.CitizenMerchantScreen;
import com.kingdomunique.client.KeyBindings;
import com.kingdomunique.client.SelectionClient;
import com.kingdomunique.client.SoldierEquipmentScreen;
import com.kingdomunique.soldier.SoldierScreenHandlers;
import com.kingdomunique.trade.CitizenMerchantScreenHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import com.kingdomunique.client.render.HeroEntityRenderer;
import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.entity.ModEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.ClientModInitializer;

/** KingdomUnique のクライアントサイドエントリポイント。 */
public class KingdomUniqueClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // カスタムコンポーネントのネットワークコーデックをクライアント側でも登録する。
        ModDataComponents.initialize();
        HandledScreens.register(SoldierScreenHandlers.EQUIPMENT, SoldierEquipmentScreen::new);
        HandledScreens.register(CitizenMerchantScreenHandler.TYPE, CitizenMerchantScreen::new);

        // 能力発動キー(R) / モード切替キー(V) を登録する。
        KeyBindings.initialize();

        // 選択GUIのパケット受信を登録する。
        SelectionClient.initialize();

        // ステータス確認画面（/ku info・Kキー）と統合HUD。
        com.kingdomunique.client.StatusClient.initialize();
        com.kingdomunique.client.KingdomUnifiedHud.register();
        com.kingdomunique.client.KingdomUnifiedHud.registerPopulationReceiver();

        // 英雄NPCのレンダラーを登録する（Bipedモデル＋防具/手持ち描画）。
        EntityRendererRegistry.register(ModEntities.HERO, HeroEntityRenderer::new);

        // 設計図の建材HUDと3Dプレビュー。
        BlueprintClientOverlay.initialize();

        // 建築済み／建築中の占有範囲を受信してプレビューの赤枠判定に使う。
        com.kingdomunique.client.ClientBuildingBoxCache.registerReceiver();

        // 設計図アイテムを持った際のパーティクル枠線プレビュー（向き・入口・建築可否を表示）。
        com.kingdomunique.client.BlueprintPreviewRenderer.register();

        // 起動確認用: -Dkingdomunique.verify.seconds=N を渡すと N 秒後に自動終了する。
        // 通常プレイではプロパティが無いため何も起きない。
        String verifySeconds = System.getProperty("kingdomunique.verify.seconds");
        if (verifySeconds != null) {
            try {
                int seconds = Integer.parseInt(verifySeconds);
                if (seconds > 0) {
                    final int[] remaining = {seconds * 20};
                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (remaining[0]-- <= 0) {
                            KingdomUniqueMod.LOGGER.info(
                                    "[KingdomUnique] Verification complete. Closing client.");
                            client.scheduleStop();
                        }
                    });
                }
            } catch (NumberFormatException ignored) {
                // 不正な値は無視する。
            }
        }

        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Client entrypoint initialized.");
    }
}
