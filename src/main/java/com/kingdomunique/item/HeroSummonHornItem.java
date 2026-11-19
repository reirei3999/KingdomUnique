package com.kingdomunique.item;

import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 英雄召喚の角笛：1王国1回（軍旗で2回）だけ英雄を召喚する。 */
public final class HeroSummonHornItem extends Item {

    public HeroSummonHornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                KingdomManager.summonHero(player);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
