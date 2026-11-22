package com.kingdomunique.selection;

import com.kingdomunique.data.PendingSelectionState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.network.OpenSelectionPayload;
import com.kingdomunique.network.SelectionResultPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 抽選・排他ロック・選択GUIのサーバー側管理。 */
public final class SelectionManager {

    public static final int DIAMOND_OPTIONS = 2;
    public static final int RELIC_OPTIONS = 3;
    public static final int CURSE_OPTIONS = 5;
    static final int MANUAL_SELECTION_PERMISSION_LEVEL = 2;

    private SelectionManager() {
    }

    /** トリガーA: ダイヤモンド初取得。 */
    public static void onDiamondObtained(ServerPlayerEntity player) {
        openFromTrigger(player, ContentRegistry.Kind.ABILITY, DIAMOND_OPTIONS);
    }

    /** トリガーB: 国民15人達成（外部システムから呼ばれる判定用メソッド）。 */
    public static void onCitizenCountReached(ServerPlayerEntity player, int count) {
        if (count < 15) {
            return;
        }
        openFromTrigger(player, ContentRegistry.Kind.RELIC, RELIC_OPTIONS);
    }

    /** トリガーC: ネザー初到達。 */
    public static void onNetherEntered(ServerPlayerEntity player) {
        openFromTrigger(player, ContentRegistry.Kind.CURSE, CURSE_OPTIONS);
    }

    private static void openFromTrigger(ServerPlayerEntity player, ContentRegistry.Kind kind, int count) {
        if (pendingState(player).recordTrigger(player.getUuid(), kind)) {
            openRandom(player, kind, count);
        }
    }

    /** 未取得プールからランダムに選択肢を抽選し、GUIを開かせる。 */
    public static void openRandom(ServerPlayerEntity player, ContentRegistry.Kind kind, int count) {
        WorldUniqueData data = uniqueData(player);
        List<String> available = data.getAvailableIds(ContentRegistry.idsOf(kind));
        if (available.isEmpty()) {
            player.sendMessage(Text.literal("§c[KingdomUnique] このカテゴリの未取得アイテムは残っていません。"), false);
            return;
        }
        List<String> options = ContentRegistry.randomDistinct(available, count);
        pendingState(player).setPending(player.getUuid(), kind, options);
        PendingSelectionState.Pending head = pendingState(player).getPending(player.getUuid());
        if (head == null) {
            return;
        }
        if (head.kind() == kind) {
            sendScreen(player, kind, options);
        } else {
            player.sendMessage(Text.literal("§e[KingdomUnique] 新しい選択を保留しました。現在の選択を確定すると表示されます。"), false);
        }
    }

    /** 専用コマンド等から未選択分を再表示する。 */
    public static boolean reopen(ServerPlayerEntity player) {
        PendingSelectionState.Pending pending = pendingState(player).getPending(player.getUuid());
        if (pending == null) {
            return false;
        }
        sendScreen(player, pending.kind(), refreshOptions(player, pending));
        return true;
    }

    /**
     * 管理者向けの手動選択。保留中なら再表示し、なければ新規抽選する。
     */
    public static boolean reopenOrOpen(ServerPlayerEntity player, ContentRegistry.Kind kind) {
        if (!player.hasPermissionLevel(MANUAL_SELECTION_PERMISSION_LEVEL)) {
            return false;
        }
        if (reopen(player)) {
            return true;
        }
        int count = switch (kind) {
            case ABILITY -> DIAMOND_OPTIONS;
            case RELIC -> RELIC_OPTIONS;
            case CURSE -> CURSE_OPTIONS;
        };
        openRandom(player, kind, count);
        return true;
    }

    private static void sendScreen(ServerPlayerEntity player, ContentRegistry.Kind kind, List<String> options) {
        if (ServerPlayNetworking.canSend(player, OpenSelectionPayload.ID)) {
            ServerPlayNetworking.send(player,
                    new OpenSelectionPayload(kind, new ArrayList<>(options)));
        } else {
            player.sendMessage(Text.literal("§c[KingdomUnique] クライアントが選択GUIに対応していません。"), false);
        }
    }

