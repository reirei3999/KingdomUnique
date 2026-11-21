package com.kingdomunique.kingdom;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** 「王国の証」廃止後: 自国領土内の村人を右クリックして直接国民化する。 */
public final class VillagerEnlistHandler {

    private VillagerEnlistHandler() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND
                    || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(entity instanceof VillagerEntity villager)
                    || !(entity instanceof com.kingdomunique.entity.CitizenVillager citizen)) {
                return ActionResult.PASS;
            }
            // 通常の右クリックはバニラの交易に任せる。スニーク時のみ国民化する。
            if (!serverPlayer.isSneaking()) {
                return ActionResult.PASS;
            }
            if (!"".equals(citizen.ku$getOwnerUuid())) {
                return ActionResult.PASS;
            }
            if (!KingdomManager.isInTerritory(serverPlayer)) {
                serverPlayer.sendMessage(Text.literal("§c[王国] 村人を国民化できるのは自国領土内のみです。"), false);
                return ActionResult.PASS;
            }
            KingdomManager.enlistVillager(serverPlayer, (LivingEntity) villager, null);
            return ActionResult.SUCCESS;
        });
    }
}
