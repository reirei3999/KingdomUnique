package com.kingdomunique.war;

import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.kingdom.PolicyData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.UUID;

/** 7日目の夜に王国領土へ迫る大襲撃と、朝の防衛褒賞。 */
public final class RaidSystem {

    private static final long DAY_TICKS = 24000L;
    private static final long NIGHT_START = 12500L;
    private static final long NIGHT_END = 23000L;
    private static final long MORNING_END = 2000L;

    private RaidSystem() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RaidSystem::onTick);
    }

    private static void onTick(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        long time = world.getTimeOfDay();
        long day = time / DAY_TICKS;
        long timeOfDay = time % DAY_TICKS;

        for (ServerPlayerEntity king : server.getPlayerManager().getPlayerList()) {
            PolicyData data = PolicyData.getOrCreate(world);
            PolicyData.PolicyState state = data.getOrCreate(king.getUuid());
            boolean night = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
            boolean raidDay = day > 0 && day % 7L == 0L;

            if (raidDay && night && state.lastRaidDay() != (int) day && !state.raidActive()) {
                spawnRaid(world, king, (int) day, data, state);
                continue;
            }
            if (state.raidActive() && timeOfDay < MORNING_END && !hasLivingRaiders(world, king.getUuid())) {
                grantReward(world, king, data, state);
            }
        }
    }

    private static String raidTag(UUID owner) {
        return "ku_raid_" + owner.toString();
    }

    private static void spawnRaid(ServerWorld world, ServerPlayerEntity king, int day,
                                  PolicyData data, PolicyData.PolicyState state) {
        BlockPos center = KingdomManager.centerOf(world, king.getUuid());
        if (center == null) {
            return;
        }
        KingdomData.KingdomInfo info = KingdomData.getOrCreate(world).get(king.getUuid());
        int citizens = info == null ? 0 : info.population();
        int count = Math.min(24, 4 + citizens / 2);
        int radius = KingdomManager.territoryRadius(world, king.getUuid()) + 4;
        String tag = raidTag(king.getUuid());

        EntityType<?>[] types = {
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.PILLAGER,
                EntityType.VINDICATOR, EntityType.RAVAGER
        };
        double power = 1.0D + Math.min(2.0D, citizens * 0.05D);
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            double angle = i * 2.0D * Math.PI / count;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 1;

            Entity entity = types[i % types.length].create(world);
            if (!(entity instanceof MobEntity mob)) {
                continue;
            }
            mob.refreshPositionAndAngles(x + 0.5D, y, z + 0.5D,
                    (float) (angle + Math.PI), 0.0F);
            mob.addCommandTag(tag);
            mob.setPersistent();
            strengthen(mob, power);
            world.spawnEntity(mob);
            spawned++;
        }

        data.put(king.getUuid(), state.withRaid(true, day));
        world.getServer().getPlayerManager().broadcast(
                Text.literal("§c§l【大襲撃】§r 第" + day + "日、"
                        + king.getName().getString() + " の王国の国境へ襲撃軍（" + spawned
                        + "体）が迫っている！"), false);
        king.sendMessage(Text.literal("§c[大襲撃] 領土外周の敵を全滅させ、朝まで持ちこたえよ！"), false);
    }

    private static void strengthen(MobEntity mob, double power) {
        var health = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            double value = health.getBaseValue() * power;
            health.setBaseValue(value);
            mob.setHealth((float) value);
        }
        var damage = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * power);
        }
    }

    private static boolean hasLivingRaiders(ServerWorld world, UUID owner) {
        BlockPos center = KingdomManager.centerOf(world, owner);
        if (center == null) {
            return false;
        }
        int radius = KingdomManager.territoryRadius(world, owner) + 32;
        String tag = raidTag(owner);
        Box box = new Box(center.getX() - radius, 0.0D, center.getZ() - radius,
                center.getX() + radius, 320.0D, center.getZ() + radius);
        return !world.getEntitiesByClass(MobEntity.class, box,
                mob -> mob.isAlive() && mob.getCommandTags().contains(tag)
                        && !com.kingdomunique.ability.AlliedMobManager.isAlly(mob)).isEmpty();
    }

    private static void grantReward(ServerWorld world, ServerPlayerEntity king,
                                    PolicyData data, PolicyData.PolicyState state) {
        KingdomData.KingdomInfo info = KingdomData.getOrCreate(world).get(king.getUuid());
        int citizens = info == null ? 0 : info.population();
        int diamonds = 2 + citizens / 4;
        int emeralds = 1 + citizens / 5;
        int books = citizens / 10;

        king.giveItemStack(new ItemStack(Items.DIAMOND, diamonds));
        king.giveItemStack(new ItemStack(Items.EMERALD, emeralds));
        if (books > 0) {
            king.giveItemStack(new ItemStack(Items.ENCHANTED_BOOK, books));
        }
        data.put(king.getUuid(), state.withReward());

        world.getServer().getPlayerManager().broadcast(
                Text.literal("§6§l【防衛成功】§r " + king.getName().getString()
                        + " が大襲撃を退けた！ ダイヤモンド" + diamonds
                        + "・エメラルド" + emeralds + " が褒賞として授与された。"), false);
    }
}
