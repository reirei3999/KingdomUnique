package com.kingdomunique.status;

import com.kingdomunique.ability.AbilityState;
import com.kingdomunique.ability.PlayerAbilityState;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.network.RequestStatusPayload;
import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 所持ステータスの同期・確認画面表示・コマンド登録。 */
public final class StatusManager {

    private StatusManager() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestStatusPayload.ID,
                (payload, context) -> sendStatus(context.player(), true));
        ServerTickEvents.END_SERVER_TICK.register(StatusManager::onTick);
    }

    public static void registerCommands(LiteralArgumentBuilder<ServerCommandSource> ku) {
        ku.then(CommandManager.literal("info").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) {
                return 0;
            }
            sendStatus(player, true);
            return 1;
        }));
        ku.then(CommandManager.literal("status").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) {
                return 0;
            }
            sendStatus(player, true);
            return 1;
        }));
    }

    private static long tick = 0L;

    /** HUD用に10ティックごと同期する（モータスを開く指示は false）。 */
    private static void onTick(net.minecraft.server.MinecraftServer server) {
        if (++tick % 10L != 0L) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendStatus(player, false);
        }
    }

    public static void sendStatus(ServerPlayerEntity player, boolean openScreen) {
        if (!ServerPlayNetworking.canSend(player, SyncPlayerStatusPayload.ID)) {
            return;
        }
        WorldUniqueData data = WorldUniqueData.getOrCreate(
                player.getServer().getWorld(World.OVERWORLD));
        List<String> owned = new ArrayList<>(data.getClaimedIds(player.getUuid()));

        String abilityId = UniqueContentRegistry.getAbility(player);
        PlayerAbilityState state = AbilityState.get(player.getUuid());
        long now = player.getServerWorld().getTime();
        int cooldown = 0;
        if (abilityId != null) {
            long ready = state.getReadyTick(abilityId);
            if (ready > now) {
                cooldown = (int) (ready - now);
            }
        }
        ServerPlayNetworking.send(player, new SyncPlayerStatusPayload(
                owned, abilityId == null ? "" : abilityId, cooldown,
                state.getMiningRange(), openScreen));
    }
}
