package com.kingdomunique.soldier;

import com.kingdomunique.entity.CitizenVillager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.UUID;

/** 自国の兵士を右クリックした際に専用の装備編成UIを開く。 */
public final class SoldierInteractionHandler {

    private SoldierInteractionHandler() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND
                    || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(entity instanceof VillagerEntity villager)
                    || !(villager instanceof CitizenVillager citizen)) {
                return ActionResult.PASS;
            }
            if (!CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole())
                    || citizen.ku$getOwnerUuid().isEmpty()) {
                return ActionResult.PASS;
            }
            if (!isOwnedByKing(citizen, serverPlayer.getUuid())) {
                serverPlayer.sendMessage(Text.literal("§c[王国] 他国の兵士の装備は変更できません。"), true);
                return ActionResult.FAIL;
            }

            int hp = (int) Math.ceil(villager.getHealth());
            int maxHp = (int) Math.ceil(villager.getMaxHealth());
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new SoldierEquipmentScreenHandler(syncId, inv, villager),
                    Text.literal("§b[王国兵士 装備編成] §8(HP " + hp + "/" + maxHp + ")")));
            return ActionResult.SUCCESS;
        });
    }

    /** 兵士が指定された王の所属かどうか。 */
    public static boolean isOwnedByKing(CitizenVillager soldier, UUID kingUuid) {
        return kingUuid.toString().equals(soldier.ku$getOwnerUuid());
    }
}
