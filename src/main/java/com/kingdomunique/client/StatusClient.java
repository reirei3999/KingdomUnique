package com.kingdomunique.client;

import com.kingdomunique.network.SyncPlayerStatusPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** ステータス同期パケットの受信と最新状態の保持。 */
@Environment(EnvType.CLIENT)
public final class StatusClient {

    private static SyncPlayerStatusPayload latest = new SyncPlayerStatusPayload(
            List.of(), "", 0, 1, false);

    private StatusClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPlayerStatusPayload.ID, (payload, context) -> {
            latest = payload;
            if (payload.openScreen()) {
                context.client().execute(() ->
                        context.client().setScreen(new StatusScreen(payload)));
            }
        });
    }

    public static SyncPlayerStatusPayload latest() {
        return latest;
    }
}
