package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 神弓アポロンの太陽弓。矢を消費せず、離した瞬間に光の矢を放つ。 */
public final class ApolloBowItem extends BowItem implements UniqueWeapon {

    public ApolloBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "apollo_bow";
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // インベントリに矢がなくても引き絞れるようにする。
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()) {
            return;
        }
        if (user instanceof ServerPlayerEntity player) {
            UniqueWeaponManager.fireLightArrow(player);
        }
    }
}
