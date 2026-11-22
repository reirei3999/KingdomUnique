package com.kingdomunique.world;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.kingdom.KingdomData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

/** 王国中心・初期村の周囲では村人を敵対Mobから保護する。 */
public final class VillageProtection {

    private static final double KINGDOM_RADIUS_SQ = 32.0D * 32.0D;
    private static final double VILLAGE_RADIUS_SQ = 24.0D * 24.0D;

    private VillageProtection() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(VillageProtection::allowDamage);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        // プレイヤーが意図的に攻撃した場合は通常どおりダメージを通す。
        return source.getAttacker() instanceof ServerPlayerEntity || !protectsFromMobDamage(entity);
    }

    public static boolean protectsFromMobDamage(LivingEntity entity) {
        if (!(entity instanceof VillagerEntity) || !(entity.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        double x = entity.getX();
        double z = entity.getZ();

        for (KingdomData.KingdomInfo info : KingdomData.getOrCreate(world).all().values()) {
            double dx = info.centerX() - x;
            double dz = info.centerZ() - z;
            if (dx * dx + dz * dz <= KINGDOM_RADIUS_SQ) {
                return true;
            }
        }
        for (WorldUniqueData.VillageSite site
                : WorldUniqueData.getOrCreate(world).getVillageSites()) {
            double dx = site.x() - x;
            double dz = site.z() - z;
            if (dx * dx + dz * dz <= VILLAGE_RADIUS_SQ) {
                return true;
            }
        }
        return false;
    }
}
