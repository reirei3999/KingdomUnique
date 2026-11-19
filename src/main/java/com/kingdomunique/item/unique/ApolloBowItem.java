package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * 神弓アポロンの太陽弓。BowItem を継承せず Item を直接拡張し、
 * 矢を一切消費せずに光の矢を放つ。引き絞り時間・UseAction.BOW により
 * バニラと同じ弓持ち構え／引き絞りモーションが再生される。
 */
public final class ApolloBowItem extends Item implements UniqueWeapon {

    /** バニラの弓と同じ最大チャージ時間（ティック）。 */
    private static final int USE_DURATION = 72000;

    public ApolloBowItem(Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "apollo_bow";
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public boolean isUsedOnRelease(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // 矢の所持判定を一切行わず、常に引き絞りを開始する。
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
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
