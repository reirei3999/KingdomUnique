package com.kingdomunique.ability;

import com.kingdomunique.network.AbilityKeyPayload;
import com.kingdomunique.registry.UniqueContentRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.concurrent.ThreadLocalRandom;

/** ステップ3のイベント登録をまとめたクラス。 */
public final class AbilityEventHandler {

    private static final double REVIVE_CHANCE = 0.25D;

    private AbilityEventHandler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AbilityEventHandler::onTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            AbilityPassives.applyAttributes(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            AbilityState.remove(handler.getPlayer().getUuid());
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> AlliedMobManager.restore(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> AlliedMobManager.forget(entity.getUuid()));

        ServerPlayNetworking.registerGlobalReceiver(AbilityKeyPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            switch (payload.action()) {
                case ACTIVATE -> AbilityActions.handleActivate(player);
                case MODE_SWITCH -> AbilityActions.handleModeSwitch(player);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register(AbilityEventHandler::onDeath);
    }

    private static void onTick(MinecraftServer server) {
        AbilityPassives.onServerTick(server);
        AbilityActions.tickDecoys(server);
    }

    /** 絶対支配：敵対Mob撃破時に25%で味方化して蘇生する。 */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof HostileEntity hostile) || entity.getWorld().isClient()) {
            return;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!UniqueContentRegistry.has(player, UniqueContentRegistry.ABSOLUTE_DOMINION)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > REVIVE_CHANCE) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (AlliedMobManager.tryRevive(hostile, player, world)) {
            player.sendMessage(Text.literal("§d[絶対支配] " + hostile.getName().getString() + " を味方として蘇生させました。"), false);
        }
        AlliedMobManager.forget(entity.getUuid());
    }
}
