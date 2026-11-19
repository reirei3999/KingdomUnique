package com.kingdomunique.item.unique;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 神弓アポロンの太陽弓。引き絞り（UseAction.BOW）を完全に廃止し、
 * 見た目だけ弓の通常 Item として実装する。
 *
 *  - 左クリック（空振り・ブロック攻撃）: 光の矢を即時発射（CT 8秒）
 *  - 右クリック: 飛翔中の自分の光の矢を空中起爆
 */
public final class ApolloBowItem extends Item implements UniqueWeapon {

    public ApolloBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "apollo_bow";
    }

    /** ブロックを攻撃した瞬間の左クリックも発射扱いにする。 */
    public static void registerEvents() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND
                    || !player.getMainHandStack().isOf(UniqueWeapons.APOLLO_BOW)
                    || player.getItemCooldownManager().isCoolingDown(UniqueWeapons.APOLLO_BOW)) {
                return ActionResult.PASS;
            }
            if (player instanceof ServerPlayerEntity serverPlayer) {
                UniqueWeaponManager.fireLightArrowWithCooldown(serverPlayer);
            }
            return ActionResult.SUCCESS;
        });
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            UniqueWeaponManager.detonateLightArrow(player);
        }
        return TypedActionResult.success(stack);
    }
}
