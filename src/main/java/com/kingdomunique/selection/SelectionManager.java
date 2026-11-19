package com.kingdomunique.selection;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 抽選・排他ロック・選択GUIのサーバー側管理。 */
public final class SelectionManager {

    public static final int DIAMOND_OPTIONS = 2;
    public static final int RELIC_OPTIONS = 3;
    public static final int CURSE_OPTIONS = 5;

    private record Pending(ContentRegistry.Kind kind, List<String> optionIds) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Set<UUID> DIAMOND_DONE = new HashSet<>();
    private static final Set<UUID> CITIZEN_DONE = new HashSet<>();
    private static final Set<UUID> NETHER_DONE = new HashSet<>();

    private SelectionManager() {
    }

    /** トリガーA: ダイヤモンド初取得。 */
    public static void onDiamondObtained(ServerPlayerEntity player) {
        if (!DIAMOND_DONE.add(player.getUuid())) {
            return;
        }
        openRandom(player, ContentRegistry.Kind.ABILITY, DIAMOND_OPTIONS);
    }

    /** トリガーB: 国民15人達成（外部システムから呼ばれる判定用メソッド）。 */
    public static void onCitizenCountReached(ServerPlayerEntity player, int count) {
        if (count < 15) {
            return;
        }
        if (!CITIZEN_DONE.add(player.getUuid())) {
            return;
        }
        openRandom(player, ContentRegistry.Kind.RELIC, RELIC_OPTIONS);
    }

    /** トリガーC: ネザー初到達。 */
    public static void onNetherEntered(ServerPlayerEntity player) {
        if (!NETHER_DONE.add(player.getUuid())) {
            return;
        }
        openRandom(player, ContentRegistry.Kind.CURSE, CURSE_OPTIONS);
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
        Pending pending = new Pending(kind, options);
        PENDING.put(player.getUuid(), pending);
        sendScreen(player, pending);
    }

    /** 専用コマンド等から未選択分を再表示する。 */
    public static boolean reopen(ServerPlayerEntity player) {
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null) {
            return false;
        }
        sendScreen(player, pending);
        return true;
    }

    /**
     * /ku select 用: 保留中の選択があればそれを再表示し、
     * なければ指定カテゴリの選択肢を新規抽選して必ず選択GUIを開く。
     */
    public static boolean reopenOrOpen(ServerPlayerEntity player, ContentRegistry.Kind kind) {
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

    private static void sendScreen(ServerPlayerEntity player, Pending pending) {
        if (ServerPlayNetworking.canSend(player, OpenSelectionPayload.ID)) {
            ServerPlayNetworking.send(player,
                    new OpenSelectionPayload(pending.kind(), new ArrayList<>(pending.optionIds())));
        } else {
            player.sendMessage(Text.literal("§c[KingdomUnique] クライアントが選択GUIに対応していません。"), false);
        }
    }

    /** プレイヤーが選択した瞬間にサーバー側で排他ロックする。 */
    public static void handleChoice(ServerPlayerEntity player, ContentRegistry.Kind kind, String chosenId) {
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.kind() != kind || !pending.optionIds().contains(chosenId)) {
            return;
        }
        WorldUniqueData data = uniqueData(player);
        if (!data.claimId(player.getUuid(), chosenId)) {
            player.sendMessage(Text.literal("§c[KingdomUnique] このアイテムは他のプレイヤーに取得されました。"), false);
            PENDING.remove(player.getUuid());
            return;
        }
        PENDING.remove(player.getUuid());

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

    public static void remove(ServerPlayerEntity player) {
        PENDING.remove(player.getUuid());
    }

    private static WorldUniqueData uniqueData(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
        return WorldUniqueData.getOrCreate(overworld);
    }
}
