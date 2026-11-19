package com.kingdomunique.item;

import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 憑依のオーブ：刺客英雄への乗り移りを切り替える。 */
public final class PossessionOrbItem extends Item {

    public PossessionOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                KingdomManager.togglePossession(player);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
