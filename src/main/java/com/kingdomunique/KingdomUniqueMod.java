package com.kingdomunique;

import com.kingdomunique.ability.AbilityEventHandler;
import com.kingdomunique.command.ShowItemCommand;
import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.building.ModBlocks;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.item.ModItems;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.kingdom.KingdomEventHandler;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.selection.SelectionEventHandler;
import com.kingdomunique.selection.SelectionManager;
import com.kingdomunique.war.WarfareEventHandler;
import com.kingdomunique.world.WorldInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KingdomUnique のサーバーサイドエントリポイント。
 *
 * 担当:
 *  - オーバーワールド初回生成時の初期化（ワールドボーダー + 村10箇所）
 *  - プレイヤー初ログイン時のユニーク能力覚醒
 */
public class KingdomUniqueMod implements ModInitializer {

    public static final String MOD_ID = "kingdomunique";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[KingdomUnique] Initializing on Minecraft 1.21.1 / Fabric.");

        // カスタム Data Component（enhance_level）を登録する。
        ModDataComponents.initialize();

        // ステップ3: 能力のイベント・パケット・R/Vキー操作を登録する。
        AbilityEventHandler.register();

        // ステップ4: 専用アイテム・選択GUI・遺物/呪物の効果を登録する。
        ModItems.initialize();
        com.kingdomunique.item.ItemMenuHandler.register();
        com.kingdomunique.command.ModCommands.register();
        SelectionEventHandler.register();

        // ステップ5: 国民・英雄エンティティと王国イベントを登録する。
        ModEntities.initialize();
        KingdomEventHandler.register();
        com.kingdomunique.kingdom.PolicyManager.register();
        com.kingdomunique.kingdom.VillagerEnlistHandler.register();
        com.kingdomunique.building.DefenseSystem.register();
        com.kingdomunique.util.EnhancementAuraSystem.register();
        com.kingdomunique.util.RoadSpeedSystem.register();

        // ステップ6: 設計図ブロック・一点モノ武器・最終統合イベントを登録する。
        ModBlocks.initialize();
        UniqueWeapons.initialize();
        com.kingdomunique.item.unique.ApolloBowItem.registerEvents();
        FinalEventHandler.register();

        // 追加: 王国間外交・スマートヘイト・王権略奪・7日ごとの大襲撃。
        WarfareEventHandler.register();
        com.kingdomunique.war.SpoilsSystem.register();
        com.kingdomunique.war.RaidSystem.register();

        // /i コマンド（アイテム披露）を登録する。
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ShowItemCommand.register(dispatcher);
        });

        // ワールド読み込み時にオーバーワールドの初期設定を行う。
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                WorldInitializer.initialize(world);
            }
        });

        // プレイヤーがゲームに参加した時点で覚醒処理を行う。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.kingdomunique.world.StarterSpawn.onJoin(handler.getPlayer(), server);
            grantInitialAwakening(server, handler.getPlayer());
        });

        LOGGER.info("[KingdomUnique] Initialization completed.");
    }

    /**
     * 初回ログイン（＝まだ能力を持っていない）プレイヤーに、
     * 世界で未取得のユニークIDを1つだけ割り当てる。
     */
    private static void grantInitialAwakening(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            LOGGER.warn("[KingdomUnique] Overworld is not ready. Skipping awakening for {}.", player.getName().getString());
            return;
        }

        WorldUniqueData data = WorldUniqueData.getOrCreate(overworld);
        UUID uuid = player.getUuid();

        if (SelectionManager.hasAnyAbility(player)) {
            player.sendMessage(Text.literal("§7[KingdomUnique] 既に覚醒済みです。"), false);
            return;
        }

        List<String> available = data.getAvailableIds(UniqueContentRegistry.UNIQUE_IDS);
        if (available.isEmpty()) {
            player.sendMessage(Text.literal("§c[KingdomUnique] 割り当て可能なユニークが残っていません。"), false);
            LOGGER.warn("[KingdomUnique] No unique ids left for player {}.", uuid);
            return;
        }

        String chosenId = available.get(ThreadLocalRandom.current().nextInt(available.size()));
        if (!data.claimId(uuid, chosenId)) {
            player.sendMessage(Text.literal("§c[KingdomUnique] 覚醒処理に失敗しました。もう一度ログインしてください。"), false);
            LOGGER.error("[KingdomUnique] Failed to claim '{}' for {}.", chosenId, uuid);
            return;
        }

        UniqueContentRegistry.UniqueEntry entry = UniqueContentRegistry.get(chosenId);
        String displayName = entry == null ? chosenId : entry.displayName();
        String category = entry == null ? "UNKNOWN" : entry.category().getJapanese();

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§6§l====== 覚醒 ======"), false);
        player.sendMessage(Text.literal("§e" + player.getName().getString() + " §7はユニークな力に目覚めました！"), false);
        player.sendMessage(Text.literal("§f[" + category + "] §a§l" + displayName + " §7(ID: " + chosenId + ")"), false);
        player.sendMessage(Text.literal("§7この力は世界でたった一つ。同じ力を持つ者は他にいません。"), false);
        player.sendMessage(Text.literal("§6§l=================="), false);
        player.sendMessage(Text.literal(""), false);

        LOGGER.info("[KingdomUnique] Player {} awakened with unique '{}'.", uuid, chosenId);
    }
}
