package com.kingdomunique;

import com.kingdomunique.building.BuildingManager;
import com.kingdomunique.building.ModBlocks;
import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.item.unique.PoseidonWhirlpool;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.network.DetonatePayload;
import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** ステップ6の最終統合イベント登録。 */
public final class FinalEventHandler {

    private FinalEventHandler() {
    }

    public static void register() {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
                com.kingdomunique.item.unique.UniqueWeaponTriggers::onTrishulaBlockBreak);
        ServerTickEvents.END_SERVER_TICK.register(FinalEventHandler::onTick);

        ServerPlayNetworking.registerGlobalReceiver(DetonatePayload.ID,
                (payload, context) -> UniqueWeaponManager.fireLightArrowWithCooldown(context.player()));

        ServerLivingEntityEvents.AFTER_DEATH.register(FinalEventHandler::onDeath);
    }

    private static void onTick(MinecraftServer server) {
        UniqueWeaponTriggers.onServerTick(server);
        BuildingManager.onServerTick(server);
        UniqueWeaponManager.tick(server);
        UniqueWeaponManager.tickEffects(server);
        UniqueWeaponManager.tickLightArrows(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UniqueWeapons.POSEIDON_TRIDENT.tickHolder(player);
        }
        server.getWorlds().forEach(PoseidonWhirlpool::tick);
    }

    /** 撃破時の固有武器ギミック（ムラマサ成長・ゲイボルグクール短縮）。 */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof VillagerEntity villager && villager.getWorld() instanceof ServerWorld world) {
            UniqueWeaponTriggers.onVillagerDeath(villager, world);
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }
        UniqueWeaponTriggers.onKill(player, entity);
        ItemStack held = player.getMainHandStack();
        String id = held.get(ModDataComponents.UNIQUE_WEAPON_ID);
        if (id == null && held.isOf(com.kingdomunique.item.ModItems.SLAUGHTER_BLADE)) {
            id = "slaughter_blade";
        }
        if (id == null) {
            return;
        }
        if ("muramasa".equals(id)) {
            float bonus = held.getOrDefault(ModDataComponents.SLAUGHTER_BONUS, 0.0F) + 0.35F;
            held.set(ModDataComponents.SLAUGHTER_BONUS, bonus);
            EnhancementUtil.applySlaughterBonus(held, bonus);
        } else if ("slaughter_blade".equals(id) && isSlaughterGrowthTarget(entity)) {
            float bonus = held.getOrDefault(ModDataComponents.SLAUGHTER_BONUS, 0.0F) + 0.05F;
            held.set(ModDataComponents.SLAUGHTER_BONUS, bonus);
            EnhancementUtil.applySlaughterBonus(held, bonus);
        }
        if ("gae_bolg".equals(id)) {
            UniqueWeaponManager.onPlayerKill(player);
        }
    }

    public static boolean isSlaughterGrowthTarget(LivingEntity entity) {
        return entity instanceof net.minecraft.entity.mob.MobEntity
                && (entity.getType().getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER
                || entity instanceof net.minecraft.entity.mob.Angerable
                || entity instanceof net.minecraft.entity.passive.DolphinEntity
                || entity instanceof net.minecraft.entity.passive.LlamaEntity
                || entity instanceof net.minecraft.entity.passive.PandaEntity
                || entity instanceof net.minecraft.entity.passive.GoatEntity);
    }
}
