package com.kingdomunique.selection;

import com.kingdomunique.data.PendingSelectionState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.network.OpenSelectionPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽選・排他ロック・選択GUIのサーバー側管理。
 * 保留中の選択とトリガーの発動記録はすべて永続データに置き、再ログイン・再起動で再抽選されないようにする。
 */
public final class SelectionManager {

    public static final int DIAMOND_OPTIONS = 2;
    public static final int RELIC_OPTIONS = 3;
    public static final int CURSE_OPTIONS = 5;

    private static final String TRIGGER_DIAMOND = "diamond";
    private static final String TRIGGER_CITIZEN = "citizen";
    private static final String TRIGGER_NETHER = "nether";
    static final int MANUAL_SELECTION_PERMISSION_LEVEL = 2;

    private SelectionManager() {
    }

    /** トリガーA: ダイヤモンド初取得。 */
    public static void onDiamondObtained(ServerPlayerEntity player) {
        // 初回覚醒（1つ目）＋ダイヤ（2つ目）。旧版で既に2つ以上持っていれば発動済みとみなす。
        trigger(player, TRIGGER_DIAMOND, ContentRegistry.Kind.ABILITY, heldCount(player, ContentRegistry.Kind.ABILITY) >= 2);
    }

    /** トリガーB: 国民15人達成（外部システムから呼ばれる判定用メソッド）。 */
    public static void onCitizenCountReached(ServerPlayerEntity player, int count) {
        if (count < 15) {
            return;
        }
        trigger(player, TRIGGER_CITIZEN, ContentRegistry.Kind.RELIC, heldCount(player, ContentRegistry.Kind.RELIC) > 0);
    }

    /** トリガーC: ネザー初到達。 */
    public static void onNetherEntered(ServerPlayerEntity player) {
        trigger(player, TRIGGER_NETHER, ContentRegistry.Kind.CURSE, heldCount(player, ContentRegistry.Kind.CURSE) > 0);
    }

    private static void trigger(ServerPlayerEntity player, String trigger, ContentRegistry.Kind kind,
                                boolean alreadyGranted) {
        boolean fresh = uniqueData(player).markSelectionTrigger(player.getUuid(), trigger);
        boolean recorded = pendingState(player).recordTrigger(player.getUuid(), kind);
        if (!fresh || !recorded || alreadyGranted) {
            return;
        }
        PendingSelectionState state = pendingState(player);
        if (state.hasPending(player.getUuid())) {
            // 別の選択が未確定なら上書きせず、確定後に順番に表示する。
            state.enqueue(player.getUuid(), kind);
            return;
        }
        openRandom(player, kind, optionCount(kind));
    }

    private static int optionCount(ContentRegistry.Kind kind) {
        return switch (kind) {
            case ABILITY -> DIAMOND_OPTIONS;
            case RELIC -> RELIC_OPTIONS;
            case CURSE -> CURSE_OPTIONS;
        };
    }

    private static int heldCount(ServerPlayerEntity player, ContentRegistry.Kind kind) {
        WorldUniqueData data = uniqueData(player);
        int count = 0;
        for (String id : ContentRegistry.idsOf(kind)) {
            if (data.hasId(player.getUuid(), id)) {
                count++;
            }
        }
        return count;
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
        sendScreen(player, kind, options);
    }

    /** 専用コマンド等から未選択分を再表示する。 */
    public static boolean reopen(ServerPlayerEntity player) {
        PendingSelectionState.Pending pending = pendingState(player).getPending(player.getUuid());
        if (pending == null) {
            return false;
        }
        sendScreen(player, pending.kind(), pending.optionIds());
        return true;
    }

    /**
     * /ku select 用: 保留中の選択があればそれを再表示する。
     * 新規抽選は管理者（権限レベル2以上）のデバッグ用途に限る。一般プレイヤーが何度でも抽選して
     * ユニークを独占できないようにするため。
     */
    public static boolean reopenOrOpen(ServerPlayerEntity player, ContentRegistry.Kind kind) {
        if (!player.hasPermissionLevel(MANUAL_SELECTION_PERMISSION_LEVEL)) {
            return false;
        }
        if (reopen(player)) {
            return true;
        }
        openRandom(player, kind, optionCount(kind));
        return true;
    }

    private static void sendScreen(ServerPlayerEntity player, ContentRegistry.Kind kind, List<String> options) {
        if (player.networkHandler == null) {
            // 未接続（切断処理中など）。保留は永続化済みなので、次回ログイン時に再表示される。
            return;
        }
        if (ServerPlayNetworking.canSend(player, OpenSelectionPayload.ID)) {
            ServerPlayNetworking.send(player, new OpenSelectionPayload(kind, new ArrayList<>(options)));
        } else {
            player.sendMessage(Text.literal("§c[KingdomUnique] クライアントが選択GUIに対応していません。"), false);
        }
    }

    /** プレイヤーが選択した瞬間にサーバー側で排他ロックする。 */
    public static void handleChoice(ServerPlayerEntity player, ContentRegistry.Kind kind, String chosenId) {
        PendingSelectionState state = pendingState(player);
        PendingSelectionState.Pending pending = state.getPending(player.getUuid());
        if (pending == null || pending.kind() != kind || !pending.optionIds().contains(chosenId)) {
            return;
        }
        WorldUniqueData data = uniqueData(player);
        boolean claimed = data.claimId(player.getUuid(), chosenId);
        state.clearPending(player.getUuid());
        if (!claimed) {
            player.sendMessage(Text.literal("§c[KingdomUnique] このアイテムは他のプレイヤーに取得されました。"), false);
            openNextQueued(player, state);
            return;
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
        openNextQueued(player, state);
    }

    private static void openNextQueued(ServerPlayerEntity player, PendingSelectionState state) {
        ContentRegistry.Kind next = state.pollQueued(player.getUuid());
        if (next != null) {
            openRandom(player, next, optionCount(next));
        }
    }

    /** 初回覚醒で既に能力を持っているか（ステップ1互換）。 */
    public static boolean hasAnyAbility(ServerPlayerEntity player) {
        return heldCount(player, ContentRegistry.Kind.ABILITY) > 0;
    }

    private static PendingSelectionState pendingState(ServerPlayerEntity player) {
        return PendingSelectionState.get(player.getServerWorld());
    }

    private static WorldUniqueData uniqueData(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
        return WorldUniqueData.getOrCreate(overworld);
    }
}
