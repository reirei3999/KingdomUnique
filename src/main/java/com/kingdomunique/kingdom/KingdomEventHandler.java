package com.kingdomunique.kingdom;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.mixin.LivingEntityAccessor;
import com.kingdomunique.network.PossessionPayload;
import com.kingdomunique.network.SetKingdomNamePayload;
import com.kingdomunique.world.WorldInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** ステップ5のイベント登録（国民・英雄・憑依）。 */
public final class KingdomEventHandler {

    private static final Map<UUID, Long> LAST_BIG_JUMP = new HashMap<>();

    private KingdomEventHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SetKingdomNamePayload.ID, (payload, context) -> {
            KingdomManager.setKingdomName(context.player(), payload.name());
        });
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                KingdomManager.adjustVillagePopulations(world);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(KingdomEventHandler::onTick);

        ServerPlayNetworking.registerGlobalReceiver(PossessionPayload.ID,
                (payload, context) -> KingdomManager.togglePossession(context.player()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                KingdomManager.clearPossession(handler.getPlayer()));

        ServerLivingEntityEvents.AFTER_DEATH.register(KingdomEventHandler::onDeath);
    }

    private static void onTick(MinecraftServer server) {
        KingdomManager.tickGrowth(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPossession(player);
        }
    }

    /** 憑依中：本体を固定し、英雄をプレイヤー入力で操作する。 */
    private static void tickPossession(ServerPlayerEntity player) {
        HeroEntity hero = KingdomManager.possessedHero(player);
        if (hero == null) {
            return;
        }

        player.setVelocity(Vec3d.ZERO);
        player.setNoGravity(true);
        hero.setYaw(player.getYaw());
        hero.setPitch(player.getPitch());
        hero.setHeadYaw(player.getYaw());

        LivingEntityAccessor input = (LivingEntityAccessor) player;
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        double speed = hero.getHeroClass() == HeroClass.ASSASSIN ? 0.9D : 0.6D;
        Vec3d move = look.multiply(input.ku$forwardSpeed() * speed)
                .add(side.multiply(input.ku$sidewaysSpeed() * speed));
        Vec3d pos = hero.getPos().add(move);
        hero.teleport(pos.x, hero.getY(), pos.z, false);

        // 刺客：スニーク＋ジャンプで大ジャンプ。
        if (hero.getHeroClass() == HeroClass.ASSASSIN
                && player.isSneaking() && input.ku$jumping()) {
            long now = hero.getWorld().getTime();
            Long last = LAST_BIG_JUMP.get(player.getUuid());
            if (last == null || now - last > 40L) {
                LAST_BIG_JUMP.put(player.getUuid(), now);
                hero.setVelocity(hero.getVelocity().add(0.0D, 2.2D, 0.0D));
            }
        }
    }

    /** 国民死亡時に王へ呪いダメージ。 */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof VillagerEntity villager) || !(villager instanceof CitizenVillager citizen)) {
            return;
        }
        String owner = citizen.ku$getOwnerUuid();
        if (owner.isEmpty()) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(owner);
            if (((ServerWorld) entity.getWorld()).getEntity(uuid) instanceof ServerPlayerEntity player) {
                KingdomManager.onCitizenDeath(player);
            }
        } catch (IllegalArgumentException ignored) {
            // 不正なUUIDは無視する。
        }
    }
}
