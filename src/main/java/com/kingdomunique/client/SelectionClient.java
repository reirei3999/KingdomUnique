package com.kingdomunique.client;

import com.kingdomunique.network.OpenItemMenuPayload;
import com.kingdomunique.network.ItemGrantResultPayload;
import com.kingdomunique.network.RequestKingdomNamePayload;
import com.kingdomunique.network.OpenSelectionPayload;
import com.kingdomunique.network.SelectionResultPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 選択GUIに関するクライアント側登録。 */
@Environment(EnvType.CLIENT)
public final class SelectionClient {

    private static boolean initialized = false;

    private SelectionClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(ItemGrantResultPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof ItemMenuScreen menu) {
                    menu.acceptResult(payload);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(SelectionResultPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof SelectionScreen screen) {
                    screen.handleResult(payload);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RequestKingdomNamePayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new KingdomNameScreen()));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenItemMenuPayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new ItemMenuScreen()));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenSelectionPayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    context.client().setScreen(new SelectionScreen(payload.kind(), payload.optionIds())));
        });
    }
}
