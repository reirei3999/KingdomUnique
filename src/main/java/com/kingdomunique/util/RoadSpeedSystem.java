package com.kingdomunique.util;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Set;

/** 整備された道の上を歩くプレイヤー・国民・英雄の移動速度を20%上げる。 */
public final class RoadSpeedSystem {

    private static final Identifier MODIFIER_ID =
            Identifier.of("kingdomunique", "road_speed");
    private static final double BONUS = 0.2D;

    private static final Set<Block> ROAD_BLOCKS = Set.of(
            Blocks.DIRT_PATH,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
            Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS
    );

    private RoadSpeedSystem() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RoadSpeedSystem::onTick);
    }

    private static long tick = 0L;

    private static void onTick(MinecraftServer server) {
        if (++tick % 10L != 0L) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            apply(player, player.getServerWorld());
        }
        net.minecraft.util.math.Box all = new net.minecraft.util.math.Box(
                -3_000_000.0D, -512.0D, -3_000_000.0D,
                3_000_000.0D, 512.0D, 3_000_000.0D);
        for (ServerWorld world : server.getWorlds()) {
            for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class,
                    all, v -> v.isAlive())) {
                if (villager instanceof CitizenVillager) {
                    apply(villager, world);
                }
            }
            for (HeroEntity hero : world.getEntitiesByClass(HeroEntity.class,
                    all, h -> h.isAlive())) {
                apply(hero, world);
            }
        }
    }

    private static void apply(LivingEntity entity, ServerWorld world) {
        EntityAttributeInstance attribute =
                entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        boolean onRoad = ROAD_BLOCKS.contains(world.getBlockState(entity.getBlockPos().down()).getBlock());
        if (onRoad) {
            if (attribute.getModifier(MODIFIER_ID) == null) {
                attribute.addTemporaryModifier(new EntityAttributeModifier(
                        MODIFIER_ID, BONUS, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            attribute.removeModifier(MODIFIER_ID);
        }
    }
}
