package com.kingdomunique.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 時の支配者：周囲8ブロックの敵の時間を3秒止める。 */
public final class TimeRulerAbility {

    public static final double RADIUS = 8.0D;
    public static final int DURATION_TICKS = 60;
    /** 停止中に保存された場合でも再読み込み時にAIを戻すための目印。 */
    public static final String FROZEN_TAG = "ku_time_stopped";

    private record Frozen(MobEntity mob, long releaseTick) {
    }

    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    private TimeRulerAbility() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MobEntity mob && mob.getCommandTags().contains(FROZEN_TAG)
                    && !FROZEN.containsKey(mob.getUuid())) {
                unfreeze(mob);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> releaseAll());
    }

    public static boolean activate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<MobEntity> targets = world.getEntitiesByClass(MobEntity.class,
                player.getBoundingBox().expand(RADIUS),
                mob -> isEnemy(player, mob) && mob.squaredDistanceTo(player) <= RADIUS * RADIUS);
        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("§c[時の支配者] 周囲8ブロックに敵がいません。"), true);
            return false;
        }
        long release = overworldTime(world.getServer()) + DURATION_TICKS;
        for (MobEntity mob : targets) {
            mob.setAiDisabled(true);
            mob.setVelocity(0.0D, Math.min(0.0D, mob.getVelocity().y), 0.0D);
            mob.addCommandTag(FROZEN_TAG);
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, DURATION_TICKS, 0, false, false));
            FROZEN.put(mob.getUuid(), new Frozen(mob, release));
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getBodyY(0.5D), mob.getZ(),
                    20, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.PLAYERS, 1.0F, 0.6F);
        player.sendMessage(Text.literal("§b[時の支配者] " + targets.size() + "体の敵の時間を止めた（3秒）。"), true);
        return true;
    }

    /** プレイヤー以外の敵：敵対Mob、またはこのプレイヤーを狙っているMob（味方化したMobは除く）。 */
    private static boolean isEnemy(ServerPlayerEntity player, MobEntity mob) {
        if (!mob.isAlive() || mob.isAiDisabled() || AlliedMobManager.isAlly(mob)) {
            return false;
        }
        return mob instanceof Monster || mob.getTarget() == player;
    }

    public static boolean isFrozen(Entity entity) {
        return FROZEN.containsKey(entity.getUuid());
    }

    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) {
            return;
        }
        long now = overworldTime(server);
        FROZEN.values().removeIf(frozen -> {
            MobEntity mob = frozen.mob();
            // 未ロードで外れた個体は保存済みの目印から ENTITY_LOAD で復帰する。
            if (mob.isRemoved()) {
                return true;
            }
            if (now >= frozen.releaseTick()) {
                unfreeze(mob);
                return true;
            }
            mob.setVelocity(0.0D, Math.min(0.0D, mob.getVelocity().y), 0.0D);
            return false;
        });
    }

    public static void releaseAll() {
        for (Frozen frozen : FROZEN.values()) {
            unfreeze(frozen.mob());
        }
        FROZEN.clear();
    }

    private static void unfreeze(MobEntity mob) {
        mob.setAiDisabled(false);
        mob.removeCommandTag(FROZEN_TAG);
    }

    private static long overworldTime(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        return overworld == null ? 0L : overworld.getTime();
    }
}