    /** プレイヤーが選択した瞬間にサーバー側で排他ロックする。 */
    public static void handleChoice(ServerPlayerEntity player, ContentRegistry.Kind kind, String chosenId) {
        PendingSelectionState state = pendingState(player);
        PendingSelectionState.Pending pending = state.getPending(player.getUuid());
        if (pending == null || pending.kind() != kind || !pending.optionIds().contains(chosenId)) {
            String reason = pending == null ? "未確定の選択がありません。"
                    : pending.kind() != kind ? "選択の種類が変更されています。画面を開き直してください。"
                    : "このカードは現在の選択肢にありません。選び直してください。";
            sendResult(player, kind, false, reason,
                    pending != null && pending.kind() == kind ? pending.optionIds() : List.of());
            return;
        }
        WorldUniqueData data = uniqueData(player);
        if (!data.claimId(player.getUuid(), chosenId)) {
            List<String> options = refreshOptions(player, pending);
            String message = ContentRegistry.nameOf(chosenId)
                    + "は他のプレイヤーに取得されました。"
                    + (options.isEmpty() ? "残りのカードはありません。" : "残りのカードから選び直してください。");
            player.sendMessage(Text.literal("§c[KingdomUnique] " + message), false);
            sendResult(player, kind, false, message, options);
            return;
        }
        state.clearPending(player.getUuid());

        PendingSelectionState.Pending next = state.getPending(player.getUuid());
        if (next != null) {
            sendScreen(player, next.kind(), next.optionIds());
        }

        // 実体アイテムがある場合のみ安全に付与する（空気は渡さない）。
        com.kingdomunique.util.RewardUtil.grantPhysical(player, chosenId);
        RelicEffects.applyAttributes(player);
        CurseEffects.applyAttributes(player);

        String name = ContentRegistry.nameOf(chosenId);
        player.sendMessage(Text.literal(
                com.kingdomunique.util.RewardUtil.rewardMessage(kind, name)
                        + " §7（世界で唯一）"), false);
        if (kind == ContentRegistry.Kind.ABILITY) {
            for (String line : com.kingdomunique.registry.AbilityDescriptions.details(chosenId)) {
                player.sendMessage(Text.literal("§7" + line), false);
            }
            player.sendMessage(Text.literal("§e操作: "
                    + com.kingdomunique.registry.AbilityDescriptions.controlHint(chosenId)), false);
        }
        sendResult(player, kind, true, "獲得が確定しました。", List.of());
    }

    static List<String> refreshOptions(ServerPlayerEntity player, PendingSelectionState.Pending pending) {
        WorldUniqueData data = uniqueData(player);
        List<String> options = data.getAvailableIds(pending.optionIds());
        if (options.isEmpty()) {
            int count = switch (pending.kind()) {
                case ABILITY -> DIAMOND_OPTIONS;
                case RELIC -> RELIC_OPTIONS;
                case CURSE -> CURSE_OPTIONS;
            };
            options = ContentRegistry.randomDistinct(data.getAvailableIds(ContentRegistry.idsOf(pending.kind())), count);
        }
        pendingState(player).setPending(player.getUuid(), pending.kind(), options);
        return options;
    }

    private static void sendResult(ServerPlayerEntity player, ContentRegistry.Kind kind, boolean accepted,
                                   String message, List<String> options) {
        if (ServerPlayNetworking.canSend(player, SelectionResultPayload.ID)) {
            ServerPlayNetworking.send(player, new SelectionResultPayload(kind, accepted, message, options));
        }
    }

    /** 初回覚醒で既に能力を持っているか（ステップ1互換）。 */
    public static boolean hasAnyAbility(ServerPlayerEntity player) {
        WorldUniqueData data = uniqueData(player);
        for (String id : ContentRegistry.ABILITY_IDS) {
            if (data.hasId(player.getUuid(), id)) {
                return true;
            }
        }
        return false;
    }

    private static PendingSelectionState pendingState(ServerPlayerEntity player) {
        return PendingSelectionState.get(player.getServerWorld());
    }

    private static WorldUniqueData uniqueData(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
        return WorldUniqueData.getOrCreate(overworld);
    }
}
