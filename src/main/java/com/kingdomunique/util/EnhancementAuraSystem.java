package com.kingdomunique.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** 無限強化 +5 / +10 の到達者にオーラ演出を付与する。 */
public final class EnhancementAuraSystem {

    private static long tick = 0L;

    private EnhancementAuraSystem() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EnhancementAuraSystem::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (++tick % 20L != 0L) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int level = highestLevel(player);
            if (level >= 10) {
                divineAura(player.getServerWorld(), player);
            } else if (level >= 5) {
                refinedAura(player.getServerWorld(), player);
            }
        }
    }

    private static int highestLevel(ServerPlayerEntity player) {
        int max = 0;
        for (ItemStack stack : player.getInventory().armor) {
            max = Math.max(max, getLevel(stack));
        }
        max = Math.max(max, getLevel(player.getMainHandStack()));
        max = Math.max(max, getLevel(player.getOffHandStack()));
        return max;
    }

    private static int getLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer level = stack.get(com.kingdomunique.component.ModDataComponents.ENHANCE_LEVEL);
        return level == null ? 0 : Math.max(0, level);
    }

    /** [精錬] : 金色の微小な光。 */
    private static void refinedAura(ServerWorld world, ServerPlayerEntity player) {
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z,
                6, 0.45D, 0.9D, 0.45D, 0.02D);
        world.spawnParticles(ParticleTypes.WAX_ON, x, y - 0.4D, z,
                4, 0.5D, 0.3D, 0.5D, 0.01D);
    }

    /** [神域] : 紫〜虹色の神々しい粒子。 */
    private static void divineAura(ServerWorld world, ServerPlayerEntity player) {
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y, z,
                10, 0.6D, 1.1D, 0.6D, 0.03D);
        world.spawnParticles(ParticleTypes.ENCHANT, x, y, z,
                12, 0.55D, 1.2D, 0.55D, 0.04D);
    }
}
