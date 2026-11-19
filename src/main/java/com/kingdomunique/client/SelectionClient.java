package com.kingdomunique.client;

import com.kingdomunique.network.OpenItemMenuPayload;
import com.kingdomunique.network.OpenSelectionPayload;
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
        ClientPlayNetworking.registerGlobalReceiver(OpenItemMenuPayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(new ItemMenuScreen()));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenSelectionPayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    context.client().setScreen(new SelectionScreen(payload.kind(), payload.optionIds())));
        });
    }
}
