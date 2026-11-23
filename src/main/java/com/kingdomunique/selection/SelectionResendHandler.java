package com.kingdomunique.selection;

import com.kingdomunique.data.PendingSelectionState;
import com.kingdomunique.network.OpenSelectionPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 未確定の選択を再ログイン時に自動再送する（Gemini 提示を既存 API へ接続）。 */
public final class SelectionResendHandler {

    /** 遅延送信キュー（プレイヤーUUID -> 残り待機tick）。 */
    private static final Map<UUID, Integer> DELAYED = new HashMap<>();

    private SelectionResendHandler() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            PendingSelectionState state = PendingSelectionState.get(player.getServerWorld());
            if (state.hasPending(player.getUuid())) {
                // クライアント初期化完了を待ってから送信（15tick = 0.75秒）。
                DELAYED.put(player.getUuid(), 15);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SelectionResendHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (DELAYED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = DELAYED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            UUID uuid = entry.getKey();
            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                resendPending(player);
            }
        }
    }

    /** 未確定選択肢を再送する（他プレイヤーに取得されたIDは除外）。 */
    private static void resendPending(ServerPlayerEntity player) {
        if (SelectionManager.reopen(player)
                && PendingSelectionState.get(player.getServerWorld()).hasPending(player.getUuid())
                && ServerPlayNetworking.canSend(player, OpenSelectionPayload.ID)) {
            player.sendMessage(Text.literal("§6[未完了の選択] 前回未確定だった選択画面を再表示しました。"), false);
        }
    }
}
