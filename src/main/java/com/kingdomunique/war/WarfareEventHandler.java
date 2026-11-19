package com.kingdomunique.war;

import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** 戦争・ヘイト関連のイベント登録。 */
public final class WarfareEventHandler {

    private WarfareEventHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blockedByShield) -> {
            if (!(entity instanceof LivingEntity victim)) {
                return;
            }
            if (source.getAttacker() instanceof LivingEntity attacker) {
                Warfare.propagateHate(victim, attacker);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(WarfareEventHandler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (HeroEntity hero : player.getServerWorld().getEntitiesByClass(HeroEntity.class,
                    player.getBoundingBox().expand(64.0D),
                    h -> h.isAlive() && h.getHeroClass() == HeroClass.ASSASSIN)) {
                Warfare.applyStealth(hero);
            }
        }
    }
}
